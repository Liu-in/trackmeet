package com.trackmeet.modules.athlete.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.athlete.dto.AthleteImportErrorRow;
import com.trackmeet.modules.athlete.dto.AthleteImportErrorVO;
import com.trackmeet.modules.athlete.dto.AthleteImportResultVO;
import com.trackmeet.modules.athlete.dto.AthleteImportRow;
import com.trackmeet.modules.athlete.dto.AthleteImportTipRow;
import com.trackmeet.modules.athlete.entity.Athlete;
import com.trackmeet.modules.athlete.listener.AthleteImportListener;
import com.trackmeet.modules.athlete.mapper.AthleteMapper;
import com.trackmeet.modules.athlete.service.AthleteImportService;
import com.trackmeet.modules.meet.entity.MeetGroup;
import com.trackmeet.modules.meet.entity.Unit;
import com.trackmeet.modules.meet.mapper.MeetGroupMapper;
import com.trackmeet.modules.meet.mapper.UnitMapper;
import com.trackmeet.modules.system.entity.SysImportTask;
import com.trackmeet.modules.system.mapper.SysImportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运动员 Excel 导入实现：模板生成、逐行校验、批量入库、错误报告与任务留痕。
 * 校验规则（全部返回可读中文原因，不做静默丢弃）：
 * 1. 姓名必填且 ≤ 50；2. 性别可识别；3. 出生日期可解析；
 * 4. 身份证号必填、≤ 32、同届次内唯一；5. 单位编码/名称存在；
 * 6. 组别编码/名称存在；7. 性别与组别一致；8. 出生日期落在组别区间内；
 * 9. 号码 ≤ 32 且同届次内唯一（留空自动按「单位前缀-序号」生成）；
 * 10. 资格状态可识别（默认合格）；11. 电话/监护人电话 ≤ 32。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AthleteImportServiceImpl implements AthleteImportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMATTER_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter DATE_FORMATTER_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String GENDER_MALE = "MALE";
    private static final String GENDER_FEMALE = "FEMALE";
    private static final String QUALIFY_PENDING = "PENDING";
    private static final String QUALIFY_PASSED = "QUALIFIED";
    private static final String QUALIFY_FAILED = "DISQUALIFIED";
    private static final String QUALIFY_BANNED = "BANNED";
    private static final String ENTRY_SOURCE_IMPORT = "IMPORT";

    /** 返回给前端的错误明细上限 */
    private static final int MAX_ERROR_RETURN = 200;
    /** 号码自动生成的最大序号，超出后追加时间戳后缀 */
    private static final int MAX_BIB_SEQ = 999;

    private final AthleteMapper athleteMapper;
    private final UnitMapper unitMapper;
    private final MeetGroupMapper meetGroupMapper;
    private final SysImportTaskMapper importTaskMapper;

    @Override
    public void writeTemplate(OutputStream outputStream) {
        List<AthleteImportRow> samples = new ArrayList<>();
        samples.add(buildSample("张小明", "男", "2008-03-12", "110101200803120011", "UNIT_001",
                "SENIOR_MALE", "", "高二(1)班", "13900000001", "张父", "13700000001", "合格", "否", "示例行，导入前请删除"));
        samples.add(buildSample("陈雨欣", "女", "2008-05-18", "110101200805180044", "UNIT_001",
                "SENIOR_FEMALE", "", "高二(3)班", "13900000004", "陈父", "13700000004", "合格", "否", "示例行，导入前请删除"));

        ExcelWriter writer = EasyExcel.write(outputStream).build();
        try {
            WriteSheet dataSheet = EasyExcel.writerSheet(0, "运动员导入模板").head(AthleteImportRow.class).build();
            WriteSheet tipSheet = EasyExcel.writerSheet(1, "填写说明").head(AthleteImportTipRow.class).build();
            writer.write(samples, dataSheet);
            writer.write(buildTips(), tipSheet);
        } finally {
            writer.finish();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AthleteImportResultVO importAthletes(MultipartFile file, Long meetId, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.IMPORT_FORMAT_ERROR, "请上传 Excel 文件");
        }
        Long effectiveMeetId = meetId != null ? meetId : MeetContext.get();
        if (effectiveMeetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少届次参数 meetId（可通过请求头 X-Meet-Id 传递）");
        }
        Long currentMeetId = MeetContext.get();
        if (currentMeetId != null && !currentMeetId.equals(effectiveMeetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权导入该届次数据");
        }

        long startMillis = System.currentTimeMillis();
        AthleteImportResultVO result = new AthleteImportResultVO();
        result.setDryRun(dryRun);

        AthleteImportListener listener = new AthleteImportListener();
        try (InputStream inputStream = file.getInputStream()) {
            EasyExcel.read(inputStream, AthleteImportRow.class, listener)
                    .sheet()
                    .headRowNumber(1)
                    .doRead();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("解析运动员导入文件失败: {}", ex.getMessage());
            throw new BizException(ResultCode.IMPORT_FORMAT_ERROR, "Excel 解析失败，请使用最新模板：" + ex.getMessage());
        }

        List<AthleteImportRow> rows = listener.getRows();
        result.setTotalCount(rows.size());
        if (rows.isEmpty()) {
            result.setMessage("未解析到有效数据行，请使用最新模板填写后重新上传");
            return result;
        }

        ImportContext importContext = loadContext(effectiveMeetId);
        List<AthleteImportErrorRow> errorRows = new ArrayList<>();
        int successCount = 0;
        int autoBibCount = 0;

        for (int index = 0; index < rows.size(); index++) {
            int rowNum = listener.getRowNumbers().get(index);
            AthleteImportRow row = rows.get(index);
            List<String> errors = validateRow(row, importContext);

            if (!errors.isEmpty()) {
                errorRows.add(buildErrorRow(rowNum, row, String.join("；", errors)));
                continue;
            }

            Unit unit = findUnit(row.getUnitCode(), importContext);
            MeetGroup group = findGroup(row.getGroupCode(), importContext);
            Athlete athlete = toEntity(row, effectiveMeetId, unit, group, importContext);
            if (!StringUtils.hasText(row.getBibNo())) {
                athlete.setBibNo(importContext.nextBibNo(unit));
                autoBibCount++;
            }

            if (!dryRun) {
                athleteMapper.insert(athlete);
            }
            // 校验模式下同样占用唯一键，保证同一文件内不重复
            importContext.getIdCards().add(athlete.getIdCard());
            if (StringUtils.hasText(athlete.getBibNo())) {
                importContext.getBibNos().add(athlete.getBibNo());
            }
            successCount++;
        }

        result.setSuccessCount(successCount);
        result.setFailCount(errorRows.size());
        result.setAutoBibCount(autoBibCount);
        result.setErrors(errorRows.stream()
                .limit(MAX_ERROR_RETURN)
                .map(this::toErrorVO)
                .collect(Collectors.toList()));
        if (!errorRows.isEmpty()) {
            result.setErrorFileBase64(buildErrorFile(errorRows));
            result.setErrorFileName("运动员导入错误报告_" + LocalDateTime.now().format(FILE_STAMP) + ".xlsx");
        }
        result.setMessage(buildMessage(result, dryRun));
        if (!dryRun) {
            result.setTaskId(saveTask(file, result, startMillis));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 上下文与校验
    // ------------------------------------------------------------------

    /**
     * 加载届次下的单位、组别与已有运动员唯一键。
     */
    private ImportContext loadContext(Long meetId) {
        ImportContext context = new ImportContext();

        List<Unit> units = unitMapper.selectList(new LambdaQueryWrapper<Unit>()
                .eq(Unit::getMeetId, meetId)
                .eq(Unit::getStatus, "ENABLED"));
        for (Unit unit : units) {
            context.getUnits().add(unit);
            context.getUnitById().put(unit.getId(), unit);
            if (StringUtils.hasText(unit.getUnitCode())) {
                context.getUnitKeys().put(unit.getUnitCode().trim().toLowerCase(), unit);
            }
            if (StringUtils.hasText(unit.getUnitName())) {
                context.getUnitKeys().put(unit.getUnitName().trim().toLowerCase(), unit);
            }
        }

        List<MeetGroup> groups = meetGroupMapper.selectList(new LambdaQueryWrapper<MeetGroup>()
                .eq(MeetGroup::getMeetId, meetId)
                .eq(MeetGroup::getStatus, "ENABLED"));
        for (MeetGroup group : groups) {
            if (StringUtils.hasText(group.getGroupCode())) {
                context.getGroupKeys().put(group.getGroupCode().trim().toLowerCase(), group);
            }
            if (StringUtils.hasText(group.getGroupName())) {
                context.getGroupKeys().put(group.getGroupName().trim().toLowerCase(), group);
            }
        }

        List<Athlete> athletes = athleteMapper.selectList(new LambdaQueryWrapper<Athlete>()
                .eq(Athlete::getMeetId, meetId)
                .select(Athlete::getId, Athlete::getIdCard, Athlete::getBibNo, Athlete::getUnitId));
        for (Athlete athlete : athletes) {
            if (StringUtils.hasText(athlete.getIdCard())) {
                context.getIdCards().add(athlete.getIdCard().trim());
            }
            if (athlete.getUnitId() != null) {
                context.countExisting(athlete.getUnitId());
            }
            if (StringUtils.hasText(athlete.getBibNo())) {
                context.getBibNos().add(athlete.getBibNo().trim());
                if (athlete.getUnitId() != null) {
                    Integer seq = parseBibSeq(athlete.getBibNo());
                    if (seq != null) {
                        Integer current = context.getUnitBibSeq().get(athlete.getUnitId());
                        if (current == null || seq > current) {
                            context.getUnitBibSeq().put(athlete.getUnitId(), seq);
                        }
                    }
                }
            }
        }
        return context;
    }

    private List<String> validateRow(AthleteImportRow row, ImportContext context) {
        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasText(row.getAthleteName())) {
            errors.add("姓名不能为空");
        } else if (row.getAthleteName().trim().length() > 50) {
            errors.add("姓名长度不能超过 50 个字符");
        }

        String gender = parseGender(row.getGender());
        if (!StringUtils.hasText(row.getGender())) {
            errors.add("性别不能为空");
        } else if (gender == null) {
            errors.add("性别只能填写「男」或「女」");
        }

        LocalDate birthDate = parseDate(row.getBirthDate());
        if (!StringUtils.hasText(row.getBirthDate())) {
            errors.add("出生日期不能为空");
        } else if (birthDate == null) {
            errors.add("出生日期格式应为 yyyy-MM-dd");
        }

        String idCard = row.getIdCard() == null ? null : row.getIdCard().trim();
        if (!StringUtils.hasText(idCard)) {
            errors.add("身份证号不能为空");
        } else if (idCard.length() > 32) {
            errors.add("身份证号长度不能超过 32 个字符");
        } else if (context.getIdCards().contains(idCard)) {
            errors.add("身份证号已存在（届次内唯一）");
        }

        Unit unit = null;
        if (!StringUtils.hasText(row.getUnitCode())) {
            errors.add("单位编码不能为空");
        } else {
            unit = findUnit(row.getUnitCode(), context);
            if (unit == null) {
                errors.add("单位编码不存在或已停用：" + row.getUnitCode().trim());
            }
        }

        MeetGroup group = null;
        if (!StringUtils.hasText(row.getGroupCode())) {
            errors.add("组别编码不能为空");
        } else {
            group = findGroup(row.getGroupCode(), context);
            if (group == null) {
                errors.add("组别编码不存在或已停用：" + row.getGroupCode().trim());
            }
        }

        if (group != null && gender != null
                && (GENDER_MALE.equals(group.getGender()) || GENDER_FEMALE.equals(group.getGender()))
                && !group.getGender().equals(gender)) {
            errors.add("性别与组别不一致（组别：" + group.getGroupName() + "）");
        }
        if (group != null && birthDate != null) {
            if (group.getBirthDateStart() != null && birthDate.isBefore(group.getBirthDateStart())) {
                errors.add("出生日期早于组别下限 " + group.getBirthDateStart());
            }
            if (group.getBirthDateEnd() != null && birthDate.isAfter(group.getBirthDateEnd())) {
                errors.add("出生日期晚于组别上限 " + group.getBirthDateEnd());
            }
        }

        if (StringUtils.hasText(row.getBibNo())) {
            String bibNo = row.getBibNo().trim();
            if (bibNo.length() > 32) {
                errors.add("号码长度不能超过 32 个字符");
            } else if (context.getBibNos().contains(bibNo)) {
                errors.add("号码已存在（届次内唯一）：" + bibNo);
            }
        }

        if (StringUtils.hasText(row.getQualificationStatus()) && parseQualification(row.getQualificationStatus()) == null) {
            errors.add("资格状态只能填写「待审/合格/不合格/禁赛」");
        }
        if (StringUtils.hasText(row.getPhone()) && row.getPhone().trim().length() > 32) {
            errors.add("联系电话长度不能超过 32 个字符");
        }
        if (StringUtils.hasText(row.getGuardianPhone()) && row.getGuardianPhone().trim().length() > 32) {
            errors.add("监护人电话长度不能超过 32 个字符");
        }
        if (StringUtils.hasText(row.getClassName()) && row.getClassName().trim().length() > 64) {
            errors.add("班级长度不能超过 64 个字符");
        }
        if (unit != null && unit.getTotalQuota() != null && unit.getTotalQuota() > 0
                && !context.isQuotaAvailable(unit.getId())) {
            errors.add("单位报名人数已达上限（" + unit.getTotalQuota() + " 人）");
        }
        return errors;
    }

    private Athlete toEntity(AthleteImportRow row, Long meetId, Unit unit, MeetGroup group, ImportContext context) {
        Athlete athlete = new Athlete();
        athlete.setMeetId(meetId);
        athlete.setAthleteName(row.getAthleteName().trim());
        athlete.setGender(parseGender(row.getGender()));
        athlete.setBirthDate(parseDate(row.getBirthDate()));
        athlete.setIdCard(row.getIdCard().trim());
        athlete.setUnitId(unit.getId());
        athlete.setGroupId(group.getId());
        athlete.setBibNo(StringUtils.hasText(row.getBibNo()) ? row.getBibNo().trim() : null);
        athlete.setClassName(trimToNull(row.getClassName()));
        athlete.setPhone(trimToNull(row.getPhone()));
        athlete.setGuardian(trimToNull(row.getGuardian()));
        athlete.setGuardianPhone(trimToNull(row.getGuardianPhone()));
        String qualification = parseQualification(row.getQualificationStatus());
        athlete.setQualificationStatus(qualification == null ? QUALIFY_PASSED : qualification);
        athlete.setIsRelayOnly(parseBoolean(row.getRelayOnly()));
        athlete.setEntrySource(ENTRY_SOURCE_IMPORT);
        athlete.setRemark(trimToNull(row.getRemark()));
        context.countUnit(unit.getId());
        return athlete;
    }

    // ------------------------------------------------------------------
    // 类型转换与工具
    // ------------------------------------------------------------------

    private Unit findUnit(String key, ImportContext context) {
        return StringUtils.hasText(key) ? context.getUnitKeys().get(key.trim().toLowerCase()) : null;
    }

    private MeetGroup findGroup(String key, ImportContext context) {
        return StringUtils.hasText(key) ? context.getGroupKeys().get(key.trim().toLowerCase()) : null;
    }

    private String parseGender(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase(GENDER_MALE) || "男".equals(value) || "男性".equals(value) || "1".equals(value)) {
            return GENDER_MALE;
        }
        if (value.equalsIgnoreCase(GENDER_FEMALE) || "女".equals(value) || "女性".equals(value) || "2".equals(value)) {
            return GENDER_FEMALE;
        }
        return null;
    }

    private String parseQualification(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase(QUALIFY_PENDING) || "待审".equals(value) || "待审核".equals(value)) {
            return QUALIFY_PENDING;
        }
        if (value.equalsIgnoreCase(QUALIFY_PASSED) || "合格".equals(value) || "通过".equals(value)) {
            return QUALIFY_PASSED;
        }
        if (value.equalsIgnoreCase(QUALIFY_FAILED) || "不合格".equals(value) || "不通过".equals(value)) {
            return QUALIFY_FAILED;
        }
        if (value.equalsIgnoreCase(QUALIFY_BANNED) || "禁赛".equals(value)) {
            return QUALIFY_BANNED;
        }
        return null;
    }

    private Integer parseBoolean(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        String value = raw.trim();
        return ("是".equals(value) || "Y".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value) || "1".equals(value)) ? 1 : 0;
    }

    private LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{DATE_FORMATTER, DATE_FORMATTER_SLASH, DATE_FORMATTER_COMPACT}) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }

    /**
     * 从既有号码中解析序号（形如「一中-003」或纯数字）。
     */
    private Integer parseBibSeq(String bibNo) {
        if (!StringUtils.hasText(bibNo)) {
            return null;
        }
        String digits = bibNo.replaceAll("\\D+", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return Integer.valueOf(digits.length() > 6 ? digits.substring(digits.length() - 6) : digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildMessage(AthleteImportResultVO result, boolean dryRun) {
        StringBuilder builder = new StringBuilder();
        builder.append(dryRun ? "校验完成：" : "导入完成：")
                .append("共解析 ").append(result.getTotalCount()).append(" 行，")
                .append(dryRun ? "通过 " : "成功 ").append(result.getSuccessCount()).append(" 行，")
                .append("失败 ").append(result.getFailCount()).append(" 行");
        if (result.getAutoBibCount() != null && result.getAutoBibCount() > 0) {
            builder.append("；自动生成号码 ").append(result.getAutoBibCount()).append(" 个");
        }
        if (result.getFailCount() != null && result.getFailCount() > 0) {
            builder.append("；请下载错误报告修正后重新导入（错误明细仅返回前 ").append(MAX_ERROR_RETURN).append(" 条）");
        }
        return builder.toString();
    }

    private AthleteImportErrorRow buildErrorRow(int rowNum, AthleteImportRow row, String message) {
        AthleteImportErrorRow errorRow = new AthleteImportErrorRow();
        errorRow.setRowNum(rowNum);
        errorRow.setAthleteName(row.getAthleteName());
        errorRow.setGender(row.getGender());
        errorRow.setBirthDate(row.getBirthDate());
        errorRow.setIdCard(row.getIdCard());
        errorRow.setUnitCode(row.getUnitCode());
        errorRow.setGroupCode(row.getGroupCode());
        errorRow.setBibNo(row.getBibNo());
        errorRow.setErrorMessage(message);
        return errorRow;
    }

    private AthleteImportErrorVO toErrorVO(AthleteImportErrorRow errorRow) {
        return new AthleteImportErrorVO(errorRow.getRowNum(), errorRow.getAthleteName(), errorRow.getIdCard(),
                errorRow.getUnitCode(), errorRow.getGroupCode(), errorRow.getErrorMessage());
    }

    private String buildErrorFile(List<AthleteImportErrorRow> errorRows) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            EasyExcel.write(outputStream, AthleteImportErrorRow.class).sheet("错误明细").doWrite(errorRows);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            log.warn("生成错误报告失败: {}", ex.getMessage());
            return null;
        }
    }

    private Long saveTask(MultipartFile file, AthleteImportResultVO result, long startMillis) {
        try {
            SysImportTask task = new SysImportTask();
            task.setTaskName("运动员导入：" + file.getOriginalFilename());
            task.setBizType("ATHLETE");
            task.setTaskDirection("IMPORT");
            task.setImportMode("INSERT");
            task.setTotalCount(result.getTotalCount());
            task.setSuccessCount(result.getSuccessCount());
            task.setFailCount(result.getFailCount());
            task.setProgress(100);
            task.setStatus(result.getFailCount() == 0 ? "SUCCESS"
                    : (result.getSuccessCount() > 0 ? "PARTIAL" : "FAILED"));
            task.setOperatorId(SecurityUtils.getUserId());
            task.setStartTime(LocalDateTime.now().minusNanos((System.currentTimeMillis() - startMillis) * 1_000_000L));
            task.setFinishTime(LocalDateTime.now());
            task.setCostMs((int) (System.currentTimeMillis() - startMillis));
            task.setMessage(result.getMessage());
            importTaskMapper.insert(task);
            return task.getId();
        } catch (Exception ex) {
            log.warn("写入导入任务记录失败: {}", ex.getMessage());
            return null;
        }
    }

    private AthleteImportRow buildSample(String name, String gender, String birthDate, String idCard, String unitCode,
                                        String groupCode, String bibNo, String className, String phone,
                                        String guardian, String guardianPhone, String qualification,
                                        String relayOnly, String remark) {
        AthleteImportRow row = new AthleteImportRow();
        row.setAthleteName(name);
        row.setGender(gender);
        row.setBirthDate(birthDate);
        row.setIdCard(idCard);
        row.setUnitCode(unitCode);
        row.setGroupCode(groupCode);
        row.setBibNo(bibNo);
        row.setClassName(className);
        row.setPhone(phone);
        row.setGuardian(guardian);
        row.setGuardianPhone(guardianPhone);
        row.setQualificationStatus(qualification);
        row.setRelayOnly(relayOnly);
        row.setRemark(remark);
        return row;
    }

    private List<AthleteImportTipRow> buildTips() {
        List<AthleteImportTipRow> tips = new ArrayList<>();
        tips.add(new AthleteImportTipRow("姓名*", "必填", "运动员真实姓名，长度不超过 50 个字符"));
        tips.add(new AthleteImportTipRow("性别*（男/女）", "必填", "填写「男」或「女」，也可填 MALE / FEMALE"));
        tips.add(new AthleteImportTipRow("出生日期*（yyyy-MM-dd）", "必填", "如 2008-03-12，用于组别年龄校验"));
        tips.add(new AthleteImportTipRow("身份证号*", "必填", "届次内唯一，重复将导入失败；长度不超过 32"));
        tips.add(new AthleteImportTipRow("单位编码*", "必填", "与「单位管理」中的单位编码一致（也支持填写单位名称）"));
        tips.add(new AthleteImportTipRow("组别编码*", "必填", "与「组别管理」中的组别编码一致（也支持填写组别名称）"));
        tips.add(new AthleteImportTipRow("号码（留空自动生成）", "选填", "留空则按「单位前缀-序号」自动生成；填写时届次内唯一"));
        tips.add(new AthleteImportTipRow("班级", "选填", "如 高二(1)班，长度不超过 64"));
        tips.add(new AthleteImportTipRow("联系电话", "选填", "长度不超过 32"));
        tips.add(new AthleteImportTipRow("监护人", "选填", "青少年赛事建议填写"));
        tips.add(new AthleteImportTipRow("监护人电话", "选填", "长度不超过 32"));
        tips.add(new AthleteImportTipRow("资格状态（待审/合格/不合格/禁赛）", "选填", "留空按「合格」处理"));
        tips.add(new AthleteImportTipRow("是否仅参加接力（是/否）", "选填", "留空按「否」处理"));
        tips.add(new AthleteImportTipRow("备注", "选填", "长度不超过 100"));
        tips.add(new AthleteImportTipRow("使用说明", "-", "1) 请勿修改表头；2) 示例行请在导入前删除；3) 支持先以 dryRun=true 校验，通过后再正式导入；4) 失败行会生成错误报告 Excel 可下载修正。"));
        return tips;
    }

    // ------------------------------------------------------------------
    // 导入上下文
    // ------------------------------------------------------------------

    /**
     * 导入上下文：缓存单位/组别索引与唯一键，支持号码自动生成。
     */
    private static class ImportContext {

        private final List<Unit> units = new ArrayList<>();
        private final Map<Long, Unit> unitById = new HashMap<>();
        private final Map<String, Unit> unitKeys = new HashMap<>();
        private final Map<String, MeetGroup> groupKeys = new HashMap<>();
        private final Set<String> idCards = new HashSet<>();
        private final Set<String> bibNos = new HashSet<>();
        private final Map<Long, Integer> unitBibSeq = new HashMap<>();
        private final Map<Long, Integer> unitCount = new HashMap<>();
        private final Map<Long, Integer> unitExistingCount = new HashMap<>();

        /**
         * 生成下一个号码：单位前缀 + 三位序号，冲突时继续顺延。
         */
        String nextBibNo(Unit unit) {
            String prefix = StringUtils.hasText(unit.getNumberPrefix()) ? unit.getNumberPrefix().trim() : unit.getUnitCode();
            if (prefix.length() > 24) {
                prefix = prefix.substring(0, 24);
            }
            String candidate;
            do {
                int seq = unitBibSeq.merge(unit.getId(), 1, Integer::sum);
                candidate = seq > MAX_BIB_SEQ
                        ? prefix + "-" + seq
                        : prefix + "-" + String.format("%03d", seq);
            } while (bibNos.contains(candidate));
            return candidate;
        }

        void countUnit(Long unitId) {
            unitCount.merge(unitId, 1, Integer::sum);
        }

        void countExisting(Long unitId) {
            unitExistingCount.merge(unitId, 1, Integer::sum);
        }

        /**
         * 单位报名总人数上限校验：已存在人数 + 本次导入人数 < 上限。
         */
        boolean isQuotaAvailable(Long unitId) {
            Unit unit = unitById.get(unitId);
            if (unit == null || unit.getTotalQuota() == null || unit.getTotalQuota() <= 0) {
                return true;
            }
            int existing = unitExistingCount.getOrDefault(unitId, 0);
            int imported = unitCount.getOrDefault(unitId, 0);
            return existing + imported < unit.getTotalQuota();
        }

        List<Unit> getUnits() {
            return units;
        }

        Map<Long, Unit> getUnitById() {
            return unitById;
        }

        Map<String, Unit> getUnitKeys() {
            return unitKeys;
        }

        Map<String, MeetGroup> getGroupKeys() {
            return groupKeys;
        }

        Set<String> getIdCards() {
            return idCards;
        }

        Set<String> getBibNos() {
            return bibNos;
        }

        Map<Long, Integer> getUnitBibSeq() {
            return unitBibSeq;
        }
    }
}
