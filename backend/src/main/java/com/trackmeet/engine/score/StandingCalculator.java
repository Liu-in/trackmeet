package com.trackmeet.engine.score;

import com.trackmeet.engine.model.MedalRow;
import com.trackmeet.engine.model.PointAward;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.TeamScoreRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 团体总分与奖牌榜统计算法。
 *
 * <p><b>团体总分</b>：按 单位 × 组别 × 口径 汇总积分明细，口径三档 ——
 * {@code MALE}（男子团体，只计男子项目）、{@code FEMALE}（女子团体）、{@code TOTAL}（总团体，全部计入）。
 * 同分并列，后续名次顺延。</p>
 *
 * <p><b>奖牌榜</b>：仅决赛有效成绩的名次 1/2/3 计金/银/铜；排序默认「金牌优先」，
 * 也可切换为「奖牌总数优先」或「积分优先」。同分并列。</p>
 *
 * @author TrackMeet
 */
public final class StandingCalculator {

    public static final String SCORE_MALE = "MALE";
    public static final String SCORE_FEMALE = "FEMALE";
    public static final String SCORE_TOTAL = "TOTAL";

    /** 排序规则：金牌优先（默认） */
    public static final String MEDAL_SORT_GOLD_FIRST = "GOLD_FIRST";
    /** 排序规则：奖牌总数优先 */
    public static final String MEDAL_SORT_TOTAL_FIRST = "TOTAL_FIRST";
    /** 排序规则：积分优先（等价于金牌优先，积分由调用方另表展示） */
    public static final String MEDAL_SORT_POINT_FIRST = "POINT_FIRST";

    private static final BigDecimal GOLD_WEIGHT = new BigDecimal("10000");
    private static final BigDecimal SILVER_WEIGHT = new BigDecimal("100");
    private static final BigDecimal BRONZE_WEIGHT = BigDecimal.ONE;

    private StandingCalculator() {
    }

    /**
     * 团体总分汇总。
     *
     * @param awards    单项积分明细
     * @param unitNames 单位 ID → 名称（可空）
     * @return 团体总分行（按组别、口径、名次排列）
     */
    public static List<TeamScoreRow> teamScores(List<PointAward> awards, Map<Long, String> unitNames) {
        List<TeamScoreRow> rows = new ArrayList<>();
        if (awards == null || awards.isEmpty()) {
            return rows;
        }
        Map<String, Accumulator> accumulatorMap = new LinkedHashMap<>();
        for (PointAward award : awards) {
            if (award == null || award.getUnitId() == null) {
                continue;
            }
            for (String scoreType : scoreTypesOf(award.getGender())) {
                String key = award.getUnitId() + "#" + (award.getGroupId() == null ? 0L : award.getGroupId()) + "#" + scoreType;
                Accumulator accumulator = accumulatorMap.computeIfAbsent(key, k -> new Accumulator(
                        award.getUnitId(), award.getGroupId(), scoreType));
                accumulator.add(award);
            }
        }
        for (Accumulator accumulator : accumulatorMap.values()) {
            rows.add(accumulator.toRow(unitNames));
        }
        rows.sort(Comparator
                .comparing((TeamScoreRow row) -> row.getGroupId() == null ? 0L : row.getGroupId())
                .thenComparing(TeamScoreRow::getScoreType)
                .thenComparing(TeamScoreRow::getTotalPoint, Comparator.reverseOrder())
                .thenComparing(row -> row.getUnitId() == null ? 0L : row.getUnitId()));
        assignTeamRank(rows);
        return rows;
    }

    /**
     * 名次赋值：**按 组别 × 口径 分区**排名，同分并列、后续顺延。
     */
    private static void assignTeamRank(List<TeamScoreRow> rows) {
        int partitionStart = 0;
        for (int i = 0; i < rows.size(); i++) {
            TeamScoreRow row = rows.get(i);
            if (i > 0 && !samePartition(rows.get(i - 1), row)) {
                partitionStart = i;
                row.setRankNo(1);
                continue;
            }
            if (i == 0) {
                row.setRankNo(1);
                continue;
            }
            TeamScoreRow previous = rows.get(i - 1);
            if (previous.getTotalPoint().compareTo(row.getTotalPoint()) == 0) {
                row.setRankNo(previous.getRankNo());
            } else {
                row.setRankNo(i - partitionStart + 1);
            }
        }
    }

    private static boolean samePartition(TeamScoreRow first, TeamScoreRow second) {
        Long firstGroup = first.getGroupId();
        Long secondGroup = second.getGroupId();
        boolean sameGroup = firstGroup == null ? secondGroup == null : firstGroup.equals(secondGroup);
        String firstType = first.getScoreType();
        String secondType = second.getScoreType();
        boolean sameType = firstType == null ? secondType == null : firstType.equals(secondType);
        return sameGroup && sameType;
    }

    private static List<String> scoreTypesOf(String gender) {
        List<String> types = new ArrayList<>(2);
        if (gender != null && SCORE_MALE.equalsIgnoreCase(gender)) {
            types.add(SCORE_MALE);
        } else if (gender != null && SCORE_FEMALE.equalsIgnoreCase(gender)) {
            types.add(SCORE_FEMALE);
        }
        types.add(SCORE_TOTAL);
        return types;
    }

