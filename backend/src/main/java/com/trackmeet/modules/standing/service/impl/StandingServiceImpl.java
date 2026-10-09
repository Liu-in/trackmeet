package com.trackmeet.modules.standing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.engine.model.BonusRuleRow;
import com.trackmeet.engine.model.MedalRow;
import com.trackmeet.engine.model.PointAward;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.ScoreRuleRow;
import com.trackmeet.engine.model.TeamScoreRow;
import com.trackmeet.engine.result.RankResolver;
import com.trackmeet.engine.score.ScoreCalculator;
import com.trackmeet.engine.score.StandingCalculator;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import com.trackmeet.modules.event.entity.ScoreRule;
import com.trackmeet.modules.event.mapper.EventMapper;
import com.trackmeet.modules.standing.dto.StandingAttemptRow;
import com.trackmeet.modules.standing.dto.StandingGroupRow;
import com.trackmeet.modules.standing.dto.StandingMedalRow;
import com.trackmeet.modules.standing.dto.StandingMeetRow;
import com.trackmeet.modules.standing.dto.StandingPointRow;
import com.trackmeet.modules.standing.dto.StandingQuery;
import com.trackmeet.modules.standing.dto.StandingRecalculateDTO;
import com.trackmeet.modules.standing.dto.StandingRecordRow;
import com.trackmeet.modules.standing.dto.StandingResultRow;
import com.trackmeet.modules.standing.dto.StandingSnapshotDTO;
import com.trackmeet.modules.standing.dto.StandingTeamScoreRow;
import com.trackmeet.modules.standing.dto.StandingUnitRow;
import com.trackmeet.modules.standing.entity.MedalStanding;
import com.trackmeet.modules.standing.entity.Points;
import com.trackmeet.modules.standing.entity.TeamScore;
import com.trackmeet.modules.standing.mapper.StandingMapper;
import com.trackmeet.modules.standing.service.StandingService;
import com.trackmeet.modules.standing.service.support.ResultRecordAssembler;
import com.trackmeet.modules.standing.service.support.StandingRuleResolver;
import com.trackmeet.modules.standing.service.support.StandingText;
import com.trackmeet.modules.standing.vo.MedalVO;
import com.trackmeet.modules.standing.vo.StandingOverviewVO;
import com.trackmeet.modules.standing.vo.TeamScoreVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 榜单服务实现：团体总分与奖牌榜的查询、重算与快照。
 * <p>
 * <b>职责边界</b>：算法全部来自 {@code com.trackmeet.engine}（{@link ScoreCalculator} /
 * {@link StandingCalculator} / {@link RankResolver}），本类只负责
 * 「读库装配 → 调引擎 → 幂等落库」，并补齐 DDL 要求但引擎输出未覆盖的字段：
 * <ul>
 *     <li>{@code relay_point_sum} / {@code event_count} / {@code athlete_count}
 *         （由积分明细在服务层按「单位 × 组别 × 口径」二次累计）；</li>
 *     <li>{@code meet_rule_config.bonus_in_team_score = 0} 时把破纪录加分排除出团体总分（R-21）：
 *         做法是把加分清零后交给引擎汇总，不改写引擎算法；</li>
 *     <li>{@code medal_sort = POINT_FIRST} 时按团体总分重排奖牌榜（R-22）。</li>
 * </ul>
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandingServiceImpl extends ServiceImpl<StandingMapper, TeamScore> implements StandingService {

    /** 计算批次号时间格式 */
    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 奖牌榜排序：积分优先 */
    private static final String MEDAL_SORT_POINT_FIRST = "POINT_FIRST";

    private final StandingMapper standingMapper;
    private final EventMapper eventMapper;
    private final StandingRuleResolver ruleResolver;
    private final ResultRecordAssembler assembler;

    // ==================================================================================
    // 一、查询（读快照；快照为空时实时计算并落库）
    // ==================================================================================

    @Override
    public List<TeamScoreVO> listTeamScores(StandingQuery query) {
        Long meetId = requireMeet(query);
        String scoreType = query.normalizedScoreType();
        List<StandingTeamScoreRow> rows = standingMapper.selectTeamScores(meetId, query.getGroupId(), scoreType);
        if (rows.isEmpty()) {
            recalculate(scopeOf(meetId, query.getGroupId(), null));
            rows = standingMapper.selectTeamScores(meetId, query.getGroupId(), scoreType);
        }
        List<TeamScoreVO> result = new ArrayList<>(rows.size());
        for (StandingTeamScoreRow row : rows) {
            result.add(toTeamScoreVO(row));
        }
        return result;
    }

    @Override
    public List<MedalVO> listMedals(StandingQuery query) {
        Long meetId = requireMeet(query);
        List<StandingMedalRow> rows = standingMapper.selectMedalStandings(meetId, query.getGroupId());
        if (rows.isEmpty()) {
            recalculate(scopeOf(meetId, query.getGroupId(), null));
            rows = standingMapper.selectMedalStandings(meetId, query.getGroupId());
        }
        List<MedalVO> result = new ArrayList<>(rows.size());
        for (StandingMedalRow row : rows) {
            result.add(toMedalVO(row));
        }
        return result;
    }

    // ==================================================================================
    // 二、重算（幂等：按范围先物理删除再整批插入）
    // ==================================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StandingOverviewVO recalculate(StandingRecalculateDTO dto) {
        if (dto == null || dto.getMeetId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        Long meetId = dto.getMeetId();
        checkMeetContext(meetId);
        Long groupId = dto.getGroupId();
        Long eventId = dto.getEventId();
        LocalDateTime calcTime = LocalDateTime.now();
        String calcBatch = "ST" + BATCH_FORMAT.format(calcTime);

        // 1) 规则全部读库（名次分 / 破纪录加分 / 接力倍数 / 团体分口径 / 奖牌榜排序）
        MeetRuleConfig meetRuleConfig = ruleResolver.loadMeetRuleConfig(meetId);
        List<BonusRuleRow> bonusRules = ruleResolver.loadBonusRules(meetId);
        boolean relayDouble = ruleResolver.relayDoubleEnabled(bonusRules);
        boolean bonusInTeamScore = ruleResolver.bonusInTeamScore(meetRuleConfig);
        String medalSort = ruleResolver.medalSort(meetRuleConfig);
        Map<Long, ScoreRule> scoreRules = ruleResolver.loadScoreRules(meetId);
        Map<Long, List<ScoreRuleRow>> ruleItems = ruleResolver.loadScoreRuleItems(meetId, scoreRules);

        // 2) 项目字典
        List<Event> events = eventMapper.selectList(new LambdaQueryWrapper<Event>().eq(Event::getMeetId, meetId));
        Map<Long, Event> eventById = new HashMap<>(events.size());
        for (Event event : events) {
            eventById.put(event.getId(), event);
        }

        // 3) 已审核成绩全量取回（团体总分与奖牌榜需要全量口径，故查询不按范围裁剪）
        List<StandingResultRow> allRows = standingMapper.selectApprovedResults(meetId, null, null, null);
        Map<Long, StandingResultRow> rowByRegistration = new HashMap<>();
        List<Long> resultIds = new ArrayList<>(allRows.size());
        for (StandingResultRow row : allRows) {
            if (row.getRegistrationId() != null) {
                rowByRegistration.putIfAbsent(row.getRegistrationId(), row);
            }
            if (row.getResultId() != null) {
                resultIds.add(row.getResultId());
            }
        }

        // 4) 试跳成绩（引擎平局规则用）按成绩 ID 批量装载
        Map<Long, List<BigDecimal>> attemptMap = new HashMap<>();
        if (!resultIds.isEmpty()) {
            List<StandingAttemptRow> attempts = standingMapper.selectAttempts(meetId, resultIds);
            attemptMap = assembler.groupAttempts(attempts);
        }

        // 5) 按「项目 × 轮次」分区：同一轮次内才有名次可比性，与引擎 calculate 的入参语义一致
        Map<String, List<ResultRecord>> partitions = new LinkedHashMap<>();
        Map<String, Long> partitionEvent = new HashMap<>();
        Map<String, String> partitionRoundCode = new HashMap<>();
        for (StandingResultRow row : allRows) {
            if (row.getEventId() == null || row.getRoundId() == null) {
                continue;
            }
            String key = row.getEventId() + "#" + row.getRoundId();
            partitions.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(assembler.toRecord(row, attemptMap.get(row.getResultId())));
            partitionEvent.putIfAbsent(key, row.getEventId());
            partitionRoundCode.putIfAbsent(key, row.getRoundCode());
        }

        // 6) 逐分区调用引擎计算积分
        List<PointAward> awards = new ArrayList<>();
        List<Points> pointEntities = new ArrayList<>();
        for (Map.Entry<String, List<ResultRecord>> entry : partitions.entrySet()) {
            String key = entry.getKey();
            Event event = eventById.get(partitionEvent.get(key));
            if (event == null) {
                continue;
            }
            List<ResultRecord> records = resolveRankIfNeeded(entry.getValue(), event);
            ScoreRule rule = ruleResolver.resolveScoreRule(event, meetRuleConfig, scoreRules);
            Long ruleId = rule == null ? null : rule.getId();
            List<ScoreRuleRow> scoreRows = ruleResolver.scoreRowsOf(ruleId, ruleItems);
            // 传入当前分区的轮次码：引擎据此剔除 effectiveRoundCode 不符的规则行，避免同项目多轮次重复计分
            String roundCode = partitionRoundCode.get(key);
            List<PointAward> awardsOfPartition = ScoreCalculator.calculate(records, scoreRows, bonusRules,
                    relayDouble, roundCode);
            for (PointAward award : awardsOfPartition) {
                StandingResultRow source = rowByRegistration.get(award.getRegistrationId());
                if (source == null) {
                    continue;
                }
                // 重算范围只裁剪「写入 points 的明细」，不裁剪团体总分 / 奖牌榜的全量口径
                if (eventId != null && !eventId.equals(source.getEventId())) {
                    continue;
                }
                if (groupId != null && !groupId.equals(source.getGroupId())) {
                    continue;
                }
                awards.add(award);
                pointEntities.add(toPointEntity(meetId, award, source, rule, calcTime, calcBatch));
            }
        }

        // 7) 幂等重建：按范围物理删除后整批插入（唯一键不含 deleted，必须物理删除）
        standingMapper.deletePoints(meetId, eventId, groupId);
        if (!pointEntities.isEmpty()) {
            standingMapper.batchInsertPoints(pointEntities);
        }

        // 8) 团体总分与奖牌榜
        Map<Long, String> unitNames = loadUnitNameMap(meetId);
        int teamCount = rebuildTeamScores(meetId, groupId, awards, bonusInTeamScore, unitNames,
                rowByRegistration, eventById, calcTime, calcBatch);
        int medalCount = rebuildMedalStandings(meetId, groupId, allRows, unitNames, medalSort, calcTime, calcBatch);

        log.info("积分重算完成：meetId={}, groupId={}, eventId={}, points={}, teamScores={}, medals={}, batch={}",
                meetId, groupId, eventId, pointEntities.size(), teamCount, medalCount, calcBatch);

        StandingOverviewVO overview = new StandingOverviewVO();
        overview.setMeetId(meetId);
        overview.setGroupId(groupId);
        overview.setEventId(eventId);
        overview.setPointCount(pointEntities.size());
        overview.setTeamScoreCount(teamCount);
        overview.setMedalCount(medalCount);
        overview.setTotalPoint(totalPointOf(meetId, groupId));
        overview.setCalcTime(calcTime);
        overview.setCalcBatch(calcBatch);
        overview.setMedalSort(medalSort);
        overview.setScopeText(scopeText(groupId, eventId));
        overview.setTeamScores(toTeamScoreVOs(meetId, groupId));
        overview.setMedals(toMedalVOs(meetId, groupId));
        return overview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StandingOverviewVO snapshot(StandingSnapshotDTO dto) {
        if (dto == null || dto.getMeetId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        Long meetId = dto.getMeetId();
        checkMeetContext(meetId);
        String scoreType = normalizeScoreType(dto.getScoreType());
        LocalDateTime calcTime = LocalDateTime.now();
        String calcBatch = "SS" + BATCH_FORMAT.format(calcTime);
        int teamCount = standingMapper.updateTeamScoreSnapshot(meetId, scoreType, calcTime, calcBatch);
        int medalCount = standingMapper.updateMedalSnapshot(meetId, calcTime, calcBatch);
        if (teamCount == 0 && medalCount == 0) {
            throw new BizException(ResultCode.CONFLICT, "暂无可快照的榜单数据，请先重算积分");
        }
        MeetRuleConfig meetRuleConfig = ruleResolver.loadMeetRuleConfig(meetId);
        StandingOverviewVO overview = new StandingOverviewVO();
        overview.setMeetId(meetId);
        overview.setPointCount(0);
        overview.setTeamScoreCount(teamCount);
        overview.setMedalCount(medalCount);
        overview.setCalcTime(calcTime);
        overview.setCalcBatch(calcBatch);
        overview.setMedalSort(ruleResolver.medalSort(meetRuleConfig));
        overview.setScopeText(scoreType == null ? "全部团体口径快照" : StandingText.scoreTypeName(scoreType) + "快照");
        overview.setTeamScores(toTeamScoreVOs(meetId, null));
        overview.setMedals(toMedalVOs(meetId, null));
        return overview;
    }

    // ==================================================================================
    // 三、内部：积分实体装配
    // ==================================================================================

    /**
     * 引擎积分结果 → {@code points} 实体。
     * <p>
     * 引擎的 {@link PointAward} 不含项目 / 轮次 / 成绩 ID，故由来源成绩投影行补齐。
     *
     * @param meetId    届次 ID
     * @param award     引擎积分结果
     * @param source    来源成绩投影行
     * @param rule      命中的名次分方案（可空）
     * @param calcTime  计算时间
     * @param calcBatch 计算批次号
     * @return 积分明细实体
     */
    private Points toPointEntity(Long meetId, PointAward award, StandingResultRow source, ScoreRule rule,
                                 LocalDateTime calcTime, String calcBatch) {
        Points entity = new Points();
        entity.setMeetId(meetId);
        entity.setEventId(source.getEventId());
        entity.setRoundId(source.getRoundId());
        entity.setResultId(source.getResultId());
        entity.setRegistrationId(award.getRegistrationId());
        entity.setAthleteId(award.getAthleteId() == null ? source.getAthleteId() : award.getAthleteId());
        entity.setUnitId(award.getUnitId() == null ? source.getUnitId() : award.getUnitId());
        entity.setGroupId(award.getGroupId() == null ? source.getGroupId() : award.getGroupId());
        entity.setGender(award.getGender() == null ? source.getGender() : award.getGender());
        entity.setRankNo(award.getRankNo());
        entity.setRankPoint(award.getRankPoint());
        entity.setBonusPoint(award.getBonusPoint());
        entity.setRelayFactor(award.getRelayFactor());
        entity.setTotalPoint(award.getTotalPoint());
        entity.setScoreRuleId(rule == null ? null : rule.getId());
        entity.setScoreRuleCode(rule == null ? null : rule.getRuleCode());
        entity.setBonusRuleIds(joinBonusRuleCodes(award.getAppliedBonusCodes()));
        entity.setCalcTime(calcTime);
        entity.setIsManual(0);
        entity.setRemark("批次" + calcBatch);
        return entity;
    }

    /**
     * 命中加分规则编码 → 逗号分隔编码串（{@code points.bonus_rule_ids} 长度上限 200）。
     *
     * @param codes 规则编码列表
     * @return 逗号分隔编码串，无命中时返回 {@code null}
     */
    private String joinBonusRuleCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(code.trim());
        }
        if (builder.length() == 0) {
            return null;
        }
        String value = builder.toString();
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    // ==================================================================================
    // 四、内部：团体总分
    // ==================================================================================

    /**
     * 重建团体总分快照：按范围物理删除 → 引擎汇总 → 整批插入，并补齐统计列。
     *
     * @return 写入行数
     */
    private int rebuildTeamScores(Long meetId, Long groupId, List<PointAward> awards, boolean bonusInTeamScore,
                                  Map<Long, String> unitNames,
                                  Map<Long, StandingResultRow> rowByRegistration,
                                  Map<Long, Event> eventById,
                                  LocalDateTime calcTime, String calcBatch) {
        standingMapper.deleteTeamScores(meetId, groupId);
        if (awards == null || awards.isEmpty()) {
            return 0;
        }
        List<PointAward> teamAwards = bonusInTeamScore ? awards : zeroBonus(awards);
        List<TeamScoreRow> rows = StandingCalculator.teamScores(teamAwards, unitNames);
        Map<String, TeamExtra> extraMap = buildTeamExtras(awards, rowByRegistration, eventById);

        List<TeamScore> entities = new ArrayList<>(rows.size());
        for (TeamScoreRow row : rows) {
            TeamExtra extra = extraMap.get(teamKey(row.getUnitId(), row.getGroupId(), row.getScoreType()));
            TeamScore entity = new TeamScore();
            entity.setMeetId(meetId);
            entity.setUnitId(row.getUnitId());
            entity.setGroupId(row.getGroupId());
            entity.setScoreType(row.getScoreType());
            entity.setRankPointSum(nullSafe(row.getRankPointSum()));
            entity.setBonusPointSum(nullSafe(row.getBonusPointSum()));
            entity.setRelayPointSum(extra == null ? BigDecimal.ZERO : extra.relayPointSum);
            entity.setTotalPoint(nullSafe(row.getTotalPoint()));
            entity.setRankNo(row.getRankNo());
            entity.setRankDisplay(String.valueOf(row.getRankNo()));
            entity.setEventCount(extra == null ? 0 : extra.eventIds.size());
            entity.setAthleteCount(extra == null ? 0 : extra.athleteIds.size());
            entity.setCalcTime(calcTime);
            entity.setCalcBatch(calcBatch);
            entity.setIsPublished(0);
            entities.add(entity);
        }
        if (!entities.isEmpty()) {
            standingMapper.batchInsertTeamScores(entities);
        }
        return entities.size();
    }

    /**
     * 破纪录加分不计入团体总分时的口径修正：返回加分清零的副本（不修改入参、不改写引擎算法）。
     *
     * @param awards 原始积分明细
     * @return 加分清零后的副本
     */
    private List<PointAward> zeroBonus(List<PointAward> awards) {
        List<PointAward> copy = new ArrayList<>(awards.size());
        for (PointAward award : awards) {
            PointAward item = new PointAward();
            item.setRegistrationId(award.getRegistrationId());
            item.setAthleteId(award.getAthleteId());
            item.setUnitId(award.getUnitId());
            item.setGroupId(award.getGroupId());
            item.setGender(award.getGender());
            item.setRankNo(award.getRankNo());
            item.setRankPoint(award.getRankPoint());
            item.setBonusPoint(BigDecimal.ZERO);
            item.setRelayFactor(award.getRelayFactor());
            BigDecimal relayFactor = award.getRelayFactor() == null ? BigDecimal.ONE : award.getRelayFactor();
            BigDecimal rankPoint = award.getRankPoint() == null ? BigDecimal.ZERO : award.getRankPoint();
            item.setTotalPoint(rankPoint.multiply(relayFactor));
            item.setAppliedBonusCodes(new ArrayList<>());
            copy.add(item);
        }
        return copy;
    }

    /**
     * 汇总引擎输出未覆盖的团队口径列：接力得分、得分项目数、得分人数。
     *
     * @param awards            本次积分明细
     * @param rowByRegistration 报名记录 ID → 成绩投影行
     * @param eventById         项目 ID → 项目实体
     * @return 团队口径键 → 附加统计
     */
    private Map<String, TeamExtra> buildTeamExtras(List<PointAward> awards,
                                                   Map<Long, StandingResultRow> rowByRegistration,
                                                   Map<Long, Event> eventById) {
        Map<String, TeamExtra> map = new HashMap<>();
        for (PointAward award : awards) {
            StandingResultRow source = award == null ? null : rowByRegistration.get(award.getRegistrationId());
            if (source == null) {
                continue;
            }
            Event event = eventById.get(source.getEventId());
            boolean relay = source.getRegistrationRelay() != null
                    ? source.getRegistrationRelay() == 1
                    : (event != null && event.getIsRelay() != null && event.getIsRelay() == 1);
            BigDecimal totalPoint = award.getTotalPoint() == null ? BigDecimal.ZERO : award.getTotalPoint();
            for (String scoreType : scoreTypesOf(award.getGender())) {
                TeamExtra extra = map.computeIfAbsent(teamKey(award.getUnitId(), award.getGroupId(), scoreType),
                        key -> new TeamExtra());
                if (relay) {
                    extra.relayPointSum = extra.relayPointSum.add(totalPoint);
                }
                if (source.getEventId() != null) {
                    extra.eventIds.add(source.getEventId());
                }
                if (award.getAthleteId() != null) {
                    extra.athleteIds.add(award.getAthleteId());
                }
            }
        }
        return map;
    }

    /**
     * 团体口径展开：男子 / 女子成绩同时计入对应单性别团体与总团体（与引擎一致）。
     */
    private List<String> scoreTypesOf(String gender) {
        List<String> types = new ArrayList<>(2);
        if (gender != null && StandingCalculator.SCORE_MALE.equalsIgnoreCase(gender)) {
            types.add(StandingCalculator.SCORE_MALE);
        } else if (gender != null && StandingCalculator.SCORE_FEMALE.equalsIgnoreCase(gender)) {
            types.add(StandingCalculator.SCORE_FEMALE);
        }
        types.add(StandingCalculator.SCORE_TOTAL);
        return types;
    }

    private String teamKey(Long unitId, Long groupId, String scoreType) {
        return (unitId == null ? 0L : unitId) + "#" + (groupId == null ? 0L : groupId) + "#" + scoreType;
    }

    // ==================================================================================
    // 五、内部：奖牌榜
    // ==================================================================================

    /**
     * 重建奖牌榜快照：按范围物理删除 → 引擎汇总（决赛 + 有效名次 1/2/3）→ 整批插入。
     *
     * @return 写入行数
     */
    private int rebuildMedalStandings(Long meetId, Long groupId, List<StandingResultRow> allRows,
                                      Map<Long, String> unitNames, String medalSort,
                                      LocalDateTime calcTime, String calcBatch) {
        standingMapper.deleteMedalStandings(meetId, groupId);

        List<ResultRecord> finalRecords = new ArrayList<>();
        for (StandingResultRow row : allRows) {
            if (row.getFinalRound() != null && row.getFinalRound() == 1) {
                finalRecords.add(assembler.toRecord(row, null));
            }
        }
        List<MedalRow> rows = StandingCalculator.medals(finalRecords, unitNames, medalSort);
        if (MEDAL_SORT_POINT_FIRST.equalsIgnoreCase(medalSort)) {
            sortMedalByTotalPoint(rows, meetId, groupId);
        }

        List<MedalStanding> entities = new ArrayList<>(rows.size());
        for (MedalRow row : rows) {
            MedalStanding entity = new MedalStanding();
            entity.setMeetId(meetId);
            entity.setUnitId(row.getUnitId());
            entity.setGroupId(row.getGroupId());
            entity.setGold(row.getGold());
            entity.setSilver(row.getSilver());
            entity.setBronze(row.getBronze());
            entity.setTotalMedal(row.getTotalMedal());
            entity.setSortScore(nullSafe(row.getSortScore()));
            entity.setMedalSort(medalSort);
            entity.setRankNo(row.getRankNo());
            entity.setRankDisplay(String.valueOf(row.getRankNo()));
            entity.setCalcTime(calcTime);
            entity.setCalcBatch(calcBatch);
            entity.setIsPublished(0);
            entities.add(entity);
        }
        if (!entities.isEmpty()) {
            standingMapper.batchInsertMedalStandings(entities);
        }
        return entities.size();
    }

    /**
     * 按团体总分（TOTAL 口径）重排奖牌榜并重赋名次（{@code medal_sort=POINT_FIRST}，R-22）。
     * <p>
     * 引擎对 {@code POINT_FIRST} 按奖牌权重兜底；此处按届次规则改为积分优先。
     */
    private void sortMedalByTotalPoint(List<MedalRow> rows, Long meetId, Long groupId) {
        List<StandingTeamScoreRow> totals = standingMapper.selectTeamScores(meetId, groupId,
                StandingCalculator.SCORE_TOTAL);
        Map<String, BigDecimal> pointMap = new HashMap<>(totals.size());
        for (StandingTeamScoreRow row : totals) {
            pointMap.put(teamKey(row.getUnitId(), row.getGroupId(), StandingCalculator.SCORE_TOTAL),
                    nullSafe(row.getTotalPoint()));
        }
        rows.sort(Comparator
                .comparing((MedalRow row) -> medalPoint(pointMap, row))
                .reversed()
                .thenComparing(MedalRow::getGold, Comparator.reverseOrder())
                .thenComparing(MedalRow::getSilver, Comparator.reverseOrder())
                .thenComparing(MedalRow::getBronze, Comparator.reverseOrder()));
        int rank = 1;
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0 && medalPoint(pointMap, rows.get(i - 1)).compareTo(medalPoint(pointMap, rows.get(i))) != 0) {
                rank = i + 1;
            }
            rows.get(i).setRankNo(rank);
        }
    }

    private BigDecimal medalPoint(Map<String, BigDecimal> pointMap, MedalRow row) {
        return pointMap.getOrDefault(teamKey(row.getUnitId(), row.getGroupId(), StandingCalculator.SCORE_TOTAL),
                BigDecimal.ZERO);
    }

    // ==================================================================================
    // 六、内部：装配与工具
    // ==================================================================================

    /**
     * 名次缺失兜底：同一（项目 × 轮次）内存在未回写名次的有效成绩时，
     * 调用引擎 {@link RankResolver} 现场解析（内存计算，不写库）。
     * <p>
     * 正常情况下名次已由成绩模块的 RankResolver 回写 {@code result.rank_no}，本方法仅为兜底。
     */
    private List<ResultRecord> resolveRankIfNeeded(List<ResultRecord> records, Event event) {
        boolean missing = false;
        for (ResultRecord record : records) {
            if (record != null && record.getRankNo() == null && RankResolver.isValid(record)) {
                missing = true;
                break;
            }
        }
        if (!missing) {
            return records;
        }
        boolean trackAscending = Event.TYPE_TRACK.equalsIgnoreCase(event.getEventType())
                || Event.TYPE_RELAY.equalsIgnoreCase(event.getEventType())
                || (event.getIsRelay() != null && event.getIsRelay() == 1);
        log.debug("项目 {} 存在未回写名次的已审核成绩，按引擎 RankResolver 现场解析", event.getId());
        return RankResolver.resolveRank(records, trackAscending, null);
    }

    private Map<Long, String> loadUnitNameMap(Long meetId) {
        List<StandingUnitRow> units = standingMapper.selectUnitNames(meetId);
        Map<Long, String> map = new HashMap<>(units.size());
        for (StandingUnitRow unit : units) {
            String name = unit.getUnitShortName() != null && !unit.getUnitShortName().isBlank()
                    ? unit.getUnitShortName() : unit.getUnitName();
            map.put(unit.getId(), name);
        }
        return map;
    }

    private BigDecimal totalPointOf(Long meetId, Long groupId) {
        BigDecimal total = BigDecimal.ZERO;
        List<StandingTeamScoreRow> rows = standingMapper.selectTeamScores(meetId, groupId,
                StandingCalculator.SCORE_TOTAL);
        for (StandingTeamScoreRow row : rows) {
            total = total.add(nullSafe(row.getTotalPoint()));
        }
        return total;
    }

    private List<TeamScoreVO> toTeamScoreVOs(Long meetId, Long groupId) {
        List<StandingTeamScoreRow> rows = standingMapper.selectTeamScores(meetId, groupId, null);
        List<TeamScoreVO> list = new ArrayList<>(rows.size());
        for (StandingTeamScoreRow row : rows) {
            list.add(toTeamScoreVO(row));
        }
        return list;
    }

    private List<MedalVO> toMedalVOs(Long meetId, Long groupId) {
        List<StandingMedalRow> rows = standingMapper.selectMedalStandings(meetId, groupId);
        List<MedalVO> list = new ArrayList<>(rows.size());
        for (StandingMedalRow row : rows) {
            list.add(toMedalVO(row));
        }
        return list;
    }

    private StandingRecalculateDTO scopeOf(Long meetId, Long groupId, Long eventId) {
        StandingRecalculateDTO dto = new StandingRecalculateDTO();
        dto.setMeetId(meetId);
        dto.setGroupId(groupId);
        dto.setEventId(eventId);
        return dto;
    }

    private String normalizeScoreType(String scoreType) {
        if (scoreType == null || scoreType.isBlank()) {
            return null;
        }
        String upper = scoreType.trim().toUpperCase();
        if (TeamScore.TYPE_MALE.equals(upper) || TeamScore.TYPE_FEMALE.equals(upper)
                || TeamScore.TYPE_TOTAL.equals(upper)) {
            return upper;
        }
        return null;
    }

    private String scopeText(Long groupId, Long eventId) {
        if (eventId != null) {
            return "项目范围重算（eventId=" + eventId + "）";
        }
        if (groupId != null) {
            return "组别范围重算（groupId=" + groupId + "）";
        }
        return "届次全量重算";
    }

    private Long requireMeet(StandingQuery query) {
        if (query == null || query.getMeetId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        checkMeetContext(query.getMeetId());
        return query.getMeetId();
    }

    /**
     * 多租户校验：请求届次必须与 {@link MeetContext#get()} 一致，否则抛 {@code FORBIDDEN}。
     */
    private void checkMeetContext(Long meetId) {
        Long current = MeetContext.get();
        if (current != null && !current.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该届次数据");
        }
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    public TeamScoreVO toTeamScoreVO(StandingTeamScoreRow row) {
        TeamScoreVO vo = new TeamScoreVO();
        if (row == null) {
            return vo;
        }
        vo.setId(row.getId());
        vo.setMeetId(row.getMeetId());
        vo.setUnitId(row.getUnitId());
        vo.setUnitName(row.getUnitName());
        vo.setUnitShortName(row.getUnitShortName());
        vo.setGroupId(row.getGroupId());
        vo.setGroupName(row.getGroupName());
        vo.setScoreType(row.getScoreType());
        vo.setRankNo(row.getRankNo());
        vo.setRankDisplay(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        vo.setRankPointSum(row.getRankPointSum());
        vo.setBonusPointSum(row.getBonusPointSum());
        vo.setRelayPointSum(row.getRelayPointSum());
        vo.setTotalPoint(row.getTotalPoint());
        vo.setEventCount(row.getEventCount());
        vo.setAthleteCount(row.getAthleteCount());
        vo.setCalcTime(row.getCalcTime());
        vo.setCalcBatch(row.getCalcBatch());
        vo.setIsPublished(row.getIsPublished());
        vo.setRemark(row.getRemark());
        return vo;
    }

    @Override
    public MedalVO toMedalVO(StandingMedalRow row) {
        MedalVO vo = new MedalVO();
        if (row == null) {
            return vo;
        }
        vo.setId(row.getId());
        vo.setMeetId(row.getMeetId());
        vo.setUnitId(row.getUnitId());
        vo.setUnitName(row.getUnitName());
        vo.setUnitShortName(row.getUnitShortName());
        vo.setGroupId(row.getGroupId());
        vo.setGroupName(row.getGroupName());
        vo.setGold(row.getGold());
        vo.setSilver(row.getSilver());
        vo.setBronze(row.getBronze());
        vo.setTotalMedal(row.getTotalMedal());
        vo.setSortScore(row.getSortScore());
        vo.setMedalSort(row.getMedalSort());
        vo.setRankNo(row.getRankNo());
        vo.setRankDisplay(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        vo.setCalcTime(row.getCalcTime());
        vo.setCalcBatch(row.getCalcBatch());
        vo.setIsPublished(row.getIsPublished());
        vo.setRemark(row.getRemark());
        return vo;
    }

    // ==================================================================================
    // 七、打印取数（只读）
    // ==================================================================================

    /**
     * 已审核成绩投影（成绩册打印）。查询前做多租户校验。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID（可空）
     * @param groupId 组别 ID（可空）
     * @param unitId  单位 ID（可空）
     * @return 成绩投影列表
     */
    public List<StandingResultRow> listResultRows(Long meetId, Long eventId, Long groupId, Long unitId) {
        checkMeetContext(meetId);
        return standingMapper.selectApprovedResults(meetId, eventId, groupId, unitId);
    }

    /**
     * 团体总分投影（积分表打印）。
     *
     * @param meetId    届次 ID
     * @param groupId   组别 ID（可空）
     * @param scoreType 团体口径（可空为三档全部）
     * @return 团体总分投影列表
     */
    public List<StandingTeamScoreRow> listTeamScoreRows(Long meetId, Long groupId, String scoreType) {
        checkMeetContext(meetId);
        return standingMapper.selectTeamScores(meetId, groupId, scoreType);
    }

    /**
     * 奖牌榜投影（奖牌榜打印）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @return 奖牌榜投影列表
     */
    public List<StandingMedalRow> listMedalRows(Long meetId, Long groupId) {
        checkMeetContext(meetId);
        return standingMapper.selectMedalStandings(meetId, groupId);
    }

    /**
     * 积分明细投影（积分明细表打印）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @param eventId 项目 ID（可空）
     * @param unitId  单位 ID（可空）
     * @return 积分明细投影列表
     */
    public List<StandingPointRow> listPointRows(Long meetId, Long groupId, Long eventId, Long unitId) {
        checkMeetContext(meetId);
        return standingMapper.selectPointList(meetId, groupId, eventId, unitId);
    }

    /**
     * 届次信息（打印标题与导出文件名）。
     *
     * @param meetId 届次 ID
     * @return 届次投影，不存在时返回 {@code null}
     */
    public StandingMeetRow meetInfo(Long meetId) {
        checkMeetContext(meetId);
        return standingMapper.selectMeetInfo(meetId);
    }

    /**
     * 组别名称映射（成绩册标题补列）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @return 组别 ID → 名称
     */
    public Map<Long, String> groupNames(Long meetId, Long groupId) {
        List<StandingGroupRow> groups = standingMapper.selectGroups(meetId, groupId);
        Map<Long, String> map = new HashMap<>(groups.size());
        for (StandingGroupRow group : groups) {
            map.put(group.getId(), group.getGroupName());
        }
        return map;
    }

    /**
     * 现行纪录清单（成绩册纪录标记）。
     *
     * @param meetId 届次 ID
     * @return 现行纪录投影列表
     */
    public List<StandingRecordRow> recordRows(Long meetId) {
        return standingMapper.selectCurrentRecords(meetId);
    }

    /**
     * 单位简称映射（榜单打印优先展示简称）。
     *
     * @param meetId 届次 ID
     * @return 单位 ID → 显示名
     */
    public Map<Long, String> unitShortNames(Long meetId) {
        return loadUnitNameMap(meetId);
    }

    /**
     * 团队口径附加统计（DDL 要求但引擎输出未覆盖的列）。
     */
    private static final class TeamExtra {

        /** 接力项目得分合计 */
        private BigDecimal relayPointSum = BigDecimal.ZERO;

        /** 得分项目 ID 集合 */
        private final Set<Long> eventIds = new HashSet<>();

        /** 得分运动员 ID 集合 */
        private final Set<Long> athleteIds = new HashSet<>();
    }
}
