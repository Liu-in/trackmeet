package com.trackmeet.engine.fieldorder;

import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.FieldOrderMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 田赛出场顺序算法单元测试：随机/按成绩/按单位/按号码、无成绩兜底与可复现性。
 *
 * @author TrackMeet
 */
class FieldOrderGeneratorTest {

    @Test
    @DisplayName("按号码排序（BIB）：按号码字符串升序")
    void shouldOrderByBib() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entry(1L, 1L, new BigDecimal("6.50"), "B-003"));
        entries.add(entry(2L, 1L, new BigDecimal("7.80"), "B-001"));
        entries.add(entry(3L, 1L, new BigDecimal("7.10"), "B-002"));

        List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, FieldOrderMode.BIB, false, 1L);

        assertThat(ids(ordered)).containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("按成绩排序（田赛：远度越大越先出场）")
    void shouldOrderBySeedDescendingForField() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entry(1L, 1L, new BigDecimal("6.50"), "B-001"));
        entries.add(entry(2L, 1L, new BigDecimal("7.80"), "B-002"));
        entries.add(entry(3L, 1L, new BigDecimal("7.10"), "B-003"));

        List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, FieldOrderMode.SEED, false, 1L);

        assertThat(ids(ordered)).containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("按成绩排序（径赛句式：数值越小越先）")
    void shouldOrderBySeedAscending() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entry(1L, 1L, new BigDecimal("12.50"), "B-001"));
        entries.add(entry(2L, 1L, new BigDecimal("11.20"), "B-002"));
        entries.add(entry(3L, 1L, new BigDecimal("11.90"), "B-003"));

        List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, FieldOrderMode.SEED, true, 1L);

        assertThat(ids(ordered)).containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("无成绩者排在最后并按号码兜底")
    void shouldPutEntriesWithoutSeedLast() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entry(1L, 1L, null, "B-003"));
        entries.add(entry(2L, 1L, new BigDecimal("7.00"), "B-001"));
        entries.add(entry(3L, 1L, null, "B-002"));

        List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, FieldOrderMode.SEED, false, 1L);

        assertThat(ids(ordered)).containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("按单位顺序：同单位连续出场，单位间按 ID 升序")
    void shouldOrderByUnit() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entry(1L, 3L, new BigDecimal("7.00"), "B-001"));
        entries.add(entry(2L, 1L, new BigDecimal("7.10"), "B-002"));
        entries.add(entry(3L, 3L, new BigDecimal("7.20"), "B-003"));
        entries.add(entry(4L, 2L, new BigDecimal("7.30"), "B-004"));

        List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, FieldOrderMode.UNIT, false, 1L);

        assertThat(ids(ordered)).containsExactly(2L, 4L, 1L, 3L);
    }

    @Test
    @DisplayName("随机抽签：同一种子结果一致，且元素不丢失")
    void shouldBeReproducibleForRandomMode() {
        List<AthleteEntry> entries = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            entries.add(entry(i, 1L, new BigDecimal(String.valueOf(6.0 + i)), "B-" + i));
        }

        List<AthleteEntry> first = FieldOrderGenerator.generate(entries, FieldOrderMode.RANDOM, false, 20251018L);
        List<AthleteEntry> second = FieldOrderGenerator.generate(entries, FieldOrderMode.RANDOM, false, 20251018L);

        assertThat(ids(first)).isEqualTo(ids(second));
        assertThat(first).hasSize(8);
    }

    @Test
    @DisplayName("默认模式：mode 为空时按随机处理，不抛异常")
    void shouldDefaultToRandom() {
        List<AthleteEntry> entries = new ArrayList<>();
        entries.add(entry(1L, 1L, new BigDecimal("7.00"), "B-001"));
        entries.add(entry(2L, 1L, new BigDecimal("7.10"), "B-002"));

        assertThat(FieldOrderGenerator.generate(entries, null, false, 1L)).hasSize(2);
    }

    @Test
    @DisplayName("边界：空输入返回空列表")
    void shouldHandleEmptyInput() {
        assertThat(FieldOrderGenerator.generate(new ArrayList<>(), FieldOrderMode.SEED, false, 1L)).isEmpty();
        assertThat(FieldOrderGenerator.generate(null, FieldOrderMode.BIB, false, 1L)).isEmpty();
    }

    private AthleteEntry entry(Long registrationId, Long unitId, BigDecimal seedValue, String bibNo) {
        return AthleteEntry.builder()
                .registrationId(registrationId)
                .athleteId(1000L + registrationId)
                .athleteName("运动员" + registrationId)
                .unitId(unitId)
                .unitName("单位" + unitId)
                .bibNo(bibNo)
                .seedValue(seedValue)
                .build();
    }

    private List<Long> ids(List<AthleteEntry> entries) {
        List<Long> ids = new ArrayList<>();
        for (AthleteEntry entry : entries) {
            ids.add(entry.getRegistrationId());
        }
        return ids;
    }
}
