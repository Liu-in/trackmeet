package com.trackmeet.engine.grouping;

import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.GroupingOptions;
import com.trackmeet.engine.model.HeatResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 径赛分组算法单元测试：蛇形分配、人数均衡、同单位避开、种子方向与边界场景。
 *
 * @author TrackMeet
 */
class SerpentineGrouperTest {

    // ------------------------------------------------------------------
    // 蛇形分配
    // ------------------------------------------------------------------

    @Test
    @DisplayName("蛇形分配：12 人分 3 组，成绩最优者分散到各组（S 型顺序可复现）")
    void shouldDistributeSerpentine() {
        List<AthleteEntry> entries = buildSeeded(12);
        List<HeatResult> heats = SerpentineGrouper.group(entries, GroupingOptions.builder()
                .heatCount(3)
                .avoidSameUnit(false)
                .build());

        assertThat(heats).hasSize(3);
        // 蛇形顺序：第 1 轮 1,2,3 → 第 2 轮 3,2,1 → 第 3 轮 1,2,3 → 第 4 轮 3,2,1
        assertThat(ids(heats.get(0))).containsExactly(1L, 6L, 7L, 12L);
        assertThat(ids(heats.get(1))).containsExactly(2L, 5L, 8L, 11L);
        assertThat(ids(heats.get(2))).containsExactly(3L, 4L, 9L, 10L);
    }

    @Test
    @DisplayName("人数均衡：13 人分 3 组，各组人数差不超过 1")
    void shouldKeepSizesBalanced() {
        List<HeatResult> heats = SerpentineGrouper.group(buildSeeded(13), GroupingOptions.builder()
                .heatCount(3)
                .build());

        assertThat(heats).hasSize(3);
        int min = heats.stream().mapToInt(HeatResult::size).min().orElse(0);
        int max = heats.stream().mapToInt(HeatResult::size).max().orElse(0);
        assertThat(max - min).isLessThanOrEqualTo(1);
        assertThat(heats.stream().mapToInt(HeatResult::size).sum()).isEqualTo(13);
    }

    @Test
    @DisplayName("按每组人数上限推算组数：25 人、每组 8 人 → 4 组")
    void shouldResolveHeatCountByHeatSize() {
        List<HeatResult> heats = SerpentineGrouper.group(buildSeeded(25), GroupingOptions.builder()
                .heatSize(8)
                .build());

        assertThat(heats).hasSize(4);
        assertThat(heats.get(0).size()).isBetween(6, 7);
    }

    // ------------------------------------------------------------------
    // 同单位避开
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同单位避开：4 个单位各 2 人分 2 组，每组单位互不重复")
    void shouldSpreadSameUnit() {
        List<AthleteEntry> entries = new ArrayList<>();
        long registrationId = 1L;
        for (long unitId = 1; unitId <= 4; unitId++) {
            for (int i = 0; i < 2; i++) {
                entries.add(entry(registrationId++, unitId, new BigDecimal(String.valueOf(12.0 + registrationId))));
            }
        }

        List<HeatResult> heats = SerpentineGrouper.group(entries, GroupingOptions.builder()
                .heatCount(2)
                .avoidSameUnit(true)
                .build());

        assertThat(heats).hasSize(2);
        for (HeatResult heat : heats) {
            assertThat(distinctUnitCount(heat)).isEqualTo(heat.size());
        }
    }

    @Test
    @DisplayName("同单位避开：单位人数多于组数时按上限均摊（不出现单组集中）")
    void shouldLimitConcentrationWhenImpossibleToSplit() {
        List<AthleteEntry> entries = new ArrayList<>();
        long registrationId = 1L;
        for (int i = 0; i < 6; i++) {
            entries.add(entry(registrationId++, 1L, new BigDecimal(String.valueOf(11.0 + i))));
        }
        for (int i = 0; i < 3; i++) {
            entries.add(entry(registrationId++, 2L, new BigDecimal(String.valueOf(13.0 + i))));
        }

        List<HeatResult> heats = SerpentineGrouper.group(entries, GroupingOptions.builder()
                .heatCount(3)
                .avoidSameUnit(true)
                .build());

        assertThat(heats).hasSize(3);
        // 6 人单位分 3 组，理想为每组 2 人；断言不会出现 3 人以上集中
        for (HeatResult heat : heats) {
            assertThat(maxUnitCount(heat)).isLessThanOrEqualTo(2);
        }
    }

