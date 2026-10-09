package com.trackmeet.modules.result.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.engine.advance.AdvanceCalculator;
import com.trackmeet.engine.model.AdvanceRule;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.EventRound;
import com.trackmeet.modules.event.entity.EventRoundRule;
import com.trackmeet.modules.event.mapper.EventMapper;
import com.trackmeet.modules.event.mapper.EventRoundMapper;
import com.trackmeet.modules.event.mapper.EventRoundRuleMapper;
import com.trackmeet.modules.result.dto.AdvanceGenerateDTO;
import com.trackmeet.modules.result.dto.AdvanceUpdateDTO;
import com.trackmeet.modules.result.entity.AdvanceResult;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.mapper.AdvanceResultMapper;
import com.trackmeet.modules.result.mapper.ResultAttemptMapper;
import com.trackmeet.modules.result.mapper.ResultMapper;
import com.trackmeet.modules.result.service.AdvanceService;
import com.trackmeet.modules.result.vo.AdvanceRowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 晋级名单服务实现。
 * <p>
 * 调用方式（不自建算法副本）：装配 {@code ResultRecord}（必须填 {@code heatNo} 与 {@code rankNo}，
 * 前者用于「每组前 N 名」分组，后者为轮次内全局名次）→
 * {@code AdvanceCalculator.calculate(records, advanceRule)} → 结果落 {@code advance_result}。
 * <p>
 * 幂等策略：写入前按「项目 × 目标轮次」<b>物理删除</b>旧名单，再按引擎给出的顺序插入，
 * 每条 {@code advance_type} 取 {@code GROUP_TOP} / {@code BEST_LOSER}，{@code advance_order} 从 1 起，
 * {@code is_confirmed = 0} 等待人工确认（重复生成不产生重复名单，也不丢人工调整前的可见性）。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class AdvanceServiceImpl extends ServiceImpl<AdvanceResultMapper, AdvanceResult> implements AdvanceService {

    private final AdvanceResultMapper advanceResultMapper;

    private final ResultMapper resultMapper;

    private final ResultAttemptMapper resultAttemptMapper;

    private final EventMapper eventMapper;

    private final EventRoundMapper eventRoundMapper;

    private final EventRoundRuleMapper eventRoundRuleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdvanceRowVO> generateAdvance(AdvanceGenerateDTO dto) {
        if (dto == null || dto.getEventId() == null || dto.getFromRoundId() == null || dto.getToRoundId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目、来源轮次与目标轮次不能为空");
        }
        if (Objects.equals(dto.getFromRoundId(), dto.getToRoundId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "来源轮次与目标轮次不能相同");
        }
        Long meetId = currentMeetId();
        Event event = requireEvent(meetId, dto.getEventId());
        EventRound fromRound = requireRound(meetId, dto.getFromRoundId(), event.getId());
        requireRound(meetId, dto.getToRoundId(), event.getId());

        // ① 规则：显式入参优先，缺省取轮次级 event_round_rule
        EventRoundRule roundRule = eventRoundRuleMapper.selectOne(
                Wrappers.<EventRoundRule>lambdaQuery()
                        .eq(EventRoundRule::getMeetId, meetId)
                        .eq(EventRoundRule::getRoundId, dto.getFromRoundId())
                        .last("LIMIT 1"));
        AdvanceRule rule = buildRule(dto, fromRound, roundRule);

        // ② 装配引擎输入：来源轮次成绩（含名次、组次、风速合法性、田赛试跳）
        List<Result> results = resultMapper.selectByRound(meetId, event.getId(), dto.getFromRoundId());
        if (results.isEmpty()) {
            throw new BizException(ResultCode.ADVANCE_RULE_MISMATCH, "来源轮次暂无成绩，无法生成晋级名单");
        }
        Map<Long, List<BigDecimal>> attemptsByResult = loadAttempts(meetId, results);
        List<ResultRecord> records = new ArrayList<>(results.size());
        Map<Long, Result> resultByRegistration = new LinkedHashMap<>();
        for (Result result : results) {
            ResultRecord record = toRecord(result, event, attemptsByResult.get(result.getId()));
            records.add(record);
            resultByRegistration.put(result.getRegistrationId(), result);
        }

        // ③ 调用成绩引擎计算晋级名单
        List<com.trackmeet.engine.model.AdvanceResult> calculated = AdvanceCalculator.calculate(records, rule);

        // ④ 幂等重建：先按「项目 × 目标轮次」物理删除，再插入
        advanceResultMapper.deleteByTargetRoundPhysically(meetId, event.getId(), dto.getToRoundId());
        if (!calculated.isEmpty()) {
            List<AdvanceResult> entities = new ArrayList<>(calculated.size());
            int order = 1;
            for (com.trackmeet.engine.model.AdvanceResult item : calculated) {
                Result source = resultByRegistration.get(item.getRegistrationId());
                AdvanceResult entity = new AdvanceResult();
                entity.setMeetId(meetId);
                entity.setEventId(event.getId());
                entity.setFromRoundId(dto.getFromRoundId());
                entity.setToRoundId(dto.getToRoundId());
                entity.setRegistrationId(item.getRegistrationId());
                entity.setAthleteId(item.getAthleteId());
                entity.setUnitId(item.getUnitId());
                entity.setGroupId(source == null ? null : source.getGroupId());
                entity.setSourceHeatNo(item.getSourceHeatNo());
                entity.setSourceRank(item.getSourceRank());
                entity.setSourceResultValue(item.getSourceResultValue());
                entity.setSourceResultRaw(source == null ? null : source.getResultRaw());
                entity.setAdvanceType(item.getAdvanceType());
                // 顺序号由引擎给出；为空时按插入顺序兜底
                entity.setAdvanceOrder(item.getAdvanceOrder() > 0 ? item.getAdvanceOrder() : order);
                entity.setIsConfirmed(AdvanceResult.UNCONFIRMED);
                entity.setRemark(dto.getRemark());
                entities.add(entity);
                order++;
            }
            if (!saveBatch(entities)) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "晋级名单写入失败");
            }
        }
        return advanceResultMapper.selectAdvanceVOs(meetId, event.getId(), dto.getToRoundId(), dto.getFromRoundId());
    }

    @Override
    public List<AdvanceRowVO> listAdvance(Long eventId, Long toRoundId, Long fromRoundId) {
        Long meetId = currentMeetId();
        if (eventId == null && toRoundId == null && fromRoundId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目、目标轮次、来源轮次至少给出一个");
        }
        return advanceResultMapper.selectAdvanceVOs(meetId, eventId, toRoundId, fromRoundId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdvanceRowVO updateAdvance(Long id, AdvanceUpdateDTO dto) {
        if (id == null || dto == null || dto.getAdvanceOrder() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "晋级名单 ID 与顺序号不能为空");
        }
        Long meetId = currentMeetId();
        AdvanceResult exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "晋级名单不存在");
        }
        if (!Objects.equals(meetId, exists.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        if (AdvanceResult.CONFIRMED.equals(exists.getIsConfirmed())) {
            throw new BizException(ResultCode.CONFLICT, "晋级名单已确认生效，请重新生成后再调整");
        }
        AdvanceResult update = new AdvanceResult();
        update.setId(id);
        // 人工调整统一标记为 MANUAL（除非显式回填来源方式）
        update.setAdvanceType(StringUtils.hasText(dto.getAdvanceType())
                ? dto.getAdvanceType() : AdvanceResult.TYPE_MANUAL);
        update.setAdvanceOrder(dto.getAdvanceOrder());
        update.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : "人工调整晋级顺序");
        if (!updateById(update)) {
            throw new BizException(ResultCode.CONFLICT, "晋级名单已被他人修改，请刷新后重试");
        }
        return advanceResultMapper.selectAdvanceVOById(meetId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AdvanceRowVO> confirmAdvance(Long eventId, Long toRoundId) {
        if (eventId == null || toRoundId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目与目标轮次不能为空");
        }
        Long meetId = currentMeetId();
        List<AdvanceRowVO> rows = advanceResultMapper.selectAdvanceVOs(meetId, eventId, toRoundId, null);
        if (rows.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "该目标轮次暂无晋级名单，请先生成");
        }
        advanceResultMapper.confirmByTargetRound(meetId, eventId, toRoundId,
                SecurityUtils.getUserId(), LocalDateTime.now());
        return advanceResultMapper.selectAdvanceVOs(meetId, eventId, toRoundId, null);
    }

    /* ==================================================================== */
    /*                            内部实现                                   */
    /* ==================================================================== */

    /**
     * 组装引擎晋级规则：显式入参优先，缺省取 {@code event_round_rule}，再缺省取轮次计划值与引擎默认值。
     *
     * @param dto       晋级入参
     * @param fromRound 来源轮次
     * @param roundRule 轮次级规则（可空）
     * @return 晋级规则
     */
    private AdvanceRule buildRule(AdvanceGenerateDTO dto, EventRound fromRound, EventRoundRule roundRule) {
        int groupTopN = firstNonNull(dto.getGroupTopN(),
                roundRule == null ? null : roundRule.getGroupTopN(),
                fromRound.getPlanAdvanceGroupTop(), 2);
        int bestLoserCount = firstNonNull(dto.getBestLoserCount(),
                roundRule == null ? null : roundRule.getBestLoserCount(),
                fromRound.getPlanBestLoserCount(), 8);
        int targetCount = firstNonNull(dto.getTargetCount(),
                roundRule == null ? null : roundRule.getTargetCount(),
                fromRound.getPlanAdvanceCount(), 16);
        boolean excludeIllegalWind = dto.getExcludeIllegalWind() != null
                ? dto.getExcludeIllegalWind()
                : (roundRule == null || roundRule.getExcludeIllegalWind() == null
                        || Integer.valueOf(1).equals(roundRule.getExcludeIllegalWind()));
        boolean excludeInvalidStatus = dto.getExcludeInvalidStatus() == null || dto.getExcludeInvalidStatus();
        String tiePolicy = StringUtils.hasText(dto.getTiePolicy()) ? dto.getTiePolicy().toUpperCase() : "ALL";
        return AdvanceRule.builder()
                .groupTopN(groupTopN)
                .bestLoserCount(bestLoserCount)
                .targetCount(targetCount)
                .excludeIllegalWind(excludeIllegalWind)
                .excludeInvalidStatus(excludeInvalidStatus)
                .tiePolicy(tiePolicy)
                .build();
    }

    /**
     * 按优先级取第一个非空值，全部为空时返回兜底默认值。
     *
     * @param candidates   候选值（按优先级排列），末位为兜底默认值
     * @return 命中的整数
     */
    private int firstNonNull(Integer... candidates) {
        if (candidates == null || candidates.length == 0) {
            return 0;
        }
        for (int index = 0; index < candidates.length - 1; index++) {
            if (candidates[index] != null) {
                return candidates[index];
            }
        }
        Integer fallback = candidates[candidates.length - 1];
        return fallback == null ? 0 : fallback;
    }

    /**
     * 装配单条引擎成绩记录（晋级输入）。
     *
     * @param result   成绩实体
     * @param event    项目
     * @param attempts 试跳值列表（晋级不直接使用，保留以复用同一装配口径）
     * @return 引擎成绩记录
     */
    private ResultRecord toRecord(Result result, Event event, List<BigDecimal> attempts) {
        return ResultRecord.builder()
                .registrationId(result.getRegistrationId())
                .athleteId(result.getAthleteId())
                .unitId(result.getUnitId())
                .groupId(result.getGroupId())
                .gender(event.getGender())
                // 组次用于「每组前 N 名」分组，必须填充
                .heatNo(result.getHeatNo())
                .laneNo(result.getLaneNo())
                .resultRaw(result.getResultRaw())
                .resultValue(result.getResultValue())
                .resultUnit(result.getResultUnit())
                .resultScore(result.getResultScore())
                .status(result.getStatus())
                .windSpeed(result.getWindSpeed())
                .windLegal(resolveWindLegal(result))
                .relay(Event.TYPE_RELAY.equals(event.getEventType())
                        || Integer.valueOf(1).equals(event.getIsRelay()))
                .recordCandidate(Integer.valueOf(1).equals(result.getIsRecordCandidate()))
                .recordType(result.getRecordType())
                // 名次为轮次内全局名次：引擎按「每组前 N 名」分组后再比较名次
                .rankNo(result.getRankNo())
                .rankDisplay(result.getRankDisplay())
                .attempts(attempts == null ? new ArrayList<>() : attempts)
                .build();
    }

    /**
     * 风速合法性：未测风速视为合法，否则以 {@code result.is_wind_legal} 为准。
     *
     * @param result 成绩实体
     * @return 是否合法
     */
    private Boolean resolveWindLegal(Result result) {
        if (result.getWindSpeed() == null) {
            return Boolean.TRUE;
        }
        return !Integer.valueOf(0).equals(result.getIsWindLegal());
    }

    /**
     * 批量装载试跳明细并转换为试跳值列表。
     *
     * @param meetId  届次 ID
     * @param results 成绩列表
     * @return 成绩 ID → 试跳值列表
     */
    private Map<Long, List<BigDecimal>> loadAttempts(Long meetId, List<Result> results) {
        Map<Long, List<BigDecimal>> attemptsMap = new LinkedHashMap<>();
        if (results.isEmpty()) {
            return attemptsMap;
        }
        List<Long> resultIds = new ArrayList<>(results.size());
        for (Result result : results) {
            resultIds.add(result.getId());
        }
        List<com.trackmeet.modules.result.entity.ResultAttempt> attempts =
                resultAttemptMapper.selectByResultIds(meetId, resultIds);
        Map<Long, List<com.trackmeet.modules.result.entity.ResultAttempt>> grouped = new LinkedHashMap<>();
        for (com.trackmeet.modules.result.entity.ResultAttempt attempt : attempts) {
            grouped.computeIfAbsent(attempt.getResultId(), key -> new ArrayList<>()).add(attempt);
        }
        for (Map.Entry<Long, List<com.trackmeet.modules.result.entity.ResultAttempt>> entry : grouped.entrySet()) {
            List<BigDecimal> values = new ArrayList<>();
            for (com.trackmeet.modules.result.entity.ResultAttempt attempt : entry.getValue()) {
                values.add(attempt.validSuccess() ? attempt.getAttemptValue() : null);
            }
            attemptsMap.put(entry.getKey(), values);
        }
        return attemptsMap;
    }

    /**
     * 校验项目存在且属于当前届次。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 项目实体
     */
    private Event requireEvent(Long meetId, Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(meetId, event.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        return event;
    }

    /**
     * 校验轮次存在、属于该项目且属于当前届次。
     *
     * @param meetId  届次 ID
     * @param roundId 轮次 ID
     * @param eventId 项目 ID
     * @return 轮次实体
     */
    private EventRound requireRound(Long meetId, Long roundId, Long eventId) {
        EventRound round = eventRoundMapper.selectById(roundId);
        if (round == null) {
            throw new BizException(ResultCode.NOT_FOUND, "轮次不存在");
        }
        if (!Objects.equals(meetId, round.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        if (eventId != null && !Objects.equals(eventId, round.getEventId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次不属于该项目");
        }
        return round;
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
}