    /**
     * 奖牌榜（默认金牌优先）。
     */
    public static List<MedalRow> medals(List<ResultRecord> finalResults, Map<Long, String> unitNames) {
        return medals(finalResults, unitNames, MEDAL_SORT_GOLD_FIRST);
    }

    /**
     * 奖牌榜（指定排序规则）。
     *
     * @param finalResults 决赛成绩（名次已解析）
     * @param unitNames    单位 ID → 名称
     * @param medalSort    GOLD_FIRST / TOTAL_FIRST / POINT_FIRST
     */
    public static List<MedalRow> medals(List<ResultRecord> finalResults, Map<Long, String> unitNames, String medalSort) {
        List<MedalRow> rows = new ArrayList<>();
        if (finalResults == null || finalResults.isEmpty()) {
            return rows;
        }
        Map<String, MedalRow> map = new LinkedHashMap<>();
        for (ResultRecord record : finalResults) {
            if (record == null || record.getUnitId() == null || record.getRankNo() == null) {
                continue;
            }
            String status = record.getStatus();
            boolean valid = status == null || status.isEmpty() || "VALID".equalsIgnoreCase(status);
            if (!valid) {
                continue;
            }
            int rank = record.getRankNo();
            if (rank < 1 || rank > 3) {
                continue;
            }
            String key = record.getUnitId() + "#" + (record.getGroupId() == null ? 0L : record.getGroupId());
            MedalRow row = map.computeIfAbsent(key, k -> {
                MedalRow created = new MedalRow();
                created.setUnitId(record.getUnitId());
                created.setGroupId(record.getGroupId());
                created.setUnitName(unitNames == null ? null : unitNames.get(record.getUnitId()));
                return created;
            });
            if (rank == 1) {
                row.setGold(row.getGold() + 1);
            } else if (rank == 2) {
                row.setSilver(row.getSilver() + 1);
            } else {
                row.setBronze(row.getBronze() + 1);
            }
        }
        for (MedalRow row : map.values()) {
            row.setTotalMedal(row.getGold() + row.getSilver() + row.getBronze());
            row.setSortScore(sortScore(row, medalSort));
            rows.add(row);
        }
        Comparator<MedalRow> comparator = MEDAL_SORT_TOTAL_FIRST.equalsIgnoreCase(medalSort)
                ? Comparator.comparingInt(MedalRow::getTotalMedal)
                : Comparator.comparing(MedalRow::getSortScore);
        rows.sort(comparator.reversed()
                .thenComparing(MedalRow::getGold, Comparator.reverseOrder())
                .thenComparing(MedalRow::getSilver, Comparator.reverseOrder())
                .thenComparing(MedalRow::getBronze, Comparator.reverseOrder())
                .thenComparing(row -> row.getUnitId() == null ? 0L : row.getUnitId()));
        assignMedalRank(rows, comparator);
        return rows;
    }

    private static BigDecimal sortScore(MedalRow row, String medalSort) {
        if (MEDAL_SORT_TOTAL_FIRST.equalsIgnoreCase(medalSort)) {
            return BigDecimal.valueOf(row.getTotalMedal());
        }
        return BigDecimal.valueOf(row.getGold()).multiply(GOLD_WEIGHT)
                .add(BigDecimal.valueOf(row.getSilver()).multiply(SILVER_WEIGHT))
                .add(BigDecimal.valueOf(row.getBronze()).multiply(BRONZE_WEIGHT));
    }

    /**
     * 奖牌名次赋值：同分并列、后续顺延（1、1、3 制）。
     */
    private static void assignMedalRank(List<MedalRow> rows, Comparator<MedalRow> comparator) {
        int rank = 1;
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0 && comparator.compare(rows.get(i - 1), rows.get(i)) != 0) {
                rank = i + 1;
            }
            rows.get(i).setRankNo(rank);
        }
    }

    /**
     * 单位累计器。
     */
    private static class Accumulator {

        private final Long unitId;
        private final Long groupId;
        private final String scoreType;
        private BigDecimal rankPointSum = BigDecimal.ZERO;
        private BigDecimal bonusPointSum = BigDecimal.ZERO;

        Accumulator(Long unitId, Long groupId, String scoreType) {
            this.unitId = unitId;
            this.groupId = groupId;
            this.scoreType = scoreType;
        }

        void add(PointAward award) {
            if (award.getRankPoint() != null) {
                rankPointSum = rankPointSum.add(award.getRankPoint());
            }
            if (award.getBonusPoint() != null) {
                bonusPointSum = bonusPointSum.add(award.getBonusPoint());
            }
        }

        TeamScoreRow toRow(Map<Long, String> unitNames) {
            TeamScoreRow row = new TeamScoreRow();
            row.setUnitId(unitId);
            row.setUnitName(unitNames == null ? null : unitNames.get(unitId));
            row.setGroupId(groupId);
            row.setScoreType(scoreType);
            row.setRankPointSum(rankPointSum);
            row.setBonusPointSum(bonusPointSum);
            row.setTotalPoint(rankPointSum.add(bonusPointSum));
            return row;
        }
    }
}
