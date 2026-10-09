package com.trackmeet.engine.advance;

import com.trackmeet.engine.model.AdvanceResult;
import com.trackmeet.engine.model.AdvanceRule;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.result.RankResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动晋级算法：**每组前 N 名直接晋级 + 其余人员按最好成绩递补至目标人数**。
 *
 * <p>输入的成绩需已由 {@link RankResolver} 写入名次（{@code rankNo}）—— 名次本身即「越好越小」，
 * 因此算法不需要知道径赛/田赛的排序方向。</p>
 *
 * <p>处理要点：</p>
 * <ol>
 *   <li>有效性：无效成绩（DNS/DNF/DSQ/NM/SCRATCH）默认剔除；超风速成绩按 {@code excludeIllegalWind} 剔除；</li>
 *   <li>直接晋级：按组次（{@code heatNo}，为空视为同一组）分组，各组按名次升序取前 {@code groupTopN} 名；</li>
 *   <li>递补：剩余人员按名次升序（即成绩由好到差）递补 {@code bestLoserCount} 人；
 *       若 {@code targetCount > 0} 且直接晋级 + 递补不足目标人数，则继续递补直至达标或人数耗尽；</li>
 *   <li>边界并列：{@code tiePolicy=ALL} 时把与边界成绩并列者全部录取（可超出目标人数），
 *       {@code TRUNCATE} 时严格截断；</li>
 *   <li>输出：直接晋级在前、递补在后（{@code advanceOrder} 从 1 连续编号），并回填来源组次/名次/成绩。</li>
 * </ol>
 *
 * @author TrackMeet
 */
public final class AdvanceCalculator {

    public static final String TYPE_GROUP_TOP = "GROUP_TOP";
    public static final String TYPE_BEST_LOSER = "BEST_LOSER";

    private AdvanceCalculator() {
    }

    /**
     * 计算晋级名单。
     *
     * @param results 同一项目同一轮次的成绩（名次已解析）
     * @param rule    晋级规则（来自 event_round_rule）
     * @return 晋级名单（按出场顺序）
     */
    public static List<AdvanceResult> calculate(List<ResultRecord> results, AdvanceRule rule) {
        AdvanceRule effective = rule == null ? AdvanceRule.builder().build() : rule;
        List<AdvanceResult> advanceList = new ArrayList<>();
        List<ResultRecord> eligible = filterEligible(results, effective);
        if (eligible.isEmpty()) {
            return advanceList;
        }

        List<ResultRecord> byRank = new ArrayList<>(eligible);
        byRank.sort(Comparator
                .comparingInt((ResultRecord record) -> record.getRankNo() == null ? Integer.MAX_VALUE : record.getRankNo())
                .thenComparingLong(record -> record.getRegistrationId() == null ? 0L : record.getRegistrationId()));

        // ① 每组前 N 名
        List<ResultRecord> advanced = new ArrayList<>();
        if (effective.getGroupTopN() > 0) {
            Map<Integer, List<ResultRecord>> heatGroups = groupByHeat(byRank);
            for (Map.Entry<Integer, List<ResultRecord>> entry : heatGroups.entrySet()) {
                List<ResultRecord> group = entry.getValue();
                int limit = Math.min(effective.getGroupTopN(), group.size());
                if (limit > 0 && "ALL".equalsIgnoreCase(effective.getTiePolicy())) {
                    limit = expandForTie(group, limit);
                }
                for (int i = 0; i < limit; i++) {
                    advanced.add(group.get(i));
                }
            }
        }

        // ② 其余人员按最好成绩递补
        List<ResultRecord> remaining = new ArrayList<>();
        for (ResultRecord record : byRank) {
            if (!advanced.contains(record)) {
                remaining.add(record);
            }
        }

        int target = effective.getTargetCount();
        int bestLoserQuota = Math.max(0, effective.getBestLoserCount());
        // 递补人数：至少满足配置的递补名额；配置了目标总人数时需继续递补至达标
        int take = bestLoserQuota;
        if (target > 0) {
            take = Math.max(bestLoserQuota, target - advanced.size());
        }
        take = Math.min(take, remaining.size());
        if ("ALL".equalsIgnoreCase(effective.getTiePolicy())) {
            take = expandForTie(remaining, take);
        }

        List<ResultRecord> losers = new ArrayList<>();
        for (int i = 0; i < Math.min(take, remaining.size()); i++) {
            losers.add(remaining.get(i));
        }

        int order = 1;
        for (ResultRecord record : advanced) {
            advanceList.add(toAdvanceResult(record, TYPE_GROUP_TOP, order++));
        }
        for (ResultRecord record : losers) {
            advanceList.add(toAdvanceResult(record, TYPE_BEST_LOSER, order++));
        }
        return advanceList;
    }

    /**
     * 过滤有效且允许参与晋级的人员，并按「名次（成绩）由好到差」排序。
     */
    public static List<ResultRecord> filterEligible(List<ResultRecord> results, AdvanceRule rule) {
        AdvanceRule effective = rule == null ? AdvanceRule.builder().build() : rule;
        List<ResultRecord> eligible = new ArrayList<>();
        if (results == null) {
            return eligible;
        }
        for (ResultRecord record : results) {
            if (record == null || record.getResultValue() == null) {
                continue;
            }
            String status = record.getStatus();
            boolean validStatus = status == null || status.isEmpty() || RankResolver.STATUS_VALID.equalsIgnoreCase(status);
            if (effective.isExcludeInvalidStatus() && !validStatus) {
                continue;
            }
            if (effective.isExcludeIllegalWind() && Boolean.FALSE.equals(record.getWindLegal())) {
                continue;
            }
            eligible.add(record);
        }
        eligible.sort(Comparator
                .comparingInt((ResultRecord record) -> record.getRankNo() == null ? Integer.MAX_VALUE : record.getRankNo())
                .thenComparingLong(record -> record.getRegistrationId() == null ? 0L : record.getRegistrationId()));
        return eligible;
    }

    /**
     * 按组次分组（组次为空者归入 0 组，保持首次出现顺序）。
     */
    private static Map<Integer, List<ResultRecord>> groupByHeat(List<ResultRecord> sorted) {
        Map<Integer, List<ResultRecord>> groups = new LinkedHashMap<>();
        for (ResultRecord record : sorted) {
            int heatNo = record.getHeatNo() == null ? 0 : record.getHeatNo();
            groups.computeIfAbsent(heatNo, key -> new ArrayList<>()).add(record);
        }
        return groups;
    }

    /**
     * 边界并列扩展：把与第 {@code limit} 位（下标 limit-1）成绩并列的后续人员一并纳入。
     */
    private static int expandForTie(List<ResultRecord> list, int limit) {
        if (limit <= 0 || limit >= list.size()) {
            return Math.min(limit, list.size());
        }
        ResultRecord boundary = list.get(limit - 1);
        int extended = limit;
        while (extended < list.size() && RankResolver.sameValue(boundary, list.get(extended))) {
            extended++;
        }
        return extended;
    }

    private static AdvanceResult toAdvanceResult(ResultRecord record, String type, int order) {
        AdvanceResult advance = new AdvanceResult();
        advance.setRegistrationId(record.getRegistrationId());
        advance.setAthleteId(record.getAthleteId());
        advance.setAthleteName(record.getAthleteName());
        advance.setUnitId(record.getUnitId());
        advance.setSourceHeatNo(record.getHeatNo());
        advance.setSourceRank(record.getRankNo());
        advance.setSourceResultValue(record.getResultValue());
        advance.setAdvanceType(type);
        advance.setAdvanceOrder(order);
        return advance;
    }
}
