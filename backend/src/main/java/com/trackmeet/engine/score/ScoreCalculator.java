package com.trackmeet.engine.score;

import com.trackmeet.engine.model.BonusRuleRow;
import com.trackmeet.engine.model.PointAward;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.ScoreRuleRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分计算算法：**名次分 + 破纪录加分 →（接力双倍）→ 单项积分**。
 *
 * <p>并列计分策略（取积分表中该名次行配置的 {@code tiePolicy}，缺省 AVERAGE）：</p>
 * <ul>
 *   <li>{@code AVERAGE}：并列者平分其占用的名次分值之和（如并列第 1、占用 1、2 名次 → (9+7)/2 = 8）；</li>
 *   <li>{@code FULL}：并列者各取所取名次的分值（双双 9 分）；</li>
 *   <li>{@code ORDER}：按列表顺序依次取 1、2…名次分值（不打平）。</li>
 * </ul>
 *
 * <p>破纪录加分：按 {@code bonusType=RECORD} 且 {@code recordType} 匹配的规则计算；
 * {@code stackable=true} 时多个层级加分叠加，否则取最高分。</p>
 *
 * <p>接力双倍：{@code relayDouble=true} 且成绩为接力项目时，乘以 {@code bonusType=RELAY} 规则的 {@code factor}（默认 2.00）。</p>
 *
 * @author TrackMeet
 */
public final class ScoreCalculator {

    /** 并列默认策略 */
    public static final String TIE_AVERAGE = "AVERAGE";
    public static final String TIE_FULL = "FULL";
    public static final String TIE_ORDER = "ORDER";

    /** 接力默认倍数 */
    public static final BigDecimal DEFAULT_RELAY_FACTOR = new BigDecimal("2.00");

    private ScoreCalculator() {
    }

    /**
     * 计算积分明细。
     *
     * @param ranked     已解析名次的成绩（无效成绩与无名次者自动跳过）
     * @param scoreRule  名次分表（score_rule_item）
     * @param bonusRules 加分规则（bonus_rule）
     * @param relayDouble 接力是否双倍计分
     * @return 积分明细（按名次升序）
     */
    public static List<PointAward> calculate(List<ResultRecord> ranked, List<ScoreRuleRow> scoreRule,
                                             List<BonusRuleRow> bonusRules, boolean relayDouble) {
        return calculate(ranked, scoreRule, bonusRules, relayDouble, null);
    }

    /**
     * 计算积分明细（按轮次过滤计分规则）。
     *
     * @param roundCode 当前轮次码；非空时会剔除 `effectiveRoundCode` 与之不符的规则行，
     *                  避免同一项目多轮次重复计分（为空则不过滤，保持向后兼容）
     */
    public static List<PointAward> calculate(List<ResultRecord> ranked, List<ScoreRuleRow> scoreRule,
                                             List<BonusRuleRow> bonusRules, boolean relayDouble, String roundCode) {
        List<PointAward> awards = new ArrayList<>();
        if (ranked == null || ranked.isEmpty()) {
            return awards;
        }
        Map<Integer, ScoreRuleRow> ruleByRank = indexScoreRule(scoreRule, roundCode);
        List<BonusRuleRow> bonuses = bonusRules == null ? new ArrayList<>() : bonusRules;

        // 按名次分组（并列名次即同组）
        Map<Integer, List<ResultRecord>> byRank = new LinkedHashMap<>();
        for (ResultRecord record : ranked) {
            if (record == null || record.getRankNo() == null) {
                continue;
            }
            byRank.computeIfAbsent(record.getRankNo(), key -> new ArrayList<>()).add(record);
        }
        List<Integer> ranks = new ArrayList<>(byRank.keySet());
        ranks.sort(Comparator.naturalOrder());

        for (Integer rank : ranks) {
            List<ResultRecord> group = byRank.get(rank);
            if (group == null || group.isEmpty()) {
                continue;
            }
            BigDecimal rankPoint = resolveRankPoint(rank, group.size(), ruleByRank, group);
            String tiePolicy = resolveTiePolicy(rank, ruleByRank);
            for (int index = 0; index < group.size(); index++) {
                ResultRecord record = group.get(index);
                BigDecimal point = rankPoint;
                if (TIE_ORDER.equalsIgnoreCase(tiePolicy) && group.size() > 1) {
                    point = pointOf(ruleByRank, rank + index);
                }
                PointAward award = new PointAward();
                award.setRegistrationId(record.getRegistrationId());
                award.setAthleteId(record.getAthleteId());
                award.setUnitId(record.getUnitId());
                award.setGroupId(record.getGroupId());
                award.setGender(record.getGender());
                award.setRankNo(rank);
                award.setRankPoint(point);

                BonusResult bonus = resolveRecordBonus(record, bonuses);
                award.setBonusPoint(bonus.point);
                award.setAppliedBonusCodes(bonus.codes);
                award.setRelayFactor(resolveRelayFactor(record, bonuses, relayDouble));
                award.setTotalPoint(award.getRankPoint().add(award.getBonusPoint())
                        .multiply(award.getRelayFactor()).setScale(2, RoundingMode.HALF_UP));
                awards.add(award);
            }
        }
        return awards;
    }

