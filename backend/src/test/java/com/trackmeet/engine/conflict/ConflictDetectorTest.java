package com.trackmeet.engine.conflict;

import com.trackmeet.engine.model.ConflictItem;
import com.trackmeet.engine.model.ConflictOptions;
import com.trackmeet.engine.model.ScheduledItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 冲突检测单元测试：运动员兼项、场地冲突、裁判冲突、时间间隔不足与开关控制。
 *
 * @author TrackMeet
 */
class ConflictDetectorTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2025, 10, 18, 8, 0);

    @Test
    @DisplayName("时间重叠：同一场地 + 同一运动员 + 同一裁判 → 三类 ERROR 冲突各 1 条")
    void shouldDetectOverlapConflicts() {
        ScheduledItem first = item(1L, 1L, 1L, BASE, 30, 101L, 201L, "100米 预赛");
        ScheduledItem second = item(2L, 1L, 1L, BASE.plusMinutes(20), 30, 101L, 201L, "跳远 决赛");

        List<ConflictItem> conflicts = ConflictDetector.detect(Arrays.asList(first, second),
                ConflictOptions.builder().build());

        assertThat(conflicts).hasSize(3);
        assertThat(conflicts).allMatch(item -> ConflictDetector.LEVEL_ERROR.equals(item.getLevel()));
        assertThat(conflicts).extracting(ConflictItem::getConflictType)
                .containsExactlyInAnyOrder(
                        ConflictDetector.TYPE_VENUE,
                        ConflictDetector.TYPE_ATHLETE,
                        ConflictDetector.TYPE_REFEREE);
        ConflictItem athleteConflict = conflicts.stream()
                .filter(item -> ConflictDetector.TYPE_ATHLETE.equals(item.getConflictType()))
                .findFirst()
                .orElseThrow();
        assertThat(athleteConflict.getAthleteId()).isEqualTo(101L);
        assertThat(athleteConflict.getOverlapMinutes()).isEqualTo(10);
        assertThat(athleteConflict.getMessage()).contains("兼项冲突");
    }

    @Test
    @DisplayName("时间不重叠但间隔不足：兼项间隔 30 分钟、实际 15 分钟 → INTERVAL WARN")
    void shouldDetectInsufficientInterval() {
        ScheduledItem first = item(1L, 1L, null, BASE, 30, 101L, null, "100米 预赛");
        ScheduledItem second = item(2L, 1L, null, BASE.plusMinutes(45), 30, 101L, null, "200米 预赛");

        List<ConflictItem> conflicts = ConflictDetector.detect(Arrays.asList(first, second),
                ConflictOptions.builder().athleteIntervalMin(30).build());

        assertThat(conflicts).hasSize(1);
        ConflictItem conflict = conflicts.get(0);
        assertThat(conflict.getConflictType()).isEqualTo(ConflictDetector.TYPE_INTERVAL);
        assertThat(conflict.getLevel()).isEqualTo(ConflictDetector.LEVEL_WARN);
        assertThat(conflict.getIntervalMinutes()).isEqualTo(15L);
        assertThat(conflict.getRequiredIntervalMin()).isEqualTo(30L);
        assertThat(conflict.getMessage()).contains("间隔不足");
    }

    @Test
    @DisplayName("间隔满足要求时不产生冲突")
    void shouldNotReportConflictWhenIntervalIsEnough() {
        ScheduledItem first = item(1L, 1L, null, BASE, 30, 101L, null, "100米 预赛");
        ScheduledItem second = item(2L, 1L, null, BASE.plusMinutes(70), 30, 101L, null, "200米 预赛");

        List<ConflictItem> conflicts = ConflictDetector.detect(Arrays.asList(first, second),
                ConflictOptions.builder().athleteIntervalMin(30).build());

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("不同运动员、不同场地、不同裁判且无交集时不产生冲突")
    void shouldNotReportConflictForUnrelatedItems() {
        ScheduledItem first = item(1L, 1L, 1L, BASE, 30, 101L, 201L, "100米 预赛");
        ScheduledItem second = item(2L, 2L, 2L, BASE.plusMinutes(10), 30, 102L, 202L, "跳远 决赛");

        List<ConflictItem> conflicts = ConflictDetector.detect(Arrays.asList(first, second),
                ConflictOptions.builder().build());

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("裁判连场间隔不足：阈值 15 分钟、实际 5 分钟 → INTERVAL WARN")
    void shouldDetectRefereeInterval() {
        ScheduledItem first = item(1L, 1L, null, BASE, 30, 101L, 201L, "100米 预赛");
        ScheduledItem second = item(2L, 1L, null, BASE.plusMinutes(35), 30, 102L, 201L, "200米 预赛");

        List<ConflictItem> conflicts = ConflictDetector.detect(Arrays.asList(first, second),
                ConflictOptions.builder().refereeIntervalMin(15).athleteIntervalMin(30).build());

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getConflictType()).isEqualTo(ConflictDetector.TYPE_INTERVAL);
        assertThat(conflicts.get(0).getRefereeId()).isEqualTo(201L);
        assertThat(conflicts.get(0).getIntervalMinutes()).isEqualTo(5L);
    }

    @Test
    @DisplayName("检测开关：关闭全部检测时不返回任何冲突")
    void shouldRespectDetectionSwitches() {
        ScheduledItem first = item(1L, 1L, 1L, BASE, 30, 101L, 201L, "100米 预赛");
        ScheduledItem second = item(2L, 1L, 1L, BASE.plusMinutes(10), 30, 101L, 201L, "跳远 决赛");

        List<ConflictItem> conflicts = ConflictDetector.detect(Arrays.asList(first, second),
                ConflictOptions.builder()
                        .checkAthlete(false)
                        .checkVenue(false)
                        .checkReferee(false)
                        .build());

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("排序：ERROR 排在 WARN 之前")
    void shouldSortErrorBeforeWarn() {
        ScheduledItem first = item(1L, 1L, 1L, BASE, 30, 101L, null, "100米 预赛");
        // 与 first 重叠（场地 + 运动员）→ ERROR
        ScheduledItem second = item(2L, 1L, 1L, BASE.plusMinutes(10), 30, 101L, null, "跳远 决赛");
        // 与 first 不重叠但间隔不足 → WARN
        ScheduledItem third = item(3L, 2L, 2L, BASE.plusMinutes(45), 30, 101L, null, "200米 预赛");

        List<ConflictItem> conflicts = ConflictDetector.detect(Arrays.asList(first, second, third),
                ConflictOptions.builder().build());

        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.get(0).getLevel()).isEqualTo(ConflictDetector.LEVEL_ERROR);
        assertThat(conflicts.get(conflicts.size() - 1).getLevel()).isEqualTo(ConflictDetector.LEVEL_WARN);
    }

    @Test
    @DisplayName("边界：空清单与单条日程不产生冲突；缺少时间的条目被忽略")
    void shouldHandleBoundaryCases() {
        assertThat(ConflictDetector.detect(new ArrayList<>(), null)).isEmpty();
        assertThat(ConflictDetector.detect(null, null)).isEmpty();
        assertThat(ConflictDetector.detect(Collections.singletonList(
                item(1L, 1L, 1L, BASE, 30, 101L, null, "100米 预赛")), null)).isEmpty();

        ScheduledItem noTime = ScheduledItem.builder().scheduleId(9L).eventId(9L).itemName("无时间").build();
        ScheduledItem normal = item(1L, 1L, 1L, BASE, 30, 101L, null, "100米 预赛");
        assertThat(ConflictDetector.detect(Arrays.asList(noTime, normal), null)).isEmpty();
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private ScheduledItem item(Long scheduleId, Long sessionId, Long venueId, LocalDateTime start,
                               int durationMinutes, Long athleteId, Long refereeId, String name) {
        return ScheduledItem.builder()
                .scheduleId(scheduleId)
                .sessionId(sessionId)
                .venueId(venueId)
                .eventId(scheduleId)
                .itemName(name)
                .startTime(start)
                .endTime(start.plusMinutes(durationMinutes))
                .track(true)
                .heatCount(1)
                .athleteIds(athleteId == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(athleteId)))
                .refereeIds(refereeId == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(refereeId)))
                .build();
    }
}
