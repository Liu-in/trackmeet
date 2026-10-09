package com.trackmeet.engine.lane;

import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.LaneAssignment;
import com.trackmeet.engine.model.LaneOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 道次分配算法单元测试：8/6 道顺序、按成绩/抽签/蛇形、超员与边界场景。
 *
 * @author TrackMeet
 */
class LaneAllocatorTest {

    @Test
    @DisplayName("默认道次顺序：8 道 [4,5,3,6,2,7,1,8]、6 道 [3,4,2,5,1,6]")
    void shouldReturnDefaultLaneOrder() {
        assertThat(LaneAllocator.defaultLaneOrder(8)).containsExactly(4, 5, 3, 6, 2, 7, 1, 8);
        assertThat(LaneAllocator.defaultLaneOrder(6)).containsExactly(3, 4, 2, 5, 1, 6);
        assertThat(LaneAllocator.defaultLaneOrder(4)).containsExactly(3, 4, 2, 1);
        assertThat(LaneAllocator.defaultLaneOrder(9)).containsExactly(4, 5, 3, 6, 2, 7, 1, 8, 9);
        // 非法/空道数按 8 道兜底
        assertThat(LaneAllocator.defaultLaneOrder(0)).containsExactly(4, 5, 3, 6, 2, 7, 1, 8);
    }

    @Test
    @DisplayName("按成绩分配：最优成绩占 4 道，其次 5 道、3 道")
    void shouldAssignBySeedOrder() {
        List<AthleteEntry> members = buildMembers(8);

        List<LaneAssignment> assignments = LaneAllocator.assign(members, LaneOptions.builder()
                .laneCount(8)
                .mode("SEED")
                .build());

        assertThat(assignments).hasSize(8);
        assertThat(assignments.get(0).getLaneNo()).isEqualTo(4);
        assertThat(assignments.get(1).getLaneNo()).isEqualTo(5);
        assertThat(assignments.get(2).getLaneNo()).isEqualTo(3);
        assertThat(assignments.get(7).getLaneNo()).isEqualTo(8);
        assertThat(assignments.get(0).getRegistrationId()).isEqualTo(1L);
        assertThat(assignments.get(0).getAssignMode()).isEqualTo("SEED");
        assertThat(assignments).extracting(LaneAssignment::getLaneNo).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("自定义道次顺序：按 [1,2,3] 分配")
    void shouldRespectCustomLaneOrder() {
        List<LaneAssignment> assignments = LaneAllocator.assign(buildMembers(3), LaneOptions.builder()
                .laneCount(3)
                .laneOrder(List.of(1, 2, 3))
                .mode("ORDER")
                .build());

        assertThat(assignments).extracting(LaneAssignment::getLaneNo).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("6 道场地：超出 6 道的道次被过滤，按过滤后的顺序分配")
    void shouldFilterLanesBeyondCapacity() {
        List<LaneAssignment> assignments = LaneAllocator.assign(buildMembers(8), LaneOptions.builder()
                .laneCount(6)
                .laneOrder(LaneAllocator.defaultLaneOrder(8))
                .mode("SEED")
                .build());

        assertThat(assignments).hasSize(6);
        // 8 道顺序 [4,5,3,6,2,7,1,8] 过滤 >6 的道次后为 [4,5,3,6,2,1]
        assertThat(assignments).extracting(LaneAssignment::getLaneNo)
                .containsExactly(4, 5, 3, 6, 2, 1);
        assertThat(assignments).allMatch(item -> item.getLaneNo() <= 6);
    }

    @Test
    @DisplayName("道次顺序覆盖不足时自动补齐：6 道顺序用于 8 道场地")
    void shouldAppendMissingLanes() {
        List<LaneAssignment> assignments = LaneAllocator.assign(buildMembers(8), LaneOptions.builder()
                .laneCount(8)
                .laneOrder(LaneAllocator.defaultLaneOrder(6))
                .mode("SEED")
                .build());

        assertThat(assignments).hasSize(8);
        assertThat(assignments).extracting(LaneAssignment::getLaneNo).doesNotHaveDuplicates();
        // 前 6 个沿用 6 道顺序，后 2 个补齐为 7、8
        assertThat(assignments.subList(0, 6)).extracting(LaneAssignment::getLaneNo).containsExactly(3, 4, 2, 5, 1, 6);
        assertThat(assignments.subList(6, 8)).extracting(LaneAssignment::getLaneNo).containsExactly(7, 8);
    }

    @Test
    @DisplayName("超员：成员多于道次时仅分配 laneCount 个道次")
    void shouldAssignOnlyAvailableLanes() {
        List<LaneAssignment> assignments = LaneAllocator.assign(buildMembers(12), LaneOptions.builder()
                .laneCount(8)
                .build());

        assertThat(assignments).hasSize(8);
        assertThat(assignments).extracting(LaneAssignment::getLaneNo).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("抽签分配：同一种子结果一致，且道次不重复")
    void shouldDrawDeterministically() {
        LaneOptions options = LaneOptions.builder().laneCount(6).mode("DRAW").randomSeed(20251018L).build();

        List<LaneAssignment> first = LaneAllocator.assign(buildMembers(6), options);
        List<LaneAssignment> second = LaneAllocator.assign(buildMembers(6), options);

        assertThat(first).extracting(LaneAssignment::getRegistrationId)
                .isEqualTo(second.stream().map(LaneAssignment::getRegistrationId).toList());
        assertThat(first).extracting(LaneAssignment::getLaneNo).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("折半交错（SERPENT）：1、n/2+1、2、n/2+2… 顺序占道")
    void shouldAssignSerpentine() {
        List<LaneAssignment> assignments = LaneAllocator.assign(buildMembers(6), LaneOptions.builder()
                .laneCount(6)
                .mode("SERPENT")
                .build());

        // 6 人折半交错顺序：1,4,2,5,3,6；6 道顺序 [3,4,2,5,1,6] → 道次 3,5,4,1,2,6
        assertThat(assignments).extracting(LaneAssignment::getRegistrationId)
                .containsExactly(1L, 4L, 2L, 5L, 3L, 6L);
        assertThat(assignments).extracting(LaneAssignment::getLaneNo)
                .containsExactly(3, 5, 4, 1, 2, 6);
    }

    @Test
    @DisplayName("边界：空输入返回空结果")
    void shouldHandleEmptyInput() {
        assertThat(LaneAllocator.assign(new ArrayList<>(), LaneOptions.builder().laneCount(8).build())).isEmpty();
        assertThat(LaneAllocator.assign(null, null)).isEmpty();
    }

    private List<AthleteEntry> buildMembers(int count) {
        List<AthleteEntry> members = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            members.add(AthleteEntry.builder()
                    .registrationId((long) i)
                    .athleteId(1000L + i)
                    .athleteName("运动员" + i)
                    .unitId((long) (i % 3 + 1))
                    .bibNo("B-" + String.format("%03d", i))
                    .seedValue(new BigDecimal(String.format("%.2f", 11.0 + i * 0.1)))
                    .build());
        }
        return members;
    }
}