    /**
     * 名次分（并列按策略处理）。
     */
    private static BigDecimal resolveRankPoint(int rank, int groupSize, Map<Integer, ScoreRuleRow> ruleByRank,
                                               List<ResultRecord> group) {
        String tiePolicy = resolveTiePolicy(rank, ruleByRank);
        if (groupSize <= 1 || TIE_FULL.equalsIgnoreCase(tiePolicy)) {
            return pointOf(ruleByRank, rank);
        }
        if (TIE_ORDER.equalsIgnoreCase(tiePolicy)) {
            return pointOf(ruleByRank, rank);
        }
        // AVERAGE：占用 rank..rank+groupSize-1 的名次分值平均
        BigDecimal sum = BigDecimal.ZERO;
        int counted = 0;
        for (int offset = 0; offset < groupSize; offset++) {
            ScoreRuleRow row = ruleByRank.get(rank + offset);
            if (row != null && row.getPointValue() != null) {
                sum = sum.add(row.getPointValue());
                counted++;
            }
        }
        if (counted == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP);
    }

    private static String resolveTiePolicy(int rank, Map<Integer, ScoreRuleRow> ruleByRank) {
        ScoreRuleRow row = ruleByRank.get(rank);
        if (row != null && row.getTiePolicy() != null && !row.getTiePolicy().isEmpty()) {
            return row.getTiePolicy();
        }
        return TIE_AVERAGE;
    }

    private static BigDecimal pointOf(Map<Integer, ScoreRuleRow> ruleByRank, int rank) {
        ScoreRuleRow row = ruleByRank.get(rank);
        if (row == null || row.getPointValue() == null) {
            return BigDecimal.ZERO;
        }
        return row.getPointValue();
    }

    /**
     * 破纪录加分：可叠加时求和，否则取最高分。
     */
    private static BonusResult resolveRecordBonus(ResultRecord record, List<BonusRuleRow> bonuses) {
        BonusResult result = new BonusResult();
        if (!record.isRecordCandidate()) {
            return result;
        }
        List<BonusRuleRow> matched = new ArrayList<>();
        for (BonusRuleRow rule : bonuses) {
            if (rule == null || rule.getBonusPoint() == null) {
                continue;
            }
            if (!"RECORD".equalsIgnoreCase(rule.getBonusType())) {
                continue;
            }
            if (!matchesRecordType(rule.getRecordType(), record.getRecordType())) {
                continue;
            }
            matched.add(rule);
        }
        if (matched.isEmpty()) {
            return result;
        }
        matched.sort(Comparator.comparingInt(BonusRuleRow::getPriority));
        boolean stackable = matched.stream().anyMatch(BonusRuleRow::isStackable);
        if (stackable) {
            BigDecimal sum = BigDecimal.ZERO;
            for (BonusRuleRow rule : matched) {
                sum = sum.add(rule.getBonusPoint());
                result.codes.add(rule.getRuleCode());
            }
            result.point = sum;
        } else {
            BonusRuleRow best = matched.get(0);
            for (BonusRuleRow rule : matched) {
                if (rule.getBonusPoint().compareTo(best.getBonusPoint()) > 0) {
                    best = rule;
                }
            }
            result.point = best.getBonusPoint();
            result.codes.add(best.getRuleCode());
        }
        return result;
    }

    private static boolean matchesRecordType(String ruleRecordType, String recordType) {
        if (ruleRecordType == null || ruleRecordType.isEmpty()) {
            return true;
        }
        return ruleRecordType.equalsIgnoreCase(recordType);
    }

    /**
     * 接力倍数。
     */
    private static BigDecimal resolveRelayFactor(ResultRecord record, List<BonusRuleRow> bonuses, boolean relayDouble) {
        if (!relayDouble || !record.isRelay()) {
            return BigDecimal.ONE;
        }
        BonusRuleRow best = null;
        for (BonusRuleRow rule : bonuses) {
            if (rule == null || !"RELAY".equalsIgnoreCase(rule.getBonusType())) {
                continue;
            }
            if (best == null || rule.getPriority() < best.getPriority()) {
                best = rule;
            }
        }
        if (best == null || best.getFactor() == null) {
            return DEFAULT_RELAY_FACTOR;
        }
        return best.getFactor();
    }

    private static Map<Integer, ScoreRuleRow> indexScoreRule(List<ScoreRuleRow> scoreRule, String roundCode) {
        Map<Integer, ScoreRuleRow> map = new HashMap<>();
        if (scoreRule != null) {
            for (ScoreRuleRow row : scoreRule) {
                if (row == null) {
                    continue;
                }
                String effectiveRound = row.getEffectiveRoundCode();
                if (roundCode != null && effectiveRound != null && !effectiveRound.isEmpty()
                        && !effectiveRound.equalsIgnoreCase(roundCode)) {
                    continue;
                }
                map.put(row.getRankNo(), row);
            }
        }
        return map;
    }

    /**
     * 加分解析结果（内部使用）。
     */
    private static class BonusResult {

        private BigDecimal point = BigDecimal.ZERO;
        private final List<String> codes = new ArrayList<>();
    }
}
