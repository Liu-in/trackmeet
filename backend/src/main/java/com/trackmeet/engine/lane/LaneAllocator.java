package com.trackmeet.engine.lane;

import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.LaneAssignment;
import com.trackmeet.engine.model.LaneOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 道次分配算法：按可配置的道次优先顺序把组内成员分配到道次。
 *
 * <p>默认顺序（国际田联常用）：</p>
 * <ul>
 *   <li>8 道：{@code 4,5,3,6,2,7,1,8}（中间道次最优）</li>
 *   <li>6 道：{@code 3,4,2,5,1,6}</li>
 *   <li>4 道：{@code 3,4,2,1}；9/10 道：在 8 道顺序后追加外侧道次</li>
 * </ul>
 *
 * <p>分配方式：</p>
 * <ul>
 *   <li>{@code SEED}（默认）：输入顺序即成绩顺序，最优成绩占最优道次；</li>
 *   <li>{@code ORDER}：按输入顺序（通常为报名/号码顺序）；</li>
 *   <li>{@code DRAW}：抽签（受 {@code randomSeed} 控制，可复现）；</li>
 *   <li>{@code SERPENT}：按输入顺序「折半交错」（1、n/2+1、2、n/2+2…），用于把两个来源的半场
 *       选手交替排入，避免同组选手集中占用相邻道次。</li>
 * </ul>
 *
 * <p>当传入的道次顺序覆盖不足 {@code laneCount} 时，会按编号升序补齐缺失道次（追加在末尾）；
 * 当成员数多于可用道次时，仅前 {@code laneCount} 名成员获得道次（其余为替补，由调用方处理）。</p>
 *
 * @author TrackMeet
 */
public final class LaneAllocator {

    /** 8 道优先顺序 */
    private static final List<Integer> LANE_ORDER_8 = List.of(4, 5, 3, 6, 2, 7, 1, 8);
    /** 6 道优先顺序 */
    private static final List<Integer> LANE_ORDER_6 = List.of(3, 4, 2, 5, 1, 6);
    /** 4 道优先顺序 */
    private static final List<Integer> LANE_ORDER_4 = List.of(3, 4, 2, 1);
    /** 9 道优先顺序 */
    private static final List<Integer> LANE_ORDER_9 = List.of(4, 5, 3, 6, 2, 7, 1, 8, 9);
    /** 10 道优先顺序 */
    private static final List<Integer> LANE_ORDER_10 = List.of(4, 5, 3, 6, 2, 7, 1, 8, 9, 10);

    /** 默认道数 */
    private static final int DEFAULT_LANE_COUNT = 8;

    private LaneAllocator() {
    }

    /**
     * 返回指定道数的默认道次优先顺序（从最优到最差）。
     */
    public static List<Integer> defaultLaneOrder(int laneCount) {
        int count = laneCount > 0 ? laneCount : DEFAULT_LANE_COUNT;
        switch (count) {
            case 6:
                return new ArrayList<>(LANE_ORDER_6);
            case 4:
                return new ArrayList<>(LANE_ORDER_4);
            case 9:
                return new ArrayList<>(LANE_ORDER_9);
            case 10:
                return new ArrayList<>(LANE_ORDER_10);
            case 8:
            default:
                return new ArrayList<>(LANE_ORDER_8);
        }
    }

    /**
     * 执行道次分配。
     *
     * @param orderedMembers 组内成员（顺序敏感：靠前者优先获得最优道次）
     * @param options        参数（道次顺序、道数、方式、随机种子）
     * @return 道次分配结果；输入为空时返回空列表
     */
    public static List<LaneAssignment> assign(List<AthleteEntry> orderedMembers, LaneOptions options) {
        LaneOptions opts = options == null ? LaneOptions.builder().build() : options;
        List<LaneAssignment> assignments = new ArrayList<>();
        if (orderedMembers == null || orderedMembers.isEmpty()) {
            return assignments;
        }

        int laneCount = opts.getLaneCount() > 0 ? opts.getLaneCount() : DEFAULT_LANE_COUNT;
        List<Integer> laneOrder = (opts.getLaneOrder() == null || opts.getLaneOrder().isEmpty())
                ? defaultLaneOrder(laneCount)
                : new ArrayList<>(opts.getLaneOrder());

        // 过滤超出可用道数的道次，并保证不超过 laneCount 个
        List<Integer> available = new ArrayList<>();
        for (Integer lane : laneOrder) {
            if (lane != null && lane >= 1 && lane <= laneCount && !available.contains(lane)) {
                available.add(lane);
            }
        }
        if (available.isEmpty()) {
            available = defaultLaneOrder(laneCount);
        }
        // 道次顺序覆盖不足时补齐缺失道次（追加在末尾，优先级最低）
        if (available.size() < laneCount) {
            for (int lane = 1; lane <= laneCount && available.size() < laneCount; lane++) {
                if (!available.contains(lane)) {
                    available.add(lane);
                }
            }
        }

        List<AthleteEntry> ordered = new ArrayList<>(orderedMembers);
        String mode = opts.getMode() == null ? "SEED" : opts.getMode().toUpperCase();
        if ("DRAW".equals(mode)) {
            Collections.shuffle(ordered, new Random(opts.getRandomSeed()));
        } else if ("SERPENT".equals(mode)) {
            ordered = serpentineOrder(ordered);
        }

        int limit = Math.min(ordered.size(), available.size());
        for (int index = 0; index < limit; index++) {
            AthleteEntry entry = ordered.get(index);
            LaneAssignment assignment = new LaneAssignment();
            assignment.setLaneNo(available.get(index));
            assignment.setRegistrationId(entry.getRegistrationId());
            assignment.setAthleteId(entry.getAthleteId());
            assignment.setAssignMode(mode);
            assignments.add(assignment);
        }
        return assignments;
    }

    /**
     * 折半交错：1、n/2+1、2、n/2+2…，用于均衡两个来源半场的道次分布。
     */
    private static List<AthleteEntry> serpentineOrder(List<AthleteEntry> ordered) {
        int half = (ordered.size() + 1) / 2;
        List<AthleteEntry> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < half; index++) {
            result.add(ordered.get(index));
            int mirror = index + half;
            if (mirror < ordered.size()) {
                result.add(ordered.get(mirror));
            }
        }
        return result;
    }
}
