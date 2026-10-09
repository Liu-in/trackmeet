package com.trackmeet.modules.result.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.TieBreakRule;
import com.trackmeet.engine.result.RankResolver;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.EventRound;
import com.trackmeet.modules.event.entity.EventRoundRule;
import com.trackmeet.modules.event.entity.EventRuleConfig;
import com.trackmeet.modules.event.mapper.EventMapper;
import com.trackmeet.modules.event.mapper.EventRoundMapper;
import com.trackmeet.modules.event.mapper.EventRoundRuleMapper;
import com.trackmeet.modules.event.mapper.EventRuleConfigMapper;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.entity.ResultAttempt;
import com.trackmeet.modules.result.mapper.ResultAttemptMapper;
import com.trackmeet.modules.result.mapper.ResultMapper;
import com.trackmeet.modules.result.service.RankService;
import com.trackmeet.modules.result.vo.RankGenerateResultVO;
import com.trackmeet.modules.result.vo.RankRowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * 名次服务实现：成绩装配 → 引擎计算 → 名次回写。
 * <p>
 * 调用方式（不自建算法副本）：
 * <ol>
 *     <li>装配 {@code com.trackmeet.engine.model.ResultRecord}：填入成绩数值、状态、风速合法性、
 *         组次（{@code heatNo}），田赛另填 {@code attempts}（{@code List&lt;BigDecimal&gt;}，
 *         按试跳顺序，失败 / 免跳 / 无成绩填 {@code null}）；</li>
 *     <li>调用 {@code RankResolver.resolveRank(records, trackAscending, tieBreakRule)} 取得写入名次的结果；</li>
 *     <li>把 {@code rankNo} / {@code rankDisplay} 批量回写 {@code result.rank_no} / {@code result.rank_display}。</li>
 * </ol>
 * 排序方向：径赛 / 接力为 true（成绩越小越好），田赛与全能为 false；
 * 平局规则优先取轮次级 {@code event_round_rule.tie_break_json}，再取项目级
 * {@code event_rule_config.tie_break_json}，均未配置时用 {@code TieBreakRule.defaultRule()}。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class RankServiceImpl implements RankService {

    /** 平局规则解析器（线程安全） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 平局规则可用取值 */
    private static final List<String> SUPPORTED_TIE_RULES = List.of("SECOND_BEST", "BEST_ATTEMPT_ROUND", "DRAW");

    private final ResultMapper resultMapper;

    private final ResultAttemptMapper resultAttemptMapper;

    private final EventMapper eventMapper;

    private final EventRoundMapper eventRoundMapper;

    private final EventRoundRuleMapper eventRoundRuleMapper;

    private final EventRuleConfigMapper eventRuleConfigMapper;

    @Override
    public List<RankRowVO> previewRank(Long eventId, Long roundId, Boolean trackAscending, String tieBreakOrder) {
        return resolve(eventId, roundId, trackAscending, tieBreakOrder, false).rows();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RankGenerateResultVO generateRank(Long eventId, Long roundId, Boolean trackAscending, String tieBreakOrder) {
        RankOutcome outcome = resolve(eventId, roundId, trackAscending, tieBreakOrder, true);
        return outcome.result();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRank(Long eventId, Long roundId) {
        if (eventId == null || roundId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目与轮次不能为空");
        }
        Long meetId = currentMeetId();
        return resultMapper.clearRankByRound(meetId, eventId, roundId);
    }

    @Override
    public boolean resolveTrackAscending(Long eventId) {
        Event event = requireEvent(eventId);
        String type = event.getEventType();
        if (Event.TYPE_TRACK.equals(type) || Event.TYPE_RELAY.equals(type)) {
            return true;
        }
        // 田赛（远度/高度）与全能总分均为越大越好
        return false;
    }

    @Override
    public TieBreakRule resolveTieBreakRule(Long roundId, Long eventId, String explicitOrder) {
        // ① 显式指定（请求参数 / 生成入参）优先
        List<String> order = parseTieOrder(explicitOrder);
        if (!order.isEmpty()) {
            return buildRule(order);
        }
        Long meetId = MeetContext.get();
        // ② 轮次级规则
        if (roundId != null && meetId != null) {
            EventRoundRule roundRule = eventRoundRuleMapper.selectOne(Wrappers.<EventRoundRule>lambdaQuery()
                    .eq(EventRoundRule::getMeetId, meetId)
                    .eq(EventRoundRule::getRoundId, roundId)
                    .last("LIMIT 1"));
            if (roundRule != null) {
                order = parseTieOrder(roundRule.getTieBreakJson());
                if (!order.isEmpty()) {
                    return buildRule(order);
                }
            }
        }
        // ③ 项目级规则
        if (eventId != null && meetId != null) {
            EventRuleConfig eventRule = eventRuleConfigMapper.selectOne(Wrappers.<EventRuleConfig>lambdaQuery()
                    .eq(EventRuleConfig::getMeetId, meetId)
                    .eq(EventRuleConfig::getEventId, eventId)
                    .last("LIMIT 1"));
            if (eventRule != null) {
                order = parseTieOrder(eventRule.getTieBreakJson());
                if (!order.isEmpty()) {
                    return buildRule(order);
                }
            }
        }
        // ④ 引擎默认：次优成绩 → 最远成功轮次 → 允许并列
        return TieBreakRule.defaultRule();
    }

    /* ==================================================================== */
    /*                            内部实现                                   */
    /* ==================================================================== */

    /**
     * 名次解析主流程。
     *
     * @param eventId        项目 ID
     * @param roundId        轮次 ID
     * @param trackAscending 排序方向（可空，按项目类型推断）
     * @param tieBreakOrder  平局规则顺序（可空）
     * @param persist        是否回写数据库
     * @return 名次解析结果（视图 + 行数据）
     */
    private RankOutcome resolve(Long eventId, Long roundId, Boolean trackAscending,
                                String tieBreakOrder, boolean persist) {
        if (eventId == null || roundId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目与轮次不能为空");
        }
        Long meetId = currentMeetId();
        Event event = requireEvent(eventId);
        EventRound round = eventRoundMapper.selectById(roundId);
        if (round == null) {
            throw new BizException(ResultCode.NOT_FOUND, "轮次不存在");
        }
        if (!Objects.equals(eventId, round.getEventId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次不属于该项目");
        }

        boolean ascending = trackAscending == null
                ? (Event.TYPE_TRACK.equals(event.getEventType()) || Event.TYPE_RELAY.equals(event.getEventType()))
                : trackAscending;
        TieBreakRule rule = resolveTieBreakRule(roundId, eventId, tieBreakOrder);

        List<Result> results = resultMapper.selectByRound(meetId, eventId, roundId);
        Map<Long, List<BigDecimal>> attemptsByResult = loadAttempts(meetId, results);
        List<ResultRecord> records = new ArrayList<>(results.size());
        Map<Long, Result> resultById = new LinkedHashMap<>();
        for (Result result : results) {
            resultById.put(result.getId(), result);
            records.add(toRecord(result, attemptsByResult.get(result.getId()), event));
        }

        List<ResultRecord> ranked = RankResolver.resolveRank(records, ascending, rule);
        // 建立「记录对象 → 成绩 ID」关联：records 与 ranked 是同一批对象，按对象身份映射即可
        Map<ResultRecord, Long> identityMap = buildIdentityMap(records, results);
        if (persist) {
            persistRank(meetId, ranked, identityMap);
        }

        List<RankRowVO> rows = new ArrayList<>(ranked.size());
        for (ResultRecord record : ranked) {
            Result entity = resultById.get(identityMap.get(record));
            if (entity == null) {
                continue;
            }
            rows.add(toRankRow(entity, record, attemptsByResult.get(entity.getId()), event, persist));
        }

        RankGenerateResultVO result = buildResult(event, round, ascending, ranked, rows);
        return new RankOutcome(rows, result);
    }

    /**
     * 建立「引擎记录对象 → 成绩 ID」映射。
     * <p>
     * 使用 {@link java.util.IdentityHashMap}：{@code ResultRecord} 为 Lombok {@code @Data}
     * 生成全字段 {@code equals/hashCode}，而引擎会改写其 {@code rankNo}，若用普通 Map
     * 会因哈希变化导致回查失败；按对象身份映射可完全规避该问题。
     *
     * @param records 装配出的引擎记录（顺序与成绩列表一致）
     * @param results 成绩实体列表（顺序与引擎记录一致）
     * @return 身份映射
     */
    private Map<ResultRecord, Long> buildIdentityMap(List<ResultRecord> records, List<Result> results) {
        Map<ResultRecord, Long> identityMap = new IdentityHashMap<>();
        for (int index = 0; index < records.size() && index < results.size(); index++) {
            identityMap.put(records.get(index), results.get(index).getId());
        }
        return identityMap;
    }

    /**
     * 批量回写名次（{@code rank_no} / {@code rank_display}），无效成绩写空以清除历史名次。
     *
     * @param meetId      届次 ID
     * @param ranked      引擎写回名次后的记录
     * @param identityMap 记录 → 成绩 ID 映射
     */
    private void persistRank(Long meetId, List<ResultRecord> ranked, Map<ResultRecord, Long> identityMap) {
        for (ResultRecord record : ranked) {
            Long resultId = identityMap.get(record);
            if (resultId == null) {
                continue;
            }
            // 无效成绩写入空值以清除历史名次
            resultMapper.updateRankNo(meetId, resultId, record.getRankNo());
            resultMapper.updateRankDisplay(meetId, resultId, record.getRankDisplay());
        }
    }

    /**
     * 装配单条引擎成绩记录。
     *
     * @param result   成绩实体
     * @param attempts 该成绩的试跳数值列表（按试跳顺序，失败/免跳为 null）
     * @param event    项目
     * @return 引擎成绩记录
     */
    private ResultRecord toRecord(Result result, List<BigDecimal> attempts, Event event) {
        boolean relay = Event.TYPE_RELAY.equals(event.getEventType())
                || Integer.valueOf(1).equals(event.getIsRelay());
        return ResultRecord.builder()
                .registrationId(result.getRegistrationId())
                .athleteId(result.getAthleteId())
                .unitId(result.getUnitId())
                .groupId(result.getGroupId())
                .gender(event.getGender())
                .heatNo(result.getHeatNo())
                .laneNo(result.getLaneNo())
                .resultRaw(result.getResultRaw())
                .resultValue(result.getResultValue())
                .resultUnit(result.getResultUnit())
                .resultScore(result.getResultScore())
                .status(result.getStatus())
                .windSpeed(result.getWindSpeed())
                .windLegal(resolveWindLegal(result))
                .relay(relay)
                .recordCandidate(Integer.valueOf(1).equals(result.getIsRecordCandidate()))
                .recordType(result.getRecordType())
                .attempts(attempts == null ? new ArrayList<>() : attempts)
                .build();
    }

    /**
     * 风速合法性：未测风速（{@code wind_speed} 为空）视为合法，
     * 否则以 {@code result.is_wind_legal} 为准（0 表示超风速）。
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
     * 批量装载试跳明细并转换为「试跳值列表」（失败 / 免跳 / 无值填 null）。
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
        List<ResultAttempt> attempts = resultAttemptMapper.selectByResultIds(meetId, resultIds);
        Map<Long, List<ResultAttempt>> grouped = new LinkedHashMap<>();
        for (ResultAttempt attempt : attempts) {
            grouped.computeIfAbsent(attempt.getResultId(), key -> new ArrayList<>()).add(attempt);
        }
        for (Map.Entry<Long, List<ResultAttempt>> entry : grouped.entrySet()) {
            List<BigDecimal> values = new ArrayList<>();
            for (ResultAttempt attempt : entry.getValue()) {
                // 失败 / 免跳 / 犯规 → null 占位，保持轮次顺序，供 SECOND_BEST 与 BEST_ATTEMPT_ROUND 使用
                values.add(attempt.validSuccess() ? attempt.getAttemptValue() : null);
            }
            attemptsMap.put(entry.getKey(), values);
        }
        return attemptsMap;
    }

    /**
     * 组装名次行视图（含田赛平局比较字段）。
     *
     * @param entity    成绩实体
     * @param record    引擎记录（已写入名次）
     * @param attempts  试跳值列表
     * @param event     项目（取接力标记）
     * @param generated 是否已回写数据库
     * @return 名次行视图
     */
    private RankRowVO toRankRow(Result entity, ResultRecord record, List<BigDecimal> attempts,
                                Event event, boolean generated) {
        RankRowVO row = new RankRowVO();
        row.setId(entity.getId());
        row.setRankNo(record.getRankNo());
        row.setRankDisplay(record.getRankDisplay());
        row.setResultRaw(entity.getResultRaw());
        row.setResultValue(entity.getResultValue());
        row.setResultUnit(entity.getResultUnit());
        row.setResultScore(entity.getResultScore());
        row.setFinalHeight(entity.getFinalHeight());
        row.setStatus(entity.getStatus());
        row.setAuditStatus(entity.getAuditStatus());
        row.setWindSpeed(entity.getWindSpeed());
        row.setIsWindLegal(entity.getIsWindLegal());
        row.setIsRecordConfirmed(entity.getIsRecordConfirmed());
        row.setGenerated(generated ? 1 : 0);
        row.setRegistrationId(entity.getRegistrationId());
        row.setAthleteId(entity.getAthleteId());
        row.setUnitId(entity.getUnitId());
        row.setGroupId(entity.getGroupId());
        row.setEventId(entity.getEventId());
        row.setRoundId(entity.getRoundId());
        row.setHeatNo(entity.getHeatNo());
        boolean relay = Event.TYPE_RELAY.equals(event.getEventType())
                || Integer.valueOf(1).equals(event.getIsRelay());
        row.setRelayFlag(relay ? 1 : 0);
        row.setBestAttempt(maxAttempt(attempts));
        row.setSecondBest(secondMaxAttempt(attempts));
        row.setBestAttemptRound(bestAttemptRound(entity.getId(), attempts));
        row.setSuccessAttemptCount(successCount(attempts));
        return row;
    }

    /**
     * 组装名次生成结果视图（含并列说明）。
     *
     * @param event     项目
     * @param round     轮次
     * @param ascending 排序方向
     * @param ranked    引擎结果
     * @param rows      名次行
     * @return 结果视图
     */
    private RankGenerateResultVO buildResult(Event event, EventRound round, boolean ascending,
                                             List<ResultRecord> ranked, List<RankRowVO> rows) {
        RankGenerateResultVO view = new RankGenerateResultVO();
        view.setEventId(event.getId());
        view.setEventName(event.getEventName());
        view.setRoundId(round.getId());
        view.setRoundName(round.getRoundName());
        view.setTrackAscending(ascending);
        view.setRanks(rows);

        int rankedCount = 0;
        int invalidCount = 0;
        Map<Integer, List<String>> tieGroups = new LinkedHashMap<>();
        for (RankRowVO row : rows) {
            if (row.getRankNo() == null) {
                invalidCount++;
                continue;
            }
            rankedCount++;
            if (row.getRankDisplay() != null && row.getRankDisplay().contains("并列")) {
                tieGroups.computeIfAbsent(row.getRankNo(), key -> new ArrayList<>())
                        .add(StringUtils.hasText(row.getAthleteName()) ? row.getAthleteName() : String.valueOf(row.getId()));
            }
        }
        view.setTotalCount(rows.size());
        view.setRankedCount(rankedCount);
        view.setInvalidCount(invalidCount);
        view.setHasTie(!tieGroups.isEmpty());
        List<String> tieNotes = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> entry : tieGroups.entrySet()) {
            tieNotes.add("第 " + entry.getKey() + " 名并列：" + String.join("、", entry.getValue()));
        }
        view.setTieGroups(tieNotes);
        if (invalidCount > 0) {
            view.getRemarks().add(invalidCount + " 条无效成绩（DNS/DNF/DSQ/NM/PASS/SCRATCH）未参与名次，已排在名次表末尾");
        }
        if (ranked.isEmpty()) {
            view.getRemarks().add("本轮暂无可排名成绩，请先录入并提交成绩");
        }
        return view;
    }

    /* ======================= 试跳派生指标（展示用） ======================= */

    /**
     * 试跳最大有效成绩。
     *
     * @param attempts 试跳值列表
     * @return 最大有效值，无有效值返回 null
     */
    private BigDecimal maxAttempt(List<BigDecimal> attempts) {
        BigDecimal best = null;
        if (attempts == null) {
            return null;
        }
        for (BigDecimal value : attempts) {
            if (value != null && (best == null || value.compareTo(best) > 0)) {
                best = value;
            }
        }
        return best;
    }

    /**
     * 试跳次优有效成绩（平局规则 SECOND_BEST 的比较依据）。
     *
     * @param attempts 试跳值列表
     * @return 次优值，有效值不足两个时返回 null
     */
    private BigDecimal secondMaxAttempt(List<BigDecimal> attempts) {
        if (attempts == null) {
            return null;
        }
        List<BigDecimal> values = new ArrayList<>();
        for (BigDecimal value : attempts) {
            if (value != null) {
                values.add(value);
            }
        }
        if (values.size() < 2) {
            return null;
        }
        values.sort((first, second) -> second.compareTo(first));
        return values.get(1);
    }

    /**
     * 最好成绩出现的轮次序号（平局规则 BEST_ATTEMPT_ROUND 的比较依据）。
     *
     * @param resultId 成绩 ID
     * @param attempts 试跳值列表
     * @return 最好成绩所在下标（从 0 起），无有效值返回 null
     */
    private Integer bestAttemptRound(Long resultId, List<BigDecimal> attempts) {
        if (resultId == null || attempts == null || attempts.isEmpty()) {
            return null;
        }
        int bestIndex = -1;
        BigDecimal best = null;
        for (int index = 0; index < attempts.size(); index++) {
            BigDecimal value = attempts.get(index);
            if (value == null) {
                continue;
            }
            if (best == null || value.compareTo(best) > 0) {
                best = value;
                bestIndex = index;
            }
        }
        return bestIndex < 0 ? null : bestIndex;
    }

    /**
     * 有效成功试跳次数。
     *
     * @param attempts 试跳值列表
     * @return 成功次数
     */
    private int successCount(List<BigDecimal> attempts) {
        if (attempts == null) {
            return 0;
        }
        int count = 0;
        for (BigDecimal value : attempts) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }

    /* ======================= 规则与校验 ======================= */

    /**
     * 解析平局规则顺序。
     * <p>
     * 兼容两种写法：JSON 数组 {@code ["SECOND_BEST","DRAW"]} 与对象
     * {@code {"order":["SECOND_BEST"],"allowParallelRank":true}}；逗号分隔字符串亦兼容。
     *
     * @param json JSON 文本或逗号分隔文本
     * @return 合法且去重的规则顺序（无法解析时返回空列表）
     */
    private List<String> parseTieOrder(String json) {
        List<String> order = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return order;
        }
        String text = json.trim();
        try {
            if (text.startsWith("[")) {
                JsonNode array = JSON_MAPPER.readTree(text);
                appendTieRules(order, array);
                return order;
            }
            if (text.startsWith("{")) {
                JsonNode node = JSON_MAPPER.readTree(text);
                JsonNode array = node.path("order");
                if (array.isMissingNode() || !array.isArray()) {
                    array = node.path("rules");
                }
                appendTieRules(order, array);
                return order;
            }
        } catch (Exception e) {
            // 非法 JSON 退化为逗号分隔解析，避免因规则配置脏数据阻断名次生成
        }
        for (String item : text.split("[,，;；\\s]+")) {
            addTieRule(order, item);
        }
        return order;
    }

    /**
     * 追加 JSON 数组中的规则项。
     *
     * @param order 结果集合
     * @param array JSON 数组节点
     */
    private void appendTieRules(List<String> order, JsonNode array) {
        if (array == null || !array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            addTieRule(order, node.asText());
        }
    }

    /**
     * 追加单个规则项（仅接受受支持取值并去重）。
     *
     * @param order 结果集合
     * @param rule  规则项
     */
    private void addTieRule(List<String> order, String rule) {
        if (!StringUtils.hasText(rule)) {
            return;
        }
        String normalized = rule.trim().toUpperCase();
        if (SUPPORTED_TIE_RULES.contains(normalized) && !order.contains(normalized)) {
            order.add(normalized);
        }
    }

    /**
     * 按规则顺序构造引擎平局规则（允许并列，符合田径「成绩相同名次并列」惯例）。
     *
     * @param order 规则顺序
     * @return 平局规则
     */
    private TieBreakRule buildRule(List<String> order) {
        return TieBreakRule.builder()
                .order(new ArrayList<>(order))
                .allowParallelRank(true)
                .build();
    }

    /**
     * 校验项目存在并属于当前届次。
     *
     * @param eventId 项目 ID
     * @return 项目实体
     */
    private Event requireEvent(Long eventId) {
        if (eventId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目不能为空");
        }
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        Long meetId = MeetContext.get();
        if (meetId != null && !Objects.equals(meetId, event.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        return event;
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
     * 名次解析内部结果（行数据 + 对外视图）。
     *
     * @param rows   名次行
     * @param result 名次生成结果视图
     */
    private record RankOutcome(List<RankRowVO> rows, RankGenerateResultVO result) {
    }
}
