package com.trackmeet.modules.standing.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import com.trackmeet.modules.standing.dto.CertificateGenerateDTO;
import com.trackmeet.modules.standing.dto.StandingCertificateRow;
import com.trackmeet.modules.standing.dto.StandingMeetRow;
import com.trackmeet.modules.standing.dto.StandingParticipantRow;
import com.trackmeet.modules.standing.dto.StandingResultRow;
import com.trackmeet.modules.standing.entity.Certificate;
import com.trackmeet.modules.standing.excel.CertificateExcel;
import com.trackmeet.modules.standing.mapper.StandingMapper;
import com.trackmeet.modules.standing.service.CertificateService;
import com.trackmeet.modules.standing.service.support.StandingRuleResolver;
import com.trackmeet.modules.standing.service.support.StandingText;
import com.trackmeet.modules.standing.vo.CertificateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 证书服务实现：批量生成、列表查询、导出与打印计数。
 * <p>
 * <b>取数范围</b>（全部基于 {@code result} / {@code registration} 的已审核数据）：
 * <ul>
 *     <li>{@code RANK} 获奖证书：已审核 + 成绩有效 + 名次 ≤ 录取名次（默认 8）；</li>
 *     <li>{@code MEDAL} 奖牌证书：已审核 + 成绩有效 + 名次 ∈ {1,2,3}；</li>
 *     <li>{@code RESULT} 成绩证书：已审核 + 成绩有效（有成绩数值）的全部记录；</li>
 *     <li>{@code PARTICIPATION} 参赛证书：已通过报名（{@code registration.status=APPROVED}）的记录。</li>
 * </ul>
 * <b>编号唯一性</b>：{@code uk_cert_no} 为全局唯一且不含 {@code deleted}，因此序号按
 * 「库内既有同前缀条数 + 1」递推（统计包含逻辑删除行），保证不撞唯一键。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl extends ServiceImpl<StandingMapper, Certificate> implements CertificateService {

    /** 录取名次默认值（获奖证书） */
    private static final int DEFAULT_ADMIT_RANK = 8;

    /** 验真链接前缀 */
    private static final String VERIFY_URL_PREFIX = "https://trackmeet.example.com/cert/verify?certNo=";

    /** 每页条数上限 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 届次编码提取：优先取年份数字（如「2025年第19届」→ 2025） */
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

    /** Excel Sheet 名 */
    private static final String SHEET_NAME = "证书清单";

    private final StandingMapper standingMapper;
    private final StandingRuleResolver ruleResolver;

    // ==================================================================================
    // 一、生成
    // ==================================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generate(CertificateGenerateDTO dto) {
        if (dto == null || dto.getMeetId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        Long meetId = dto.getMeetId();
        checkMeetContext(meetId);
        String certType = dto.normalizedCertType();

        StandingMeetRow meet = standingMapper.selectMeetInfo(meetId);
        if (meet == null) {
            throw new BizException(ResultCode.NOT_FOUND, "届次不存在");
        }
        String editionCode = editionCode(meetId, meet);
        String issuer = StringUtils.hasText(meet.getHost()) ? meet.getHost() : meet.getOrganizer();
        LocalDate issueDate = LocalDate.now();
        // 单次请求内唯一的已发编号集合（避免实例字段造成并发串号）
        Set<String> issuedInBatch = new HashSet<>();

        List<Certificate> entities = "PARTICIPATION".equals(certType)
                ? buildParticipation(meetId, dto, certType, editionCode, issuer, issueDate, issuedInBatch)
                : buildFromResults(meetId, dto, certType, editionCode, issuer, issueDate, issuedInBatch);

        if (entities.isEmpty()) {
            throw new BizException(ResultCode.CONFLICT, "没有符合条件的记录可生成证书，请先确认成绩已审核");
        }
        saveBatch(entities, 500);
        log.info("证书生成完成：meetId={}, certType={}, count={}", meetId, certType, entities.size());
        return entities.size();
    }

    /**
     * 基于成绩生成 {@code RANK} / {@code MEDAL} / {@code RESULT} 证书。
     */
    private List<Certificate> buildFromResults(Long meetId, CertificateGenerateDTO dto, String certType,
                                               String editionCode, String issuer, LocalDate issueDate,
                                               Set<String> issuedInBatch) {
        List<StandingResultRow> rows = standingMapper.selectApprovedResults(meetId, dto.getEventId(),
                dto.getGroupId(), dto.getUnitId());
        int admitRankLimit = admitRank(meetId);
        List<Certificate> entities = new ArrayList<>(rows.size());
        for (StandingResultRow row : rows) {
            Integer rankNo = row.getRankNo();
            boolean valid = row.getResultValue() != null
                    && (row.getStatus() == null || "VALID".equalsIgnoreCase(row.getStatus()));
            if (!valid) {
                continue;
            }
            if ("MEDAL".equals(certType) && (rankNo == null || rankNo < 1 || rankNo > 3)) {
                continue;
            }
            if ("RANK".equals(certType) && (rankNo == null || rankNo > admitRankLimit)) {
                continue;
            }
            Certificate entity = new Certificate();
            entity.setMeetId(meetId);
            entity.setCertType(certType);
            entity.setTemplateId(dto.getTemplateId());
            entity.setAthleteId(row.getAthleteId());
            entity.setUnitId(row.getUnitId());
            entity.setEventId(row.getEventId());
            entity.setRoundId(row.getRoundId());
            entity.setResultId(row.getResultId());
            entity.setRankNo(rankNo);
            entity.setRankDisplay(StandingText.rankDisplay(row.getRankDisplay(), rankNo));
            entity.setResultText(StandingText.text(row.getResultRaw()));
            entity.setHonorText(honorText(row, rankNo, certType));
            entity.setIssueDate(issueDate);
            entity.setIssuer(issuer);
            entity.setStatus(Certificate.STATUS_GENERATED);
            entity.setPrintCount(0);
            String certNo = nextCertNo(meetId, editionCode, certType, row.getEventId(), rankNo, issuedInBatch);
            entity.setCertNo(certNo);
            entity.setQrContent(VERIFY_URL_PREFIX + certNo);
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 基于报名生成 {@code PARTICIPATION} 参赛证书。
     */
    private List<Certificate> buildParticipation(Long meetId, CertificateGenerateDTO dto, String certType,
                                                 String editionCode, String issuer, LocalDate issueDate,
                                                 Set<String> issuedInBatch) {
        List<StandingParticipantRow> rows = standingMapper.selectParticipants(meetId, dto.getEventId(),
                dto.getGroupId(), dto.getUnitId());
        List<Certificate> entities = new ArrayList<>(rows.size());
        for (StandingParticipantRow row : rows) {
            Certificate entity = new Certificate();
            entity.setMeetId(meetId);
            entity.setCertType(certType);
            entity.setTemplateId(dto.getTemplateId());
            entity.setAthleteId(row.getAthleteId());
            entity.setUnitId(row.getUnitId());
            entity.setEventId(row.getEventId());
            entity.setResultId(null);
            entity.setRankNo(null);
            entity.setRankDisplay("参赛");
            String name = StringUtils.hasText(row.getAthleteName()) ? row.getAthleteName() : row.getTeamName();
            entity.setResultText(null);
            entity.setHonorText(StandingText.text(StandingText.text(name) + " " + StandingText.text(row.getEventName())
                    + " 参赛证书"));
            entity.setIssueDate(issueDate);
            entity.setIssuer(issuer);
            entity.setStatus(Certificate.STATUS_GENERATED);
            entity.setPrintCount(0);
            String certNo = nextCertNo(meetId, editionCode, certType, row.getEventId(), null, issuedInBatch);
            entity.setCertNo(certNo);
            entity.setQrContent(VERIFY_URL_PREFIX + certNo);
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 荣誉描述：如「男子甲组 100 米第一名」。
     */
    private String honorText(StandingResultRow row, Integer rankNo, String certType) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(row.getGroupName())) {
            builder.append(row.getGroupName()).append(' ');
        }
        builder.append(StandingText.text(row.getEventName()));
        if ("PARTICIPATION".equals(certType)) {
            builder.append(" 参赛");
        } else if ("MEDAL".equals(certType)) {
            builder.append(' ').append(medalText(rankNo));
        } else if ("RANK".equals(certType)) {
            builder.append(" 第").append(rankNo == null ? "-" : rankNo).append("名");
        } else {
            builder.append(" 成绩证书");
        }
        return builder.toString();
    }

    private String medalText(Integer rankNo) {
        if (rankNo == null) {
            return "奖牌";
        }
        return switch (rankNo) {
            case 1 -> "金牌";
            case 2 -> "银牌";
            case 3 -> "铜牌";
            default -> "奖牌";
        };
    }

    /**
     * 生成证书编号：{@code <届次编码>-<类型>-<项目>-<名次>-<序号>}。
     * <p>
     * 序号 = 库内既有同前缀条数 + 1；批量循环内还需校验本批次已发出的编号，避免同批前缀碰撞。
     * {@code issuedInBatch} 由调用方在单次请求内创建并传递，<b>不持有为实例字段</b>，避免并发串号。
     *
     * @param meetId        届次 ID
     * @param editionCode   届次编码
     * @param certType      证书类型
     * @param eventId       项目 ID（可空）
     * @param rankNo        名次（可空，参赛证书为空）
     * @param issuedInBatch 本批次已发出的编号集合
     * @return 唯一证书编号
     */
    private String nextCertNo(Long meetId, String editionCode, String certType, Long eventId, Integer rankNo,
                              Set<String> issuedInBatch) {
        String prefix = editionCode + "-" + certType + "-" + (eventId == null ? "0" : eventId)
                + "-" + (rankNo == null ? 0 : rankNo) + "-";
        long sequence = standingMapper.countCertNoByPrefix(meetId, prefix) + 1;
        String certNo = prefix + sequence;
        while (issuedInBatch.contains(certNo)) {
            sequence++;
            certNo = prefix + sequence;
        }
        issuedInBatch.add(certNo);
        return certNo;
    }

    /**
     * 届次编码：优先取 {@code meet.edition} 中的 4 位年份，缺失时退回 {@code M<meetId>}。
     */
    private String editionCode(Long meetId, StandingMeetRow meet) {
        String edition = meet.getEdition();
        if (StringUtils.hasText(edition)) {
            Matcher matcher = YEAR_PATTERN.matcher(edition);
            if (matcher.find()) {
                return matcher.group(1);
            }
            String cleaned = edition.replaceAll("[^0-9A-Za-z]", "");
            if (!cleaned.isEmpty()) {
                return cleaned.length() > 8 ? cleaned.substring(0, 8) : cleaned;
            }
        }
        if (meet.getStartDate() != null) {
            return String.valueOf(meet.getStartDate().getYear());
        }
        return "M" + meetId;
    }

    /**
     * 录取名次：取届次规则 {@code meet_rule_config.admit_rank}，缺失时默认 8。
     *
     * @param meetId 届次 ID
     * @return 录取名次
     */
    private int admitRank(Long meetId) {
        MeetRuleConfig config = ruleResolver.loadMeetRuleConfig(meetId);
        if (config == null || config.getAdmitRank() == null || config.getAdmitRank() <= 0) {
            return DEFAULT_ADMIT_RANK;
        }
        return config.getAdmitRank();
    }

    // ==================================================================================
    // 二、查询
    // ==================================================================================

    @Override
    public PageResult<CertificateVO> pageCertificates(Long meetId, String certType, Long unitId,
                                                      String keyword, long page, long size) {
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        checkMeetContext(meetId);
        long pageNo = page < 1 ? 1L : page;
        long pageSize = size < 1 ? 20L : Math.min(size, MAX_PAGE_SIZE);
        String type = StringUtils.hasText(certType) ? certType.trim().toUpperCase() : null;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long total = standingMapper.countCertificates(meetId, type, unitId, kw);
        if (total <= 0) {
            return PageResult.empty(pageNo, pageSize);
        }
        long offset = (pageNo - 1) * pageSize;
        List<StandingCertificateRow> rows = standingMapper.selectCertificatePage(meetId, type, unitId, kw,
                pageSize, offset);
        List<CertificateVO> records = new ArrayList<>(rows.size());
        for (StandingCertificateRow row : rows) {
            records.add(toVO(row));
        }
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Override
    public List<StandingCertificateRow> listByIds(Long meetId, List<Long> ids) {
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        checkMeetContext(meetId);
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return standingMapper.selectCertificatesByIds(meetId, ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markPrinted(Long meetId, List<Long> ids) {
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        checkMeetContext(meetId);
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "证书 ID 不能为空");
        }
        return standingMapper.increasePrintCount(meetId, ids, LocalDateTime.now());
    }

    // ==================================================================================
    // 三、导出
    // ==================================================================================

    @Override
    public void writeExcel(List<StandingCertificateRow> rows, OutputStream outputStream) {
        List<CertificateExcel> data = new ArrayList<>();
        if (rows != null) {
            for (StandingCertificateRow row : rows) {
                data.add(toExcel(row));
            }
        }
        try (ExcelWriter writer = EasyExcel.write(outputStream).build()) {
            WriteSheet sheet = EasyExcel.writerSheet(0, SHEET_NAME)
                    .head(CertificateExcel.class)
                    .autoTrim(false)
                    .build();
            writer.write(data, sheet);
        }
    }

    @Override
    public String buildFileName(String meetName) {
        String base = StringUtils.hasText(meetName) ? meetName.trim() : "证书";
        return base + "-证书清单.xlsx";
    }

    /**
     * 投影行 → Excel 行对象。
     */
    private CertificateExcel toExcel(StandingCertificateRow row) {
        CertificateExcel excel = new CertificateExcel();
        excel.setCertNo(StandingText.text(row.getCertNo()));
        excel.setCertTypeName(StandingText.certTypeName(row.getCertType()));
        String name = StringUtils.hasText(row.getAthleteName()) ? row.getAthleteName() : null;
        if (name == null && StringUtils.hasText(row.getBibNo())) {
            name = row.getBibNo();
        }
        excel.setAthleteName(StandingText.text(name));
        excel.setBibNo(StandingText.text(row.getBibNo()));
        excel.setUnitName(StandingText.text(StringUtils.hasText(row.getUnitShortName())
                ? row.getUnitShortName() : row.getUnitName()));
        excel.setEventName(StandingText.text(row.getEventName()));
        excel.setGroupName(StandingText.text(row.getGroupName()));
        excel.setRankDisplay(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        excel.setResultText(StandingText.text(row.getResultText()));
        excel.setHonorText(StandingText.text(row.getHonorText()));
        excel.setIssueDate(StandingText.date(row.getIssueDate()));
        excel.setIssuer(StandingText.text(row.getIssuer()));
        excel.setQrContent(StandingText.text(row.getQrContent()));
        excel.setStatusName(StandingText.certStatusName(row.getStatus()));
        return excel;
    }

    @Override
    public CertificateVO toVO(StandingCertificateRow row) {
        CertificateVO vo = new CertificateVO();
        if (row == null) {
            return vo;
        }
        vo.setId(row.getId());
        vo.setMeetId(row.getMeetId());
        vo.setCertNo(row.getCertNo());
        vo.setCertType(row.getCertType());
        vo.setCertTypeName(StandingText.certTypeName(row.getCertType()));
        vo.setTemplateId(row.getTemplateId());
        vo.setAthleteId(row.getAthleteId());
        vo.setAthleteName(StringUtils.hasText(row.getAthleteName()) ? row.getAthleteName() : null);
        vo.setBibNo(row.getBibNo());
        vo.setUnitId(row.getUnitId());
        vo.setUnitName(row.getUnitName());
        vo.setUnitShortName(row.getUnitShortName());
        vo.setEventId(row.getEventId());
        vo.setEventName(row.getEventName());
        vo.setEventCode(row.getEventCode());
        vo.setRoundId(row.getRoundId());
        vo.setRoundName(row.getRoundName());
        vo.setGroupName(row.getGroupName());
        vo.setResultId(row.getResultId());
        vo.setRankNo(row.getRankNo());
        vo.setRankDisplay(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        vo.setResultText(row.getResultText());
        vo.setHonorText(row.getHonorText());
        vo.setIssueDate(row.getIssueDate());
        vo.setIssuer(row.getIssuer());
        vo.setFileId(row.getFileId());
        vo.setQrContent(row.getQrContent());
        vo.setStatus(row.getStatus());
        vo.setPrintCount(row.getPrintCount());
        vo.setLastPrintTime(row.getLastPrintTime());
        vo.setCreateTime(row.getCreateTime());
        vo.setRemark(row.getRemark());
        return vo;
    }

    /**
     * 多租户校验：请求届次必须与 {@link MeetContext#get()} 一致。
     */
    private void checkMeetContext(Long meetId) {
        Long current = MeetContext.get();
        if (current != null && !current.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该届次数据");
        }
    }
}