    // ------------------------------------------------------------------
    // 种子方向与边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("种子方向：seedAscending=false（田赛）时最大值优先")
    void shouldRespectSeedDirection() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entry(1L, 1L, new BigDecimal("6.50")));
        entries.add(entry(2L, 1L, new BigDecimal("7.80")));
        entries.add(entry(3L, 1L, new BigDecimal("7.10")));

        List<HeatResult> heats = SerpentineGrouper.group(entries, GroupingOptions.builder()
                .heatCount(1)
                .seedAscending(false)
                .build());

        assertThat(ids(heats.get(0))).containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("无种子成绩者排在最后（按号码兜底排序）")
    void shouldPutUnseededLast() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entryWithBib(1L, 1L, null, "A-003"));
        entries.add(entryWithBib(2L, 1L, new BigDecimal("12.50"), "A-001"));
        entries.add(entryWithBib(3L, 1L, null, "A-002"));

        List<HeatResult> heats = SerpentineGrouper.group(entries, GroupingOptions.builder()
                .heatCount(1)
                .build());

        assertThat(ids(heats.get(0))).containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("随机种子可复现：相同种子两次分组结果一致")
    void shouldBeReproducibleWithSameRandomSeed() {
        List<AthleteEntry> entries = new ArrayList<>();
        long registrationId = 1L;
        for (int i = 0; i < 10; i++) {
            entries.add(entry(registrationId++, 1L, null));
        }
        GroupingOptions options = GroupingOptions.builder()
                .heatCount(3)
                .randomizeUnseeded(true)
                .randomSeed(20251018L)
                .avoidSameUnit(false)
                .build();

        List<HeatResult> first = SerpentineGrouper.group(entries, options);
        List<HeatResult> second = SerpentineGrouper.group(entries, options);

        assertThat(ids(first.get(0))).isEqualTo(ids(second.get(0)));
        assertThat(ids(first.get(1))).isEqualTo(ids(second.get(1)));
    }

    @Test
    @DisplayName("边界：空输入返回空结果；组数超过人数时每组 1 人")
    void shouldHandleBoundaryCases() {
        assertThat(SerpentineGrouper.group(new ArrayList<>(), GroupingOptions.builder().heatCount(3).build())).isEmpty();
        assertThat(SerpentineGrouper.group(null, null)).isEmpty();

        List<HeatResult> heats = SerpentineGrouper.group(buildSeeded(3), GroupingOptions.builder()
                .heatCount(5)
                .build());
        assertThat(heats).hasSize(3);
        assertThat(heats).allMatch(heat -> heat.size() == 1);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private List<AthleteEntry> buildSeeded(int count) {
        List<AthleteEntry> entries = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            entries.add(entry((long) i, (long) (i % 4 + 1), new BigDecimal(String.format("%.2f", 11.0 + i * 0.1))));
        }
        return entries;
    }

    private AthleteEntry entry(Long registrationId, Long unitId, BigDecimal seedValue) {
        return AthleteEntry.builder()
                .registrationId(registrationId)
                .athleteId(1000L + registrationId)
                .athleteName("运动员" + registrationId)
                .unitId(unitId)
                .unitName("单位" + unitId)
                .bibNo("B-" + String.format("%03d", registrationId))
                .seedValue(seedValue)
                .seedResult(seedValue == null ? null : seedValue.toPlainString())
                .build();
    }

    private AthleteEntry entryWithBib(Long registrationId, Long unitId, BigDecimal seedValue, String bibNo) {
        return AthleteEntry.builder()
                .registrationId(registrationId)
                .athleteId(1000L + registrationId)
                .athleteName("运动员" + registrationId)
                .unitId(unitId)
                .bibNo(bibNo)
                .seedValue(seedValue)
                .build();
    }

    private List<Long> ids(HeatResult heat) {
        List<Long> ids = new ArrayList<>();
        for (AthleteEntry entry : heat.getMembers()) {
            ids.add(entry.getRegistrationId());
        }
        return ids;
    }

    private int distinctUnitCount(HeatResult heat) {
        Map<Long, Integer> counts = unitCounts(heat);
        return counts.size();
    }

    private int maxUnitCount(HeatResult heat) {
        return unitCounts(heat).values().stream().max(Integer::compareTo).orElse(0);
    }

    private Map<Long, Integer> unitCounts(HeatResult heat) {
        Map<Long, Integer> counts = new HashMap<>();
        for (AthleteEntry entry : heat.getMembers()) {
            counts.merge(entry.getUnitId(), 1, Integer::sum);
        }
        return counts;
    }
}
