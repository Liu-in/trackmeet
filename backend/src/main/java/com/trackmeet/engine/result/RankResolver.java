package com.trackmeet.engine.result;

import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.TieBreakRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 名次解析与平局处理算法（纯函数）。
 *
 * <p>规则：</p>
 * <ol>
 *   <li><b>有效性</b>：仅 {@code status=VALID} 且成绩数值非空者参与排名；DNS/DNF/DSQ/NM/PASS/SCRATCH 不计名次；</li>
 *   <li><b>排序方向</b>：径赛成绩越小越好（{@code trackAscending=true}），田赛越大越好（false）；</li>
 *   <li><b>平局规则</b>（{@link TieBreakRule#getOrder()}，顺序比较）：
 *     <ul>
 *       <li>{@code SECOND_BEST}：比较次优成绩（田赛多次试跳的第二好成绩）；</li>
 *       <li>{@code BEST_ATTEMPT_ROUND}：比较最好成绩出现的轮次，越早越优；</li>
 *       <li>{@code DRAW}：抽签（算法内不作区分，由调用方决定顺序）；</li>
 *     </ul>
 *   </li>
 *   <li><b>并列</b>：仍无法区分时，{@code allowParallelRank=true} 则名次并列且后续名次顺延
 *       （1、1、3 制）；否则按报名 ID 强制区分。</li>
 * </ol>
 *
 * @author TrackMeet
 */
public final class RankResolver {

    public static final String STATUS_VALID = "VALID";

    private RankResolver() {
    }

    /**
     * 解析名次并写回 {@code rankNo/rankDisplay}。
     *
     * @param results        同一项目同一轮次的成绩（顺序不敏感）
     * @param trackAscending 径赛为 true（越小越好），田赛为 false（越大越好）
     * @param tieBreak       平局规则（为空取默认规则）
     * @return 已排序并写入名次的列表（有效成绩在前按名次、无效成绩在后）
     */
    public static List<ResultRecord> resolveRank(List<ResultRecord> results, boolean trackAscending, TieBreakRule tieBreak) {
        List<ResultRecord> all = new ArrayList<>();
        if (results != null) {
            for (ResultRecord record : results) {
                if (record != null) {
                    all.add(record);
                }
            }
        }
        List<ResultRecord> valid = new ArrayList<>();
        List<ResultRecord> invalid = new ArrayList<>();
        for (ResultRecord record : all) {
            if (isValid(record)) {
                valid.add(record);
            } else {
                record.setRankNo(null);
                record.setRankDisplay(null);
                invalid.add(record);
            }
        }

        TieBreakRule rule = tieBreak == null ? TieBreakRule.defaultRule() : tieBreak;
        Comparator<ResultRecord> comparator = buildComparator(trackAscending, rule);
        if (!rule.isAllowParallelRank()) {
            comparator = comparator.thenComparingLong(record -> record.getRegistrationId() == null ? 0L : record.getRegistrationId());
        }
        valid.sort(comparator);

        // 并列检测（相邻成绩按规则比较为 0 即并列）
        boolean[] tied = new boolean[valid.size()];
        for (int i = 1; i < valid.size(); i++) {
            if (comparator.compare(valid.get(i - 1), valid.get(i)) == 0) {
                tied[i] = true;
                tied[i - 1] = true;
            }
        }

        int currentRank = 1;
        for (int i = 0; i < valid.size(); i++) {
            if (i > 0 && comparator.compare(valid.get(i - 1), valid.get(i)) != 0) {
                currentRank = i + 1;
            }
            ResultRecord record = valid.get(i);
            record.setRankNo(currentRank);
            record.setRankDisplay(tied[i] ? currentRank + "（并列）" : String.valueOf(currentRank));
        }

        invalid.sort(Comparator
                .comparing((ResultRecord record) -> record.getStatus() == null ? "" : record.getStatus())
                .thenComparingLong(record -> record.getRegistrationId() == null ? 0L : record.getRegistrationId()));

        List<ResultRecord> ordered = new ArrayList<>(valid.size() + invalid.size());
        ordered.addAll(valid);
        ordered.addAll(invalid);
        return ordered;
    }

    /**
     * 是否参与名次（有效状态且成绩非空）。
     */
    public static boolean isValid(ResultRecord record) {
        if (record == null || record.getResultValue() == null) {
            return false;
        }
        String status = record.getStatus();
        return status == null || status.isEmpty() || STATUS_VALID.equalsIgnoreCase(status);
    }

    private static Comparator<ResultRecord> buildComparator(boolean trackAscending, TieBreakRule rule) {
        return (first, second) -> {
            int byValue = trackAscending
                    ? first.getResultValue().compareTo(second.getResultValue())
                    : second.getResultValue().compareTo(first.getResultValue());
            if (byValue != 0) {
                return byValue;
            }
            if (rule.getOrder() != null) {
                for (String item : rule.getOrder()) {
                    int compared = compareByRule(first, second, item, trackAscending);
                    if (compared != 0) {
                        return compared;
                    }
                }
            }
            return 0;
        };
    }

    private static int compareByRule(ResultRecord first, ResultRecord second, String rule, boolean trackAscending) {
        if (rule == null) {
            return 0;
        }
        switch (rule.toUpperCase()) {
            case "SECOND_BEST":
                return compareSecondBest(first, second, trackAscending);
            case "BEST_ATTEMPT_ROUND":
                return Integer.compare(bestAttemptIndex(first.getAttempts()), bestAttemptIndex(second.getAttempts()));
            case "DRAW":
            default:
                return 0;
        }
    }

    /**
     * 次优成绩比较：无次优成绩者排后。
     */
    private static int compareSecondBest(ResultRecord first, ResultRecord second, boolean trackAscending) {
        BigDecimal firstSecond = secondBest(first.getAttempts());
        BigDecimal secondSecond = secondBest(second.getAttempts());
        if (firstSecond == null && secondSecond == null) {
            return 0;
        }
        if (firstSecond == null) {
            return 1;
        }
        if (secondSecond == null) {
            return -1;
        }
        return trackAscending ? firstSecond.compareTo(secondSecond) : secondSecond.compareTo(firstSecond);
    }

    private static BigDecimal secondBest(List<BigDecimal> attempts) {
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
        values.sort(Comparator.reverseOrder());
        return values.get(1);
    }

    /**
     * 最好成绩出现的轮次下标（无成绩返回 {@link Integer#MAX_VALUE}）。
     */
    private static int bestAttemptIndex(List<BigDecimal> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return Integer.MAX_VALUE;
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
        return bestIndex < 0 ? Integer.MAX_VALUE : bestIndex;
    }

    /**
     * 判定两人是否并列（对外暴露，便于服务层展示并列说明）。
     */
    public static boolean isTied(ResultRecord first, ResultRecord second, boolean trackAscending, TieBreakRule rule) {
        if (first == null || second == null || first.getResultValue() == null || second.getResultValue() == null) {
            return false;
        }
        return buildComparator(trackAscending, rule == null ? TieBreakRule.defaultRule() : rule).compare(first, second) == 0;
    }

    /**
     * 成绩是否相等（用于晋级边界并列判断）。
     */
    public static boolean sameValue(ResultRecord first, ResultRecord second) {
        return first != null && second != null && first.getResultValue() != null
                && second.getResultValue() != null
                && Objects.equals(0, first.getResultValue().compareTo(second.getResultValue()));
    }
}
