package com.trackmeet.engine.fieldorder;

import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.FieldOrderMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 田赛出场顺序编排：随机 / 按报名成绩 / 按单位顺序 / 按号码顺序。
 *
 * <p>结果列表顺序即出场顺序（第 1 位最先试跳/试掷）。按成绩排序时，成绩缺失者排在最后并按号码兜底排序。</p>
 *
 * @author TrackMeet
 */
public final class FieldOrderGenerator {

    private FieldOrderGenerator() {
    }

    /**
     * 生成出场顺序。
     *
     * @param entries       参赛条目
     * @param mode          生成方式（为空按 RANDOM）
     * @param seedAscending 种子排序方向：true 数值越小越好（径赛型），false 越大越好（田赛远度/高度）
     * @param randomSeed    随机种子（保证可复现）
     * @return 出场顺序列表；输入为空时返回空列表
     */
    public static List<AthleteEntry> generate(List<AthleteEntry> entries, FieldOrderMode mode,
                                              boolean seedAscending, long randomSeed) {
        List<AthleteEntry> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return result;
        }
        for (AthleteEntry entry : entries) {
            if (entry != null) {
                result.add(entry);
            }
        }

        FieldOrderMode effectiveMode = mode == null ? FieldOrderMode.RANDOM : mode;
        switch (effectiveMode) {
            case SEED:
                result.sort(seedComparator(seedAscending));
                break;
            case UNIT:
                result.sort(Comparator
                        .comparing((AthleteEntry entry) -> entry.getUnitId() == null ? Long.MAX_VALUE : entry.getUnitId())
                        .thenComparing(entry -> entry.getUnitName() == null ? "" : entry.getUnitName())
                        .thenComparing(entry -> entry.getBibNo() == null ? "" : entry.getBibNo()));
                break;
            case BIB:
                result.sort(Comparator.comparing(entry -> entry.getBibNo() == null ? "" : entry.getBibNo()));
                break;
            case RANDOM:
            default:
                Collections.shuffle(result, new Random(randomSeed));
                break;
        }
        return result;
    }

    /**
     * 成绩排序：有成绩者在前，按方向排序；无成绩者按号码排在最后。
     */
    private static Comparator<AthleteEntry> seedComparator(boolean seedAscending) {
        Comparator<AthleteEntry> comparator = (first, second) -> {
            boolean firstHasSeed = first.getSeedValue() != null;
            boolean secondHasSeed = second.getSeedValue() != null;
            if (firstHasSeed && secondHasSeed) {
                int compared = first.getSeedValue().compareTo(second.getSeedValue());
                return seedAscending ? compared : -compared;
            }
            if (firstHasSeed) {
                return -1;
            }
            if (secondHasSeed) {
                return 1;
            }
            return 0;
        };
        return comparator.thenComparing(entry -> entry.getBibNo() == null ? "" : entry.getBibNo());
    }
}
