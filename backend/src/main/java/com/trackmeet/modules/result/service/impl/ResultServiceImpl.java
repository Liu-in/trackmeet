package com.trackmeet.modules.result.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.engine.result.WindValidator;
import com.trackmeet.modules.athlete.entity.Athlete;
import com.trackmeet.modules.athlete.entity.Registration;
import com.trackmeet.modules.athlete.mapper.AthleteMapper;
import com.trackmeet.modules.athlete.mapper.RegistrationMapper;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.EventRound;
import com.trackmeet.modules.event.entity.EventRoundRule;
import com.trackmeet.modules.event.entity.EventRuleConfig;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import com.trackmeet.modules.event.mapper.EventMapper;
import com.trackmeet.modules.event.mapper.EventRoundMapper;
import com.trackmeet.modules.event.mapper.EventRoundRuleMapper;
import com.trackmeet.modules.event.mapper.EventRuleConfigMapper;
import com.trackmeet.modules.event.mapper.MeetRuleConfigMapper;
import com.trackmeet.modules.result.dto.ResultApproveDTO;
import com.trackmeet.modules.result.dto.ResultAttemptDTO;
import com.trackmeet.modules.result.dto.ResultBatchSaveDTO;
import com.trackmeet.modules.result.dto.ResultQuery;
import com.trackmeet.modules.result.dto.ResultRejectDTO;
import com.trackmeet.modules.result.dto.ResultSaveDTO;
import com.trackmeet.modules.result.dto.ResultStatusDTO;
import com.trackmeet.modules.result.dto.ResultWindDTO;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.entity.ResultAudit;
import com.trackmeet.modules.result.entity.ResultAttempt;
import com.trackmeet.modules.result.entity.WindReading;
import com.trackmeet.modules.result.mapper.ResultAttemptMapper;
import com.trackmeet.modules.result.mapper.ResultMapper;
import com.trackmeet.modules.result.mapper.WindReadingMapper;
import com.trackmeet.modules.result.service.RankService;
import com.trackmeet.modules.result.service.ResultAttemptService;
import com.trackmeet.modules.result.service.ResultAuditService;
import com.trackmeet.modules.result.service.ResultService;
import com.trackmeet.modules.result.vo.EntrySheetRowVO;
import com.trackmeet.modules.result.vo.ResultVO;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 成绩服务实现。
 * <p>
 * 核心落点：
 * <ol>
 *     <li><b>成绩双列</b>：{@code result_raw}（显示原文，如 {@code 11"23}、{@code 1:02.45}）
 *         与 {@code result_value}（可比较数值：径赛秒、田赛米、全能分）；
 *         前端只给原文时由 {@link ResultRawParser} 解析补全，反之由数值格式化补全原文；</li>
 *     <li><b>特殊状态</b>：DNS/DNF/DSQ/NM/PASS/SCRATCH 置空 {@code result_value} 与名次，不参与排名；
 *         DSQ 必须给出犯规类型；</li>
 *     <li><b>风速</b>：{@code is_wind_legal = WindValidator.isLegal(windSpeed, maxLegal)}，
 *         上限取 {@code event_rule_config.wind_json} 的 {@code maxLegal}（默认 2.0），
 *         同步写 {@code wind_reading} 留档；超风速清除纪录认定但保留候选位；</li>
 *     <li><b>审核流</b>：DRAFT → SUBMITTED → APPROVED/REJECTED，每次动作写 {@code result_audit}；
 *         APPROVED 后 {@code is_locked = 1}，须先撤销审核才能改；</li>
 *     <li><b>审核通过自动生成名次</b>：按审核通过的（项目 × 轮次）分组调用
 *         {@link RankService#generateRank}，由成绩引擎回写名次。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class ResultServiceImpl extends ServiceImpl<ResultMapper, Result> implements ResultService {

    /** 风速规则 JSON 解析器（线程安全） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 风速规则 JSON 的字段名 */
    private static final String WIND_KEY_MAX_LEGAL = "maxLegal";

    /** 默认风速合法上限（与引擎 WindValidator.DEFAULT_MAX_LEGAL 一致） */
    private static final BigDecimal DEFAULT_MAX_LEGAL = WindValidator.DEFAULT_MAX_LEGAL;

    private final ResultMapper resultMapper;

    private final ResultAttemptMapper resultAttemptMapper;

    private final WindReadingMapper windReadingMapper;

    private final ResultAttemptService resultAttemptService;

    private final ResultAuditService resultAuditService;

    private final RankService rankService;

    private final RegistrationMapper registrationMapper;

    private final AthleteMapper athleteMapper;

    private final EventMapper eventMapper;

    private final EventRoundMapper eventRoundMapper;

    private final EventRoundRuleMapper eventRoundRuleMapper;

    private final EventRuleConfigMapper eventRuleConfigMapper;

    private final MeetRuleConfigMapper meetRuleConfigMapper;

    private final ScheduleMapper scheduleMapper;

    private final HeatMapper heatMapper;

    private final HeatMemberMapper heatMemberMapper;

    @Override
    public PageResult<ResultVO> pageResult(ResultQuery query) {
        ResultQuery condition = query == null ? new ResultQuery() : query;
        Long meetId = resolveMeetId(condition.getMeetId());
        Page<ResultVO> pageParam = new Page<>(condition.pageNo(), condition.pageSize());
        Page<ResultVO> page = resultMapper.selectResultPage(pageParam, meetId,
                condition.getScheduleId(), condition.getEventId(), condition.getRoundId(), condition.getHeatId(),
                condition.getUnitId(), condition.getGroupId(), condition.getAthleteId(),
                condition.getAuditStatus(), condition.getStatus(), condition.getIsLocked(),
                StringUtils.hasText(condition.getKeyword()) ? condition.getKeyword().trim() : null);
        return PageResult.of(page);
    }

    @Override
    public List<EntrySheetRowVO> entrySheet(Long scheduleId, Long eventId, Long roundId) {
        Long meetId = currentMeetId();
        if (scheduleId == null && (eventId == null || roundId == null)) {
            throw new BizException(ResultCode.PARAM_ERROR, "请给出日程条目 ID，或同时给出项目与轮次");
        }
        if (scheduleId != null) {
            Schedule schedule = scheduleMapper.selectById(scheduleId);
            if (schedule == null) {
                throw new BizException(ResultCode.NOT_FOUND, "日程条目不存在");
            }
            checkMeetId(meetId, schedule.getMeetId());
        }
        return resultMapper.selectEntrySheetRows(meetId, scheduleId, eventId, roundId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createResult(ResultSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩信息不能为空");
        }
        Long meetId = currentMeetId();
        if (dto.getScheduleId() == null || dto.getRegistrationId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程条目与报名记录不能为空");
        }
        Schedule schedule = requireSchedule(meetId, dto.getScheduleId());
        Registration registration = requireRegistration(meetId, dto.getRegistrationId(), schedule.getEventId());

        // 幂等：同一（日程 × 报名）唯一，已存在则按修改处理
        Result exists = resultMapper.selectOne(Wrappers.<Result>lambdaQuery()
                .eq(Result::getMeetId, meetId)
                .eq(Result::getScheduleId, dto.getScheduleId())
                .eq(Result::getRegistrationId, dto.getRegistrationId())
                .last("LIMIT 1"));
        if (exists != null) {
            if (exists.locked()) {
                throw new BizException(ResultCode.RESULT_LOCKED, "成绩已审核锁定，禁止直接修改");
            }
            dto.setEventId(schedule.getEventId());
            dto.setRoundId(schedule.getRoundId());
            applyToEntity(dto, exists, false, schedule, registration, meetId);
            if (!updateById(exists)) {
                throw new BizException(ResultCode.CONFLICT, "成绩已被他人修改，请刷新后重试");
            }
            persistAttempts(meetId, exists, dto.getAttempts());
            resultAuditService.recordEntities(null, exists, ResultAudit.ACTION_MODIFY, "重复录入按修改处理");
            return exists.getId();
        }

        Result entity = new Result();
        entity.setId(null);
        applyToEntity(dto, entity, true, schedule, registration, meetId);
        if (!save(entity)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "成绩录入失败");
        }
        persistAttempts(meetId, entity, dto.getAttempts());
        resultAuditService.recordEntities(null, entity, ResultAudit.ACTION_MODIFY, "成绩录入");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchSaveResult(ResultBatchSaveDTO dto) {
        if (dto == null || dto.getScheduleId() == null || dto.getResults() == null || dto.getResults().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程条目与成绩明细不能为空");
        }
        Long meetId = currentMeetId();
        Schedule schedule = requireSchedule(meetId, dto.getScheduleId());
        List<Long> ids = new ArrayList<>(dto.getResults().size());
        for (ResultSaveDTO item : dto.getResults()) {
            if (item == null) {
                continue;
            }
            // 一组一次提交：日程条目以批量入参为准
            item.setScheduleId(dto.getScheduleId());
            if (item.getEventId() == null) {
                item.setEventId(schedule.getEventId());
            }
            if (item.getRoundId() == null) {
                item.setRoundId(schedule.getRoundId());
            }
            ids.add(createResult(item));
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateResult(Long id, ResultSaveDTO dto) {
        if (id == null || dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 与成绩信息不能为空");
        }
        Long meetId = currentMeetId();
        Result exists = requireResult(meetId, id);
        // 已审核锁定：必须先走撤销审核
        if (exists.locked()) {
            throw new BizException(ResultCode.RESULT_LOCKED, "成绩已审核锁定，禁止直接修改");
        }
        Schedule schedule = requireSchedule(meetId,
                dto.getScheduleId() == null ? exists.getScheduleId() : dto.getScheduleId());
        Registration registration = requireRegistration(meetId,
                dto.getRegistrationId() == null ? exists.getRegistrationId() : dto.getRegistrationId(),
                schedule.getEventId());
        Result before = copy(resultMapper.selectById(id));

        dto.setEventId(schedule.getEventId());
        dto.setRoundId(schedule.getRoundId());
        applyToEntity(dto, exists, false, schedule, registration, meetId);
        if (!updateById(exists)) {
            throw new BizException(ResultCode.CONFLICT, "成绩已被他人修改，请刷新后重试");
        }
        persistAttempts(meetId, exists, dto.getAttempts());
        Result after = resultMapper.selectById(id);
        resultAuditService.recordEntities(before, after, ResultAudit.ACTION_MODIFY,
                StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : "修改成绩");
        // 成绩变化后名次可能失效，先清空由重新审核时再生成
        rankService.clearRank(exists.getEventId(), exists.getRoundId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAttempt(ResultAttemptDTO dto) {
        if (dto == null || dto.getResultId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属成绩不能为空");
        }
        Long meetId = currentMeetId();
        Result result = requireResult(meetId, dto.getResultId());
        checkModifiable(result);
        Long attemptId = resultAttemptService.saveAttempt(dto);
        Result after = resultMapper.selectById(result.getId());
        resultAuditService.recordEntities(result, after, ResultAudit.ACTION_MODIFY,
                "田赛试跳/试掷录入：" + dto.getAttemptNo());
        return attemptId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateAttempt(Long id, ResultAttemptDTO dto) {
        if (id == null || dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "试跳记录 ID 与试跳信息不能为空");
        }
        Long meetId = currentMeetId();
        ResultAttempt attempt = resultAttemptMapper.selectById(id);
        if (attempt == null) {
            throw new BizException(ResultCode.NOT_FOUND, "试跳成绩不存在");
        }
        Result result = requireResult(meetId, attempt.getResultId());
        checkModifiable(result);
        resultAttemptService.updateAttempt(id, dto);
        Result after = resultMapper.selectById(result.getId());
        resultAuditService.recordEntities(result, after, ResultAudit.ACTION_MODIFY,
                "田赛试跳/试掷修改：" + attempt.getAttemptNo());
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStatus(ResultStatusDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        String status = dto.getStatus();
        if (!StringUtils.hasText(status)) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩状态不能为空");
        }
        // DSQ 必须填写犯规类型
        if (Result.STATUS_DSQ.equals(status) && !StringUtils.hasText(dto.getFoulType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "取消资格（DSQ）必须填写犯规类型");
        }
        Long meetId = currentMeetId();
        List<Long> ids = distinctIds(dto.getIds());
        List<Result> before = new ArrayList<>();
        for (Long id : ids) {
            Result result = requireResult(meetId, id);
            checkModifiable(result);
            before.add(copy(result));
        }
        resultMapper.updateStatusBatch(meetId, ids, status,
                Result.STATUS_DSQ.equals(status) ? dto.getFoulType() : null,
                StringUtils.hasText(dto.getFoulReason()) ? dto.getFoulReason() : null);
        for (Long id : ids) {
            Result after = resultMapper.selectById(id);
            resultAuditService.recordEntities(findBefore(before, id), after, ResultAudit.ACTION_MODIFY,
                    StringUtils.hasText(dto.getReason()) ? dto.getReason() : ("标记成绩状态：" + status));
        }
        return ids.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateWind(ResultWindDTO dto) {
        if (dto == null || dto.getWindSpeed() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "风速读数不能为空");
        }
        Long meetId = currentMeetId();
        List<Long> ids = new ArrayList<>();
        if (dto.getResultIds() != null && !dto.getResultIds().isEmpty()) {
            ids.addAll(distinctIds(dto.getResultIds()));
        } else if (dto.getHeatId() != null) {
            List<Result> rows = resultMapper.selectList(Wrappers.<Result>lambdaQuery()
                    .eq(Result::getMeetId, meetId)
                    .eq(Result::getHeatId, dto.getHeatId())
                    .orderByAsc(Result::getId));
            for (Result row : rows) {
                ids.add(row.getId());
            }
        } else {
            throw new BizException(ResultCode.PARAM_ERROR, "请给出成绩 ID 列表或分组 ID");
        }
        if (ids.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到可录入风速的成绩");
        }

        LocalDateTime readTime = LocalDateTime.now();
        Long recorderId = SecurityUtils.getUserId();
        int affected = 0;
        for (Long id : ids) {
            Result result = requireResult(meetId, id);
            // 风速仅对适用项目判定上限，不适用项目沿用默认上限
            BigDecimal maxLegal = resolveMaxLegal(result.getEventId());
            boolean applicable = isWindApplicable(result.getEventId());
            boolean legal = !applicable || WindValidator.isLegal(dto.getWindSpeed(), maxLegal);
            Result before = copy(result);
            resultMapper.updateWindBatch(meetId, List.of(id), dto.getWindSpeed(), legal ? 1 : 0);
            upsertWindReading(meetId, result, dto, legal, maxLegal, recorderId, readTime);
            Result after = resultMapper.selectById(id);
            String reason = legal ? "风速录入：" + dto.getWindSpeed() + " m/s"
                    : WindValidator.illegalMessage(dto.getWindSpeed(), maxLegal);
            resultAuditService.recordEntities(before, after, ResultAudit.ACTION_MODIFY, reason);
            affected++;
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submit(List<Long> ids) {
        Long meetId = currentMeetId();
        List<Long> targets = distinctIds(ids);
        if (targets.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        List<Result> before = new ArrayList<>();
        for (Long id : targets) {
            Result result = requireResult(meetId, id);
            if (Result.AUDIT_APPROVED.equals(result.getAuditStatus())) {
                throw new BizException(ResultCode.CONFLICT, "已审核通过的成绩无需重复提交，请先撤销审核");
            }
            before.add(copy(result));
        }
        resultMapper.updateAuditBatch(meetId, targets, Result.AUDIT_SUBMITTED, Result.UNLOCKED,
                SecurityUtils.getUserId(), LocalDateTime.now(), null);
        for (Long id : targets) {
            Result after = resultMapper.selectById(id);
            resultAuditService.recordEntities(findBefore(before, id), after, ResultAudit.ACTION_SUBMIT, "提交审核");
        }
        return targets.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(ResultApproveDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        Long meetId = currentMeetId();
        Long auditorId = SecurityUtils.getUserId();
        LocalDateTime auditTime = LocalDateTime.now();
        List<Long> targets = distinctIds(dto.getIds());
        List<Result> before = new ArrayList<>();
        Set<String> combos = new LinkedHashSet<>();
        for (Long id : targets) {
            Result result = requireResult(meetId, id);
            before.add(copy(result));
            if (result.getEventId() != null && result.getRoundId() != null) {
                combos.add(result.getEventId() + ":" + result.getRoundId());
            }
        }
        resultMapper.updateAuditBatch(meetId, targets, Result.AUDIT_APPROVED, Result.LOCKED,
                auditorId, auditTime, null);
        for (Long id : targets) {
            Result after = resultMapper.selectById(id);
            resultAuditService.recordEntities(findBefore(before, id), after, ResultAudit.ACTION_APPROVE,
                    StringUtils.hasText(dto.getReason()) ? dto.getReason() : "审核通过");
        }
        // 审核通过后自动触发生成名次（按项目 × 轮次逐个生成）
        for (String combo : combos) {
            String[] parts = combo.split(":");
            Long eventId = Long.valueOf(parts[0]);
            Long roundId = Long.valueOf(parts[1]);
            rankService.generateRank(eventId, roundId, null, null);
        }
        return targets.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(ResultRejectDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getRejectReason())) {
            throw new BizException(ResultCode.PARAM_ERROR, "驳回原因不能为空");
        }
        Long meetId = currentMeetId();
        List<Long> targets = distinctIds(dto.getIds());
        List<Result> before = new ArrayList<>();
        Set<String> combos = new LinkedHashSet<>();
        for (Long id : targets) {
            Result result = requireResult(meetId, id);
            before.add(copy(result));
            if (result.getEventId() != null && result.getRoundId() != null) {
                combos.add(result.getEventId() + ":" + result.getRoundId());
            }
        }
        resultMapper.updateAuditBatch(meetId, targets, Result.AUDIT_REJECTED, Result.UNLOCKED,
                SecurityUtils.getUserId(), LocalDateTime.now(), dto.getRejectReason());
        for (Long id : targets) {
            Result after = resultMapper.selectById(id);
            resultAuditService.recordEntities(findBefore(before, id), after, ResultAudit.ACTION_REJECT,
                    dto.getRejectReason());
        }
        // 驳回后原名次失效
        clearRankByCombos(combos);
        return targets.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeApproval(Long id, String reason) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        Long meetId = currentMeetId();
        Result result = requireResult(meetId, id);
        if (!Result.AUDIT_APPROVED.equals(result.getAuditStatus()) && !result.locked()) {
            throw new BizException(ResultCode.CONFLICT, "该成绩未审核通过，无需撤销审核");
        }
        Result before = copy(result);
        resultMapper.updateAuditBatch(meetId, List.of(id), Result.AUDIT_SUBMITTED, Result.UNLOCKED,
                SecurityUtils.getUserId(), LocalDateTime.now(), null);
        Result after = resultMapper.selectById(id);
        resultAuditService.recordEntities(before, after, ResultAudit.ACTION_REVOKE,
                StringUtils.hasText(reason) ? reason : "撤销审核");
        rankService.clearRank(result.getEventId(), result.getRoundId());
    }

    @Override
    public List<com.trackmeet.modules.result.vo.ResultAuditVO> listAudits(Long resultId) {
        if (resultId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        // 先校验成绩归属，避免跨届次读取留痕
        requireResult(currentMeetId(), resultId);
        return resultAuditService.listByResult(resultId);
    }

    @Override
    public int importResults(Long scheduleId, boolean overwrite) {
        if (scheduleId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "目标日程条目不能为空");
        }
        // 接口占位：导入批次留档（result_import_log）与设备文件解析映射尚未开放，
        // 显式抛业务异常避免调用方误判为「导入成功 0 条」。
        throw new BizException(ResultCode.IMPORT_FORMAT_ERROR,
                "设备成绩文件导入尚未开放（接口已占位）：请使用成绩录入或批量录入接口");
    }

    /* ==================================================================== */
    /*                            内部实现                                   */
    /* ==================================================================== */

    /**
     * 把入参写入成绩实体（新增与修改共用），并完成状态、双列、风速的规范化。
     *
     * @param dto          成绩入参
     * @param entity       目标实体
     * @param create       是否新增
     * @param schedule     日程条目
     * @param registration 报名记录
     * @param meetId       届次 ID
     */
    private void applyToEntity(ResultSaveDTO dto, Result entity, boolean create,
                               Schedule schedule, Registration registration, Long meetId) {
        Event event = requireEvent(meetId, schedule.getEventId());
        EventRound round = schedule.getRoundId() == null ? null : eventRoundMapper.selectById(schedule.getRoundId());
        if (round != null && !Objects.equals(event.getId(), round.getEventId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程条目的轮次与项目不匹配");
        }

        String status = StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : Result.STATUS_VALID;
        // 特殊状态校验：DSQ 必须给出犯规类型
        if (Result.STATUS_DSQ.equals(status) && !StringUtils.hasText(dto.getFoulType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "取消资格（DSQ）必须填写犯规类型");
        }

        entity.setMeetId(meetId);
        entity.setScheduleId(schedule.getId());
        entity.setEventId(event.getId());
        entity.setRoundId(schedule.getRoundId());
        entity.setRegistrationId(registration.getId());
        entity.setAthleteId(registration.getAthleteId());
        entity.setUnitId(registration.getUnitId());
        entity.setGroupId(registration.getGroupId());
        entity.setHeatId(dto.getHeatId() != null ? dto.getHeatId() : resolveHeatId(meetId, schedule.getId(), registration.getId()));
        entity.setHeatMemberId(dto.getHeatMemberId() != null ? dto.getHeatMemberId()
                : resolveHeatMemberId(meetId, schedule.getId(), registration.getId()));
        entity.setLaneNo(dto.getLaneNo() != null ? dto.getLaneNo()
                : resolveLaneNo(meetId, schedule.getId(), registration.getId()));
        // 报名/运动员快照（接力成绩记在该报名行上，运动员为队首人）
        if (registration.getAthleteId() != null) {
            Athlete athlete = athleteMapper.selectById(registration.getAthleteId());
            if (athlete != null) {
                entity.setGroupId(entity.getGroupId() == null ? athlete.getGroupId() : entity.getGroupId());
                entity.setUnitId(entity.getUnitId() == null ? athlete.getUnitId() : entity.getUnitId());
            }
        }

        // 成绩双列：数值缺失时按原文解析
        String raw = dto.getResultRaw();
        BigDecimal value = dto.getResultValue();
        if (value == null && StringUtils.hasText(raw)) {
            value = ResultRawParser.parse(raw);
            if (value == null && Result.STATUS_VALID.equals(status)
                    && !ResultRawParser.isInvalidStatusText(raw)) {
                throw new BizException(ResultCode.PARAM_ERROR, "成绩原文无法解析为成绩数值：" + raw);
            }
        }
        if (!StringUtils.hasText(raw) && value != null) {
            raw = value.stripTrailingZeros().toPlainString();
        }
        entity.setStatus(status);
        entity.setResultRaw(raw);
        entity.setResultUnit(StringUtils.hasText(dto.getResultUnit()) ? dto.getResultUnit()
                : (StringUtils.hasText(event.getUnitOfMeasure()) ? event.getUnitOfMeasure() : defaultUnit(event)));
        entity.setResultScore(dto.getResultScore());
        entity.setFinalHeight(dto.getFinalHeight());
        entity.setFoulType(StringUtils.hasText(dto.getFoulType()) ? dto.getFoulType() : null);
        entity.setFoulReason(StringUtils.hasText(dto.getFoulReason()) ? dto.getFoulReason() : null);
        entity.setIsRecordCandidate(dto.getIsRecordCandidate() == null ? Result.NO : dto.getIsRecordCandidate());
        entity.setIsPb(dto.getIsPb());
        entity.setIsSb(dto.getIsSb());
        entity.setAllroundGroupId(dto.getAllroundGroupId());
        entity.setClientToken(dto.getClientToken());
        entity.setRemark(dto.getRemark());
        if (create) {
            entity.setIsManual(Result.YES);
            entity.setIsRecordConfirmed(Result.NO);
            entity.setRankNo(null);
            entity.setRankDisplay(null);
            entity.setAuditStatus(Result.AUDIT_DRAFT);
            entity.setIsLocked(Result.UNLOCKED);
        }

        // 无效状态：成绩数值与名次一并置空
        if (Result.isInvalidStatus(status)) {
            entity.setResultValue(null);
            entity.setRankNo(null);
            entity.setRankDisplay(null);
        } else {
            entity.setResultValue(value);
        }

        // 风速：判定合法性并回填；超风速清除纪录认定
        if (dto.getWindSpeed() != null) {
            BigDecimal maxLegal = resolveMaxLegal(event.getId());
            boolean applicable = isWindApplicable(event.getId());
            boolean legal = !applicable || WindValidator.isLegal(dto.getWindSpeed(), maxLegal);
            entity.setWindSpeed(dto.getWindSpeed());
            entity.setIsWindLegal(legal ? Result.YES : Result.NO);
            if (!legal) {
                entity.setIsRecordConfirmed(Result.NO);
                entity.setRecordId(null);
            }
        } else if (create) {
            // 未测风速：列默认 0；不适用风速的项目同样视为合法（无超风速风险）
            entity.setWindSpeed(null);
            entity.setIsWindLegal(isWindApplicable(event.getId()) ? Result.NO : Result.YES);
        }
        // 修改且未提交风速时不触碰 wind_speed / is_wind_legal（MyBatis-Plus 忽略 null 字段）
    }

    /**
     * 保存试跳明细并在有成功成绩时把最好成绩回写为成绩数值（田赛）。
     *
     * @param meetId    届次 ID
     * @param result    成绩实体
     * @param attempts  试跳入参（可空）
     */
    private void persistAttempts(Long meetId, Result result, List<ResultAttemptDTO> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return;
        }
        for (ResultAttemptDTO attempt : attempts) {
            if (attempt != null) {
                attempt.setResultId(result.getId());
            }
        }
        resultAttemptService.replaceAttempts(result.getId(), attempts);
        Result refreshed = resultMapper.selectById(result.getId());
        if (refreshed != null) {
            result.setResultValue(refreshed.getResultValue());
            result.setRankNo(refreshed.getRankNo());
        }
    }

    /**
     * 写入或覆盖风速读数（表上 {@code uk_result} 要求一成绩一条）。
     *
     * @param meetId     届次 ID
     * @param result     成绩实体
     * @param dto        风速入参
     * @param legal      是否合法
     * @param maxLegal   采用的合法上限
     * @param recorderId 记录人
     * @param readTime   读数时间
     */
    private void upsertWindReading(Long meetId, Result result, ResultWindDTO dto, boolean legal,
                                   BigDecimal maxLegal, Long recorderId, LocalDateTime readTime) {
        WindReading exists = windReadingMapper.selectByResultId(meetId, result.getId());
        WindReading reading = exists == null ? new WindReading() : exists;
        reading.setMeetId(meetId);
        reading.setResultId(result.getId());
        reading.setScheduleId(result.getScheduleId());
        reading.setHeatId(result.getHeatId());
        reading.setEventId(result.getEventId());
        reading.setWindSpeed(dto.getWindSpeed());
        reading.setWindDirection(StringUtils.hasText(dto.getWindDirection())
                ? dto.getWindDirection().toUpperCase() : resolveWindDirection(dto.getWindSpeed()));
        reading.setIsWindLegal(legal ? WindReading.YES : WindReading.NO);
        reading.setLegalLimit(maxLegal);
        reading.setWindDeviceNo(dto.getWindDeviceNo());
        reading.setReadTime(readTime);
        reading.setRecorderId(recorderId);
        reading.setRemark(dto.getRemark());
        if (exists == null) {
            windReadingMapper.insert(reading);
        } else {
            windReadingMapper.updateById(reading);
        }
    }

    /**
     * 采用的风速合法上限：{@code event_rule_config.wind_json.maxLegal} →
     * {@code meet_rule_config.wind_json.maxLegal} → 引擎默认 {@code 2.0 m/s}（规则 R-13）。
     *
     * @param eventId 项目 ID
     * @return 合法上限（m/s）
     */
    private BigDecimal resolveMaxLegal(Long eventId) {
        Long meetId = MeetContext.get();
        if (eventId != null && meetId != null) {
            EventRuleConfig eventRule = eventRuleConfigMapper.selectOne(Wrappers.<EventRuleConfig>lambdaQuery()
                    .eq(EventRuleConfig::getMeetId, meetId)
                    .eq(EventRuleConfig::getEventId, eventId)
                    .last("LIMIT 1"));
            BigDecimal value = extractMaxLegal(eventRule == null ? null : eventRule.getWindJson());
            if (value != null) {
                return value;
            }
        }
        if (meetId != null) {
            MeetRuleConfig meetRule = meetRuleConfigMapper.selectOne(Wrappers.<MeetRuleConfig>lambdaQuery()
                    .eq(MeetRuleConfig::getMeetId, meetId)
                    .last("LIMIT 1"));
            BigDecimal value = extractMaxLegal(meetRule == null ? null : meetRule.getWindJson());
            if (value != null) {
                return value;
            }
        }
        return DEFAULT_MAX_LEGAL;
    }

    /**
     * 从风速规则 JSON 中取出 {@code maxLegal}。
     *
     * @param windJson 风速规则 JSON，形如 {@code {"applicable":true,"maxLegal":2.0}}
     * @return 上限值，缺失或非法时返回 null（由调用方回退默认值）
     */
    private BigDecimal extractMaxLegal(String windJson) {
        if (!StringUtils.hasText(windJson)) {
            return null;
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(windJson);
            JsonNode maxLegal = node.path(WIND_KEY_MAX_LEGAL);
            if (maxLegal.isNumber()) {
                return maxLegal.decimalValue();
            }
            if (maxLegal.isTextual()) {
                return new BigDecimal(maxLegal.asText());
            }
        } catch (Exception e) {
            // 规则 JSON 脏数据不阻断成绩录入，回退默认上限
            return null;
        }
        return null;
    }

    /**
     * 项目是否需要计风速（项目类型 + 子类型判定，复用引擎 {@code WindValidator.isApplicable}）。
     *
     * @param eventId 项目 ID
     * @return 需要计风速返回 true
     */
    private boolean isWindApplicable(Long eventId) {
        if (eventId == null) {
            return false;
        }
        Long meetId = MeetContext.get();
        Event event = meetId == null ? eventMapper.selectById(eventId)
                : eventMapper.selectOne(Wrappers.<Event>lambdaQuery()
                        .eq(Event::getMeetId, meetId)
                        .eq(Event::getId, eventId)
                        .last("LIMIT 1"));
        if (event == null) {
            return false;
        }
        if (Integer.valueOf(1).equals(event.getWindApplicable())) {
            return true;
        }
        return WindValidator.isApplicable(event.getEventType(), event.getSubType());
    }

    /**
     * 按风速正负推断风向（顺风为正、逆风为负）。
     *
     * @param windSpeed 风速
     * @return 风向：TAIL / HEAD
     */
    private String resolveWindDirection(BigDecimal windSpeed) {
        if (windSpeed == null || windSpeed.compareTo(BigDecimal.ZERO) >= 0) {
            return WindReading.DIRECTION_TAIL;
        }
        return WindReading.DIRECTION_HEAD;
    }

    /**
     * 默认成绩单位（按项目类型兜底）。
     *
     * @param event 项目
     * @return 单位：s / m / pt
     */
    private String defaultUnit(Event event) {
        String type = event.getEventType();
        if (Event.TYPE_TRACK.equals(type) || Event.TYPE_RELAY.equals(type)) {
            return "s";
        }
        if (Event.TYPE_ALLROUND.equals(type)) {
            return "pt";
        }
        return "m";
    }

    /**
     * 取组内人员 ID（无编排记录时返回空）。
     *
     * @param meetId       届次 ID
     * @param scheduleId   日程条目 ID
     * @param registrationId 报名 ID
     * @return 组内人员 ID 或 null
     */
    private Long resolveHeatMemberId(Long meetId, Long scheduleId, Long registrationId) {
        HeatMember member = findHeatMember(meetId, scheduleId, registrationId);
        return member == null ? null : member.getId();
    }

    /**
     * 取分组 ID（无编排记录时返回空）。
     *
     * @param meetId         届次 ID
     * @param scheduleId     日程条目 ID
     * @param registrationId 报名 ID
     * @return 分组 ID 或 null
     */
    private Long resolveHeatId(Long meetId, Long scheduleId, Long registrationId) {
        HeatMember member = findHeatMember(meetId, scheduleId, registrationId);
        return member == null ? null : member.getHeatId();
    }

    /**
     * 取道次号（无编排记录时返回空）。
     *
     * @param meetId         届次 ID
     * @param scheduleId     日程条目 ID
     * @param registrationId 报名 ID
     * @return 道次号或 null
     */
    private Integer resolveLaneNo(Long meetId, Long scheduleId, Long registrationId) {
        HeatMember member = findHeatMember(meetId, scheduleId, registrationId);
        return member == null ? null : member.getLaneNo();
    }

    /**
     * 按日程条目与报名记录查编排结果（组内人员）。
     *
     * @param meetId         届次 ID
     * @param scheduleId     日程条目 ID
     * @param registrationId 报名 ID
     * @return 组内人员，未编排时返回 null
     */
    private HeatMember findHeatMember(Long meetId, Long scheduleId, Long registrationId) {
        if (meetId == null || scheduleId == null || registrationId == null) {
            return null;
        }
        return heatMemberMapper.selectOne(Wrappers.<HeatMember>lambdaQuery()
                .eq(HeatMember::getMeetId, meetId)
                .eq(HeatMember::getScheduleId, scheduleId)
                .eq(HeatMember::getRegistrationId, registrationId)
                .last("LIMIT 1"));
    }

    /**
     * 校验日程条目存在且属于当前届次；组次由 {@code heat} 冗余回填。
     *
     * @param meetId     届次 ID
     * @param scheduleId 日程条目 ID
     * @return 日程条目
     */
    private Schedule requireSchedule(Long meetId, Long scheduleId) {
        if (scheduleId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程条目不能为空");
        }
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException(ResultCode.NOT_FOUND, "日程条目不存在");
        }
        checkMeetId(meetId, schedule.getMeetId());
        return schedule;
    }

    /**
     * 校验报名记录存在、属于当前届次且项目一致。
     *
     * @param meetId         届次 ID
     * @param registrationId 报名 ID
     * @param eventId        项目 ID
     * @return 报名记录
     */
    private Registration requireRegistration(Long meetId, Long registrationId, Long eventId) {
        if (registrationId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名记录不能为空");
        }
        Registration registration = registrationMapper.selectById(registrationId);
        if (registration == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        checkMeetId(meetId, registration.getMeetId());
        if (eventId != null && registration.getEventId() != null
                && !Objects.equals(eventId, registration.getEventId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名记录不属于该日程条目的项目");
        }
        return registration;
    }

    /**
     * 校验项目存在且属于当前届次。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 项目
     */
    private Event requireEvent(Long meetId, Long eventId) {
        if (eventId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目不能为空");
        }
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        checkMeetId(meetId, event.getMeetId());
        return event;
    }

    /**
     * 取成绩实体并校验届次。
     *
     * @param meetId 届次 ID
     * @param id     成绩 ID
     * @return 成绩实体
     */
    private Result requireResult(Long meetId, Long id) {
        Result result = resultMapper.selectById(id);
        if (result == null) {
            throw new BizException(ResultCode.NOT_FOUND, "成绩不存在");
        }
        checkMeetId(meetId, result.getMeetId());
        return result;
    }

    /**
     * 已审核锁定禁止改值。
     *
     * @param result 成绩实体
     */
    private void checkModifiable(Result result) {
        if (result.locked()) {
            throw new BizException(ResultCode.RESULT_LOCKED, "成绩已审核锁定，禁止直接修改");
        }
    }

    /**
     * 需求 ID 去重并保持顺序。
     *
     * @param ids 原始 ID 列表
     * @return 去重后的 ID 列表
     */
    private List<Long> distinctIds(List<Long> ids) {
        List<Long> targets = new ArrayList<>();
        if (ids == null) {
            return targets;
        }
        for (Long id : ids) {
            if (id != null && !targets.contains(id)) {
                targets.add(id);
            }
        }
        return targets;
    }

    /**
     * 从留痕快照列表中取出指定成绩的变更前实体。
     *
     * @param before 变更前实体列表
     * @param id     成绩 ID
     * @return 变更前实体，未找到返回 null
     */
    private Result findBefore(List<Result> before, Long id) {
        for (Result item : before) {
            if (item != null && Objects.equals(item.getId(), id)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按「项目:轮次」组合清理名次。
     *
     * @param combos 组合集合
     */
    private void clearRankByCombos(Set<String> combos) {
        for (String combo : combos) {
            String[] parts = combo.split(":");
            rankService.clearRank(Long.valueOf(parts[0]), Long.valueOf(parts[1]));
        }
    }

    /**
     * 浅拷贝成绩实体（用于留痕变更前快照）。
     *
     * @param result 成绩实体
     * @return 拷贝对象，入参为空时返回 null
     */
    private Result copy(Result result) {
        if (result == null) {
            return null;
        }
        Result copy = new Result();
        copy.setId(result.getId());
        copy.setMeetId(result.getMeetId());
        copy.setScheduleId(result.getScheduleId());
        copy.setHeatId(result.getHeatId());
        copy.setHeatMemberId(result.getHeatMemberId());
        copy.setEventId(result.getEventId());
        copy.setRoundId(result.getRoundId());
        copy.setRegistrationId(result.getRegistrationId());
        copy.setAthleteId(result.getAthleteId());
        copy.setUnitId(result.getUnitId());
        copy.setGroupId(result.getGroupId());
        copy.setLaneNo(result.getLaneNo());
        copy.setResultRaw(result.getResultRaw());
        copy.setResultValue(result.getResultValue());
        copy.setResultUnit(result.getResultUnit());
        copy.setResultScore(result.getResultScore());
        copy.setFinalHeight(result.getFinalHeight());
        copy.setRankNo(result.getRankNo());
        copy.setRankDisplay(result.getRankDisplay());
        copy.setStatus(result.getStatus());
        copy.setFoulType(result.getFoulType());
        copy.setFoulReason(result.getFoulReason());
        copy.setWindSpeed(result.getWindSpeed());
        copy.setIsWindLegal(result.getIsWindLegal());
        copy.setIsRecordCandidate(result.getIsRecordCandidate());
        copy.setIsRecordConfirmed(result.getIsRecordConfirmed());
        copy.setRecordId(result.getRecordId());
        copy.setRecordType(result.getRecordType());
        copy.setIsPb(result.getIsPb());
        copy.setIsSb(result.getIsSb());
        copy.setIsManual(result.getIsManual());
        copy.setAllroundGroupId(result.getAllroundGroupId());
        copy.setAuditStatus(result.getAuditStatus());
        copy.setAuditBy(result.getAuditBy());
        copy.setAuditTime(result.getAuditTime());
        copy.setRejectReason(result.getRejectReason());
        copy.setIsLocked(result.getIsLocked());
        copy.setRemark(result.getRemark());
        return copy;
    }

    /**
     * 解析届次：入参优先，其次上下文；与上下文不一致按越权处理。
     *
     * @param meetId 入参届次 ID
     * @return 届次 ID
     */
    private Long resolveMeetId(Long meetId) {
        Long current = MeetContext.get();
        Long resolved = meetId == null ? current : meetId;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        if (current != null && !Objects.equals(current, resolved)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        return resolved;
    }

    /**
     * 当前届次 ID。
     *
     * @return 届次 ID
     */
    private Long currentMeetId() {
        Long meetId = MeetContext.get();
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次上下文缺失");
        }
        return meetId;
    }

    /**
     * 届次一致性校验。
     *
     * @param currentMeetId 当前届次 ID
     * @param meetId        数据届次 ID
     */
    private void checkMeetId(Long currentMeetId, Long meetId) {
        if (!Objects.equals(currentMeetId, meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
    }
}
