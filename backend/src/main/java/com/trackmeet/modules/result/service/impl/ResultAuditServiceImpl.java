package com.trackmeet.modules.result.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.RequestUtils;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.entity.ResultAttempt;
import com.trackmeet.modules.result.entity.ResultAudit;
import com.trackmeet.modules.result.mapper.ResultAttemptMapper;
import com.trackmeet.modules.result.mapper.ResultAuditMapper;
import com.trackmeet.modules.result.mapper.ResultMapper;
import com.trackmeet.modules.result.service.ResultAuditService;
import com.trackmeet.modules.result.vo.ResultAuditVO;
import com.trackmeet.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩留痕服务实现。
 * <p>
 * 留痕落点：{@code result_audit} 表，每次「提交 / 审核通过 / 驳回 / 改值 / 状态变更 / 撤销审核」
 * 都写入一条，包含：
 * <ul>
 *     <li>{@code action}：SUBMIT / APPROVE / REJECT / MODIFY / REVOKE；</li>
 *     <li>{@code before_json} / {@code after_json}：成绩快照（含各次试跳），供前端按字段对比展示；</li>
 *     <li>{@code reason}：修改原因 / 驳回原因 / 撤销原因；</li>
 *     <li>{@code operator_id} / {@code operator_name} / {@code operate_time} / {@code ip}：操作人与时间留痕。</li>
 * </ul>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class ResultAuditServiceImpl extends ServiceImpl<ResultAuditMapper, ResultAudit> implements ResultAuditService {

    /** 操作人姓名最大长度，与 result_audit.operator_name VARCHAR(50) 一致 */
    private static final int OPERATOR_NAME_MAX_LENGTH = 50;

    /** 原因文本最大长度，与 result_audit.reason VARCHAR(500) 一致 */
    private static final int REASON_MAX_LENGTH = 500;

    /** 快照序列化器（线程安全；注册 JSR-310 以支持 LocalDateTime） */
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ResultAuditMapper resultAuditMapper;

    private final ResultAttemptMapper resultAttemptMapper;

    private final ResultMapper resultMapper;

    @Override
    public List<ResultAuditVO> listByResult(Long resultId) {
        if (resultId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        Long meetId = currentMeetId();
        return resultAuditMapper.selectAuditVOs(meetId, resultId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(Long resultId, String action, Map<String, Object> before, Map<String, Object> after, String reason) {
        if (resultId == null || !StringUtils.hasText(action)) {
            throw new BizException(ResultCode.PARAM_ERROR, "留痕必须给出成绩 ID 与动作");
        }
        Long meetId = currentMeetId();
        ResultAudit audit = new ResultAudit();
        audit.setMeetId(meetId);
        audit.setResultId(resultId);
        audit.setAction(action);
        audit.setBeforeJson(toJson(before));
        audit.setAfterJson(toJson(after));
        audit.setReason(RequestUtils.truncate(reason, REASON_MAX_LENGTH));
        audit.setOperateTime(LocalDateTime.now());
        fillOperator(audit);
        // 补充项目 / 轮次 / 报名上下文，便于按项目与运动员维度检索留痕
        Result result = resultMapper.selectById(resultId);
        if (result != null) {
            audit.setEventId(result.getEventId());
            audit.setRoundId(result.getRoundId());
            audit.setRegistrationId(result.getRegistrationId());
        }
        if (!save(audit)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "成绩留痕写入失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordEntities(Object beforeResult, Object afterResult, String action, String reason) {
        Object effective = afterResult == null ? beforeResult : afterResult;
        if (!(effective instanceof Result result) || result.getId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "留痕必须给出成绩实体");
        }
        Long meetId = currentMeetId();
        ResultAudit audit = new ResultAudit();
        audit.setMeetId(meetId);
        audit.setResultId(result.getId());
        audit.setEventId(result.getEventId());
        audit.setRoundId(result.getRoundId());
        audit.setRegistrationId(result.getRegistrationId());
        audit.setAction(action);
        audit.setBeforeJson(toJson(snapshot(beforeResult)));
        audit.setAfterJson(toJson(snapshot(afterResult)));
        audit.setReason(RequestUtils.truncate(reason, REASON_MAX_LENGTH));
        audit.setOperateTime(LocalDateTime.now());
        fillOperator(audit);
        if (!save(audit)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "成绩留痕写入失败");
        }
    }

    @Override
    public Map<String, Object> snapshot(Object result) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (!(result instanceof Result entity)) {
            return snapshot;
        }
        snapshot.put("id", entity.getId());
        snapshot.put("scheduleId", entity.getScheduleId());
        snapshot.put("heatId", entity.getHeatId());
        snapshot.put("eventId", entity.getEventId());
        snapshot.put("roundId", entity.getRoundId());
        snapshot.put("registrationId", entity.getRegistrationId());
        snapshot.put("athleteId", entity.getAthleteId());
        snapshot.put("unitId", entity.getUnitId());
        snapshot.put("groupId", entity.getGroupId());
        snapshot.put("laneNo", entity.getLaneNo());
        snapshot.put("resultRaw", entity.getResultRaw());
        snapshot.put("resultValue", entity.getResultValue());
        snapshot.put("resultUnit", entity.getResultUnit());
        snapshot.put("resultScore", entity.getResultScore());
        snapshot.put("finalHeight", entity.getFinalHeight());
        snapshot.put("rankNo", entity.getRankNo());
        snapshot.put("rankDisplay", entity.getRankDisplay());
        snapshot.put("status", entity.getStatus());
        snapshot.put("foulType", entity.getFoulType());
        snapshot.put("foulReason", entity.getFoulReason());
        snapshot.put("windSpeed", entity.getWindSpeed());
        snapshot.put("isWindLegal", entity.getIsWindLegal());
        snapshot.put("isRecordCandidate", entity.getIsRecordCandidate());
        snapshot.put("isRecordConfirmed", entity.getIsRecordConfirmed());
        snapshot.put("recordId", entity.getRecordId());
        snapshot.put("recordType", entity.getRecordType());
        snapshot.put("isPb", entity.getIsPb());
        snapshot.put("isSb", entity.getIsSb());
        snapshot.put("auditStatus", entity.getAuditStatus());
        snapshot.put("isLocked", entity.getIsLocked());
        snapshot.put("rejectReason", entity.getRejectReason());
        snapshot.put("remark", entity.getRemark());
        // 含各次试跳：田赛平局与复核的关键依据
        snapshot.put("attempts", snapshotAttempts(entity));
        return snapshot;
    }

    /**
     * 拍所属成绩的各次试跳快照。
     *
     * @param result 成绩实体
     * @return 试跳快照列表（无成绩 ID 时为空列表）
     */
    private List<Map<String, Object>> snapshotAttempts(Result result) {
        List<Map<String, Object>> attempts = new ArrayList<>();
        if (result.getId() == null) {
            return attempts;
        }
        Long meetId = result.getMeetId() == null ? MeetContext.get() : result.getMeetId();
        if (meetId == null) {
            return attempts;
        }
        List<ResultAttempt> rows = resultAttemptMapper.selectByResultId(meetId, result.getId());
        for (ResultAttempt row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("attemptNo", row.getAttemptNo());
            item.put("attemptRound", row.getAttemptRound());
            item.put("attemptRaw", row.getAttemptRaw());
            item.put("attemptValue", row.getAttemptValue());
            item.put("attemptUnit", row.getAttemptUnit());
            item.put("heightValue", row.getHeightValue());
            item.put("isSuccess", row.getIsSuccess());
            item.put("isPass", row.getIsPass());
            item.put("isFoul", row.getIsFoul());
            item.put("foulType", row.getFoulType());
            item.put("isBest", row.getIsBest());
            attempts.add(item);
        }
        return attempts;
    }

    /**
     * 填充操作人、操作时间与来源 IP。
     *
     * @param audit 留痕实体
     */
    private void fillOperator(ResultAudit audit) {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser != null) {
            audit.setOperatorId(loginUser.getId());
            String name = StringUtils.hasText(loginUser.getRealName()) ? loginUser.getRealName() : loginUser.getUsername();
            audit.setOperatorName(RequestUtils.truncate(name, OPERATOR_NAME_MAX_LENGTH));
        }
        HttpServletRequest request = currentRequest();
        if (request != null) {
            audit.setIp(RequestUtils.truncate(RequestUtils.getClientIp(request), 64));
        }
    }

    /**
     * 当前请求对象（非 Web 线程返回 null，不影响留痕写入）。
     *
     * @return 请求对象或 null
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 快照转 JSON 文本；序列化失败时降级为字符串，避免留痕写入失败阻断业务。
     *
     * @param snapshot 快照
     * @return JSON 文本或 null
     */
    private String toJson(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        try {
            return SNAPSHOT_MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            return String.valueOf(snapshot);
        }
    }

    /**
     * 当前届次 ID，缺失时抛参数错误（留痕必须有届次归属）。
     *
     * @return 届次 ID
     */
    private Long currentMeetId() {
        Long meetId = MeetContext.get();
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次上下文缺失，无法写入成绩留痕");
        }
        return meetId;
    }
}
