package com.trackmeet.engine.allround;

import com.trackmeet.engine.model.AllroundScoreRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 全能计分表查表算法：按「成绩落点区间」取分。
 *
 * <p>计分表来自 {@code allround_score_table}（全局参照表，不按届次隔离）：每一行描述一个成绩区间
 * {@code [performanceMin, performanceMax]} 及其得分。查表时区间可正序或倒序书写，算法内部会归一化。</p>
 *
 * @author TrackMeet
 */
public final class AllroundScoreLookup {

    private AllroundScoreLookup() {
    }

    /**
     * 查询单项得分。
     *
     * @param table       计分表（同一 tableCode 的全部区间行）
     * @param performance 实际成绩（径赛秒、田赛米/厘米，与表中 unit 一致）
     * @param tableCode   计分表编码（为空则忽略编码过滤）
     * @return 命中区间的得分；未命中返回 {@link BigDecimal#ZERO}
     */
    public static BigDecimal lookup(List<AllroundScoreRow> table, BigDecimal performance, String tableCode) {
        if (table == null || table.isEmpty() || performance == null) {
            return BigDecimal.ZERO;
        }
        for (AllroundScoreRow row : table) {
            if (row == null || row.getPointValue() == null) {
                continue;
            }
            if (tableCode != null && !tableCode.isEmpty()
                    && row.getTableCode() != null && !tableCode.equalsIgnoreCase(row.getTableCode())) {
                continue;
            }
            BigDecimal lower = min(row.getPerformanceMin(), row.getPerformanceMax());
            BigDecimal upper = max(row.getPerformanceMin(), row.getPerformanceMax());
            if (lower == null || upper == null) {
                continue;
            }
            if (performance.compareTo(lower) >= 0 && performance.compareTo(upper) <= 0) {
                return row.getPointValue();
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 查询单项得分（带性别与子类型过滤，便于一次传入整表）。
     */
    public static BigDecimal lookup(List<AllroundScoreRow> table, BigDecimal performance,
                                    String tableCode, String gender, String subEventType) {
        List<AllroundScoreRow> filtered = new ArrayList<>();
        if (table != null) {
            for (AllroundScoreRow row : table) {
                if (row == null) {
                    continue;
                }
                if (gender != null && row.getGender() != null && !gender.equalsIgnoreCase(row.getGender())) {
                    continue;
                }
                if (subEventType != null && row.getSubEventType() != null
                        && !subEventType.equalsIgnoreCase(row.getSubEventType())) {
                    continue;
                }
                filtered.add(row);
            }
        }
        return lookup(filtered, performance, tableCode);
    }

    /**
     * 全能总分：各单项得分求和（未完成项按 0 计，调用方自行判断是否弃权）。
     */
    public static BigDecimal total(List<BigDecimal> scores) {
        BigDecimal sum = BigDecimal.ZERO;
        if (scores == null) {
            return sum;
        }
        for (BigDecimal score : scores) {
            if (score != null) {
                sum = sum.add(score);
            }
        }
        return sum;
    }

    private static BigDecimal min(BigDecimal first, BigDecimal second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static BigDecimal max(BigDecimal first, BigDecimal second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.compareTo(second) >= 0 ? first : second;
    }
}
