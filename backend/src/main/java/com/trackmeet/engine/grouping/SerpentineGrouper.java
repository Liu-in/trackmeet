package com.trackmeet.engine.grouping;

import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.GroupingOptions;
import com.trackmeet.engine.model.HeatResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 径赛分组算法：按报名成绩排序 → 蛇形（S 型）分配到各组 → 同单位避开 → 人数均衡。
 *
 * <p>算法要点：</p>
 * <ol>
 *   <li><b>排序</b>：有种子成绩者按成绩排序（径赛越小越好、田赛越大越好，由 {@code seedAscending} 控制），
 *       无成绩者按号码（或随机）排在最后，保证有成绩者先被蛇形分配；</li>
 *   <li><b>蛇形分配</b>：第 1 轮按键号正序放入各组，第 2 轮逆序，交替进行 —— 使各组实力均衡，
 *       同时每组人数自然保持差 ≤ 1；</li>
 *   <li><b>同单位避开</b>：对组内单位重复的成员，与其它组中「换过来不产生新重复、且换过去不产生新重复」
 *       的成员交换，最多迭代 {@value #MAX_REPAIR_ROUNDS} 轮；</li>
 *   <li><b>人数均衡</b>：若最多与最少相差 > 1，从最多组移动一名成员到最少组（尽量不破坏同单位避开）。</li>
 * </ol>
 *
 * <p>结果中组内成员顺序即道次优先级：越靠前越应分配到最优道次（见 {@code LaneAllocator}）。</p>
 *
 * @author TrackMeet
 */
public final class SerpentineGrouper {

    /** 同单位避开的最大修复轮数 */
    private static final int MAX_REPAIR_ROUNDS = 3;

    /** 均衡保护的迭代上限（防止极端数据下的死循环） */
    private static final int MAX_BALANCE_LOOPS = 1000;

    private SerpentineGrouper() {
    }

    /**
     * 执行分组。
     *
     * @param entries 参赛条目（可为空）
     * @param options 分组参数（为空时使用默认值）
     * @return 分组结果，组号从 1 开始；输入为空时返回空列表
     */
    public static List<HeatResult> group(List<AthleteEntry> entries, GroupingOptions options) {
        GroupingOptions opts = options == null ? GroupingOptions.builder().build() : options;
        List<HeatResult> results = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return results;
        }

        List<AthleteEntry> sorted = sortEntries(entries, opts);
        int heatCount = resolveHeatCount(sorted.size(), opts);
        List<List<AthleteEntry>> buckets = snakeDistribute(sorted, heatCount);

        if (opts.isAvoidSameUnit()) {
            for (int round = 0; round < MAX_REPAIR_ROUNDS; round++) {
                if (!repairSameUnit(buckets)) {
                    break;
                }
            }
        }
        balance(buckets);

        for (int i = 0; i < buckets.size(); i++) {
            HeatResult heat = new HeatResult();
            heat.setHeatNo(i + 1);
            heat.setMembers(buckets.get(i));
            results.add(heat);
        }
        return results;
    }

    /**
     * 排序：有种子成绩者优先，成绩方向由参数控制；无成绩者按号码或随机。
     */
    private static List<AthleteEntry> sortEntries(List<AthleteEntry> entries, GroupingOptions opts) {
        List<AthleteEntry> seeded = new ArrayList<>();
        List<AthleteEntry> unseeded = new ArrayList<>();
        for (AthleteEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (entry.getSeedValue() != null) {
                seeded.add(entry);
            } else {
                unseeded.add(entry);
            }
        }

        Comparator<AthleteEntry> comparator = Comparator.comparing(AthleteEntry::getSeedValue);
        if (!opts.isSeedAscending()) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparingLong(entry -> entry.getRegistrationId() == null ? 0L : entry.getRegistrationId());
        seeded.sort(comparator);

        if (opts.isRandomizeUnseeded()) {
            Collections.shuffle(unseeded, new Random(opts.getRandomSeed()));
        } else {
            unseeded.sort(Comparator.comparing(entry -> entry.getBibNo() == null ? "" : entry.getBibNo()));
        }

        List<AthleteEntry> sorted = new ArrayList<>(seeded.size() + unseeded.size());
        sorted.addAll(seeded);
        sorted.addAll(unseeded);
        return sorted;
    }

    /**
     * 计算组数：优先显式组数，其次按每组人数上限推算，最后兜底 1 组；组数不超过总人数。
     */
    private static int resolveHeatCount(int participantCount, GroupingOptions opts) {
        int heatCount;
        if (opts.getHeatCount() > 0) {
            heatCount = opts.getHeatCount();
        } else if (opts.getHeatSize() > 0) {
            heatCount = (int) Math.ceil((double) participantCount / opts.getHeatSize());
        } else {
            heatCount = 1;
        }
        if (heatCount < 1) {
            heatCount = 1;
        }
        return Math.min(heatCount, participantCount);
    }

    /**
     * 蛇形（S 型）分配：第一轮正序、第二轮逆序，交替进行。
     */
    private static List<List<AthleteEntry>> snakeDistribute(List<AthleteEntry> sorted, int heatCount) {
        List<List<AthleteEntry>> buckets = new ArrayList<>(heatCount);
        for (int i = 0; i < heatCount; i++) {
            buckets.add(new ArrayList<>());
        }
        int index = 0;
        boolean forward = true;
        while (index < sorted.size()) {
            if (forward) {
                for (int heat = 0; heat < heatCount && index < sorted.size(); heat++) {
                    buckets.get(heat).add(sorted.get(index++));
                }
            } else {
                for (int heat = heatCount - 1; heat >= 0 && index < sorted.size(); heat--) {
                    buckets.get(heat).add(sorted.get(index++));
                }
            }
            forward = !forward;
        }
        return buckets;
    }

    /**
     * 同单位避开：交换「双方都不会产生新重复」的两名成员。
     *
     * @return 是否发生了交换
     */
    private static boolean repairSameUnit(List<List<AthleteEntry>> buckets) {
        boolean changed = false;
        for (int heatIndex = 0; heatIndex < buckets.size(); heatIndex++) {
            List<AthleteEntry> bucket = buckets.get(heatIndex);
            Map<Long, Integer> counts = countUnits(bucket);
            for (int position = 0; position < bucket.size(); position++) {
                AthleteEntry member = bucket.get(position);
                Long unitId = member.getUnitId();
                if (unitId == null || counts.getOrDefault(unitId, 0) <= 1) {
                    continue;
                }
                SwapCandidate candidate = findSwapCandidate(buckets, heatIndex, member, counts);
                if (candidate == null) {
                    continue;
                }
                buckets.get(candidate.heatIndex).set(candidate.position, member);
                bucket.set(position, candidate.partner);
                counts = countUnits(bucket);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 寻找交换对象：对方单位在本组不存在（换入不新增重复），且本组成员单位在对方组不存在（换出不新增重复）。
     */
    private static SwapCandidate findSwapCandidate(List<List<AthleteEntry>> buckets, int heatIndex,
                                                   AthleteEntry member, Map<Long, Integer> currentCounts) {
        for (int otherIndex = 0; otherIndex < buckets.size(); otherIndex++) {
            if (otherIndex == heatIndex) {
                continue;
            }
            List<AthleteEntry> other = buckets.get(otherIndex);
            Map<Long, Integer> otherCounts = countUnits(other);
            if (otherCounts.getOrDefault(member.getUnitId(), 0) > 0) {
                continue;
            }
            for (int position = 0; position < other.size(); position++) {
                AthleteEntry partner = other.get(position);
                Long partnerUnit = partner.getUnitId();
                if (Objects.equals(partnerUnit, member.getUnitId())) {
                    continue;
                }
                if (partnerUnit != null && currentCounts.getOrDefault(partnerUnit, 0) > 0) {
                    continue;
                }
                return new SwapCandidate(otherIndex, position, partner);
            }
        }
        return null;
    }

    /**
     * 人数均衡：最多组与最少组相差 > 1 时转移一名成员（优先选择不会造成目标组单位重复者）。
     */
    private static void balance(List<List<AthleteEntry>> buckets) {
        if (buckets.size() <= 1) {
            return;
        }
        int loops = 0;
        while (loops++ < MAX_BALANCE_LOOPS) {
            List<AthleteEntry> largest = buckets.get(0);
            List<AthleteEntry> smallest = buckets.get(0);
            for (List<AthleteEntry> bucket : buckets) {
                if (bucket.size() > largest.size()) {
                    largest = bucket;
                }
                if (bucket.size() < smallest.size()) {
                    smallest = bucket;
                }
            }
            if (largest == smallest || largest.size() - smallest.size() <= 1) {
                break;
            }
            Map<Long, Integer> smallestUnits = countUnits(smallest);
            AthleteEntry candidate = null;
            for (int i = largest.size() - 1; i >= 0; i--) {
                AthleteEntry entry = largest.get(i);
                if (entry.getUnitId() == null || smallestUnits.getOrDefault(entry.getUnitId(), 0) == 0) {
                    candidate = entry;
                    break;
                }
            }
            if (candidate == null && !largest.isEmpty()) {
                candidate = largest.get(largest.size() - 1);
            }
            if (candidate == null) {
                break;
            }
            largest.remove(candidate);
            smallest.add(candidate);
        }
    }

    private static Map<Long, Integer> countUnits(List<AthleteEntry> bucket) {
        Map<Long, Integer> counts = new HashMap<>();
        for (AthleteEntry entry : bucket) {
            if (entry != null && entry.getUnitId() != null) {
                counts.merge(entry.getUnitId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * 交换位置信息。
     */
    private static class SwapCandidate {

        private final int heatIndex;
        private final int position;
        private final AthleteEntry partner;

        SwapCandidate(int heatIndex, int position, AthleteEntry partner) {
            this.heatIndex = heatIndex;
            this.position = position;
            this.partner = partner;
        }
    }
}
