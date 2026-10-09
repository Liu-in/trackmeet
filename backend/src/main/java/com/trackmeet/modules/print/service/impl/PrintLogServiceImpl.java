package com.trackmeet.modules.print.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.RequestUtils;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.print.constant.PrintReportType;
import com.trackmeet.modules.print.dto.PrintLogQuery;
import com.trackmeet.modules.print.dto.PrintLogSaveDTO;
import com.trackmeet.modules.print.entity.PrintLog;
import com.trackmeet.modules.print.mapper.PrintLogMapper;
import com.trackmeet.modules.print.service.PrintLogService;
import com.trackmeet.modules.print.vo.PrintLogVO;
import com.trackmeet.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印留痕服务实现。
 * <p>
 * {@code sys_print_log} 为系统表（无 {@code meet_id} 列，多租户拦截器按 {@code sys_} 前缀忽略），
 * 因此分页查询走自定义 SQL：自带 {@code deleted = 0} 与 {@code meet_ref_id} 条件。
 * <p>
 * 错误留痕的兜底责任在调用方：{@link #recordFailure} 自身不吞异常（事务语义正确），
 * 由渲染编排在 {@code try/catch} 中包裹，保证「原始失败异常」始终是对外抛出的那一个。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintLogServiceImpl extends ServiceImpl<PrintLogMapper, PrintLog> implements PrintLogService {

    /** 每页最大条数（与项目其它列表一致） */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 报表名称列长度 */
    private static final int REPORT_NAME_MAX = 100;

    /** 操作人姓名列长度 */
    private static final int OPERATOR_NAME_MAX = 50;

    /** 打印设备列长度 */
    private static final int PRINT_DEVICE_MAX = 100;

    /** IP 列长度 */
    private static final int IP_MAX = 64;

    /** 失败原因列长度 */
    private static final int ERROR_MSG_MAX = 255;

    /** params_json 直接落库的长度上限（超过则降级为摘要 JSON） */
    private static final int PARAMS_JSON_MAX = 4000;

    /** 摘要 JSON 中保留的原文长度 */
    private static final int PARAMS_PREVIEW = 1500;

    /** 兜底报表类型（{@code report_type} 列 NOT NULL） */
    private static final String UNKNOWN_REPORT_TYPE = "UNKNOWN";

    /** 纯日期文本长度（{@code yyyy-MM-dd}） */
    private static final int DATE_TEXT_LENGTH = 10;

    /** 时间边界解析格式（按顺序尝试） */
    private static final List<DateTimeFormatter> BOUNDARY_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

    private final ObjectMapper objectMapper;

    @Override
    public PageResult<PrintLogVO> pageLogs(PrintLogQuery query) {
        PrintLogQuery condition = query == null ? new PrintLogQuery() : query;
        Long meetRefId = resolveMeetRefId(condition.getMeetRefId() != null
                ? condition.getMeetRefId() : condition.getMeetId());
        long pageNo = condition.pageNo();
        long pageSize = Math.min(condition.pageSize(), MAX_PAGE_SIZE);
        String reportType = PrintReportType.normalizeCode(condition.getReportType());
        String format = upper(condition.getFormat());
        String status = upper(condition.getStatus());
        String keyword = StringUtils.hasText(condition.getKeyword()) ? condition.getKeyword().trim() : null;
        LocalDateTime beginTime = beginTime(condition);
        LocalDateTime endTime = endTime(condition);

        long total = baseMapper.countLogs(reportType, format, status, meetRefId, beginTime, endTime, keyword);
        if (total <= 0) {
            return PageResult.empty(pageNo, pageSize);
        }
        List<PrintLog> rows = baseMapper.selectLogPage(reportType, format, status, meetRefId, beginTime, endTime,
                keyword, pageSize, (pageNo - 1) * pageSize);
        List<PrintLogVO> records = new ArrayList<>(rows.size());
        for (PrintLog row : rows) {
            records.add(toVO(row));
        }
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveLog(PrintLogSaveDTO dto, String ip) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "留痕参数不能为空");
        }
        if (!StringUtils.hasText(dto.getReportType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "报表类型不能为空");
        }
        Long meetRefId = resolveMeetRefId(dto.getMeetRefId());
        String resolvedIp = StringUtils.hasText(dto.getIp()) ? dto.getIp().trim() : ip;
        PrintLog entity = build(PrintReportType.normalizeCode(dto.getReportType()), dto.getReportName(),
                paramsJson(dto.getParamsJson()), normalizeFormat(dto.getFormat(), PrintLog.FORMAT_PDF), meetRefId,
                dto.getTemplateId(), dto.getFileId(), dto.getPrintCount(), dto.getPrintDevice(), resolvedIp,
                dto.getStatus(), dto.getErrorMsg(), dto.getPrintTime());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordSuccess(PrintReportType type, String format, Object params, Long meetRefId, Long templateId,
                              Integer printCount, String ip) {
        PrintLog entity = build(reportCode(type), type == null ? null : type.getReportName(),
                paramsJson(params), normalizeFormat(format, PrintLog.FORMAT_PDF), meetRefId, templateId, null,
                printCount, null, ip, PrintLog.STATUS_SUCCESS, null, LocalDateTime.now());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(PrintReportType type, String format, Object params, Long meetRefId, Long templateId,
                              String ip, String errorMsg) {
        PrintLog entity = build(reportCode(type), type == null ? null : type.getReportName(),
                paramsJson(params), normalizeFormat(format, PrintLog.FORMAT_PDF), meetRefId, templateId, null,
                1, null, ip, PrintLog.STATUS_FAILED, errorMsg, LocalDateTime.now());
        save(entity);
    }

    @Override
    public PrintLogVO toVO(PrintLog entity) {
        PrintLogVO vo = new PrintLogVO();
        if (entity == null) {
            return vo;
        }
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    // ==================================================================================
    // 内部工具
    // ==================================================================================

    /**
     * 组装留痕实体（成功 / 失败 / 手工上报共用）。
     */
    private PrintLog build(String reportType, String reportName, String paramsJson, String format, Long meetRefId,
                           Long templateId, Long fileId, Integer printCount, String printDevice, String ip,
                           String status, String errorMsg, LocalDateTime printTime) {
        PrintLog entity = new PrintLog();
        entity.setReportType(StringUtils.hasText(reportType) ? reportType : UNKNOWN_REPORT_TYPE);
        entity.setReportName(RequestUtils.truncate(reportName, REPORT_NAME_MAX));
        entity.setParamsJson(paramsJson);
        entity.setFormat(format);
        entity.setMeetRefId(meetRefId);
        entity.setFileId(fileId);
        entity.setTemplateId(templateId);
        entity.setOperatorId(SecurityUtils.getUserId());
        entity.setOperatorName(RequestUtils.truncate(operatorName(), OPERATOR_NAME_MAX));
        entity.setPrintCount(printCount == null || printCount < 1 ? 1 : printCount);
        entity.setPrintDevice(RequestUtils.truncate(printDevice, PRINT_DEVICE_MAX));
        entity.setIp(RequestUtils.truncate(ip, IP_MAX));
        String resolvedStatus = StringUtils.hasText(status)
                ? status.trim().toUpperCase() : PrintLog.STATUS_SUCCESS;
        entity.setStatus(resolvedStatus);
        entity.setErrorMsg(PrintLog.STATUS_FAILED.equals(resolvedStatus)
                ? RequestUtils.truncate(errorMsg, ERROR_MSG_MAX) : null);
        entity.setPrintTime(printTime == null ? LocalDateTime.now() : printTime);
        return entity;
    }

    /**
     * 操作人姓名快照：优先真实姓名，缺失时退回登录名。
     */
    private String operatorName() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return null;
        }
        return StringUtils.hasText(loginUser.getRealName()) ? loginUser.getRealName() : loginUser.getUsername();
    }

    /**
     * 报表类型留痕码（{@code report_type} 列 NOT NULL）。
     */
    private String reportCode(PrintReportType type) {
        return type == null ? UNKNOWN_REPORT_TYPE : type.getReportCode();
    }

    /**
     * 参数快照 → 合法 JSON 文本（{@code params_json} 为 JSON 列，必须始终写合法 JSON）：
     * 已是合法 JSON 文本时原样写入；对象序列化；超长时降级为「摘要 JSON」。
     */
    private String paramsJson(Object params) {
        if (params == null) {
            return null;
        }
        String json;
        if (params instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                objectMapper.readTree(trimmed);
                json = trimmed;
            } catch (Exception ex) {
                Map<String, Object> raw = new LinkedHashMap<>(2);
                raw.put("raw", RequestUtils.truncate(trimmed, PARAMS_PREVIEW));
                return writeJson(raw);
            }
        } else {
            try {
                json = objectMapper.writeValueAsString(params);
            } catch (Exception ex) {
                log.debug("序列化打印参数失败，留痕不记录参数快照：{}", ex.getMessage());
                return null;
            }
        }
        if (json == null || json.length() <= PARAMS_JSON_MAX) {
            return json;
        }
        Map<String, Object> summary = new LinkedHashMap<>(4);
        summary.put("truncated", true);
        summary.put("length", json.length());
        summary.put("preview", json.substring(0, PARAMS_PREVIEW));
        return writeJson(summary);
    }

    /**
     * 序列化 Map（失败返回 null，不抛异常）。
     */
    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.debug("序列化留痕摘要失败：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * 留痕报表类型归一化。
     */
    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }

    /**
     * 格式归一化：空值取默认值，非法值保留大写原值（{@code format} 列允许 WORD 等扩展值）。
     */
    private String normalizeFormat(String format, String defaultFormat) {
        return StringUtils.hasText(format) ? format.trim().toUpperCase() : defaultFormat;
    }

    /**
     * 打印时间起：{@code beginTime} 精确值优先，其次自然日 {@code startDate} 的 00:00:00。
     */
    private LocalDateTime beginTime(PrintLogQuery query) {
        LocalDateTime parsed = parseBoundary(query.getBeginTime(), false);
        if (parsed != null) {
            return parsed;
        }
        return query.getStartDate() == null ? null : query.getStartDate().atStartOfDay();
    }

    /**
     * 打印时间止：{@code endTime} 精确值优先，其次自然日 {@code endDate} 的 23:59:59。
     */
    private LocalDateTime endTime(PrintLogQuery query) {
        LocalDateTime parsed = parseBoundary(query.getEndTime(), true);
        if (parsed != null) {
            return parsed;
        }
        return query.getEndDate() == null ? null : query.getEndDate().atTime(23, 59, 59);
    }

    /**
     * 宽容解析时间边界：支持 {@code yyyy-MM-dd}（按边界补 00:00:00 / 23:59:59）、
     * {@code yyyy-MM-dd HH:mm}、{@code yyyy-MM-dd HH:mm:ss} 与 ISO 的 {@code T} 分隔写法。
     *
     * @param value     原始文本（可空）
     * @param endOfDay  仅日期时是否取当日 23:59:59
     * @return 解析结果，未传值时返回 {@code null}
     * @throws BizException 格式非法
     */
    private LocalDateTime parseBoundary(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        if (text.length() == DATE_TEXT_LENGTH) {
            try {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            } catch (Exception ex) {
                throw new BizException(ResultCode.PARAM_ERROR, "打印时间格式应为 yyyy-MM-dd：" + value);
            }
        }
        for (DateTimeFormatter formatter : BOUNDARY_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        throw new BizException(ResultCode.PARAM_ERROR,
                "打印时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm[:ss]：" + value);
    }

    /**
     * 届次弱引用解析：入参与 {@link MeetContext} 同时存在且不一致时抛 {@code FORBIDDEN}。
     */
    private Long resolveMeetRefId(Long requested) {
        Long contextMeetId = MeetContext.get();
        if (requested != null && contextMeetId != null && !requested.equals(contextMeetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "请求届次与当前届次上下文不一致，禁止跨届次查询打印留痕");
        }
        return requested != null ? requested : contextMeetId;
    }
}
