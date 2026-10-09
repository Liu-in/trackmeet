package com.trackmeet.engine.schedule;

import com.trackmeet.engine.model.ScheduleOptions;
import com.trackmeet.engine.model.SchedulePlan;
import com.trackmeet.engine.model.ScheduleTask;
import com.trackmeet.engine.model.ScheduledItemResult;
import com.trackmeet.engine.model.SessionSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动编排（日程装箱）单元测试：基本装箱、径赛串行与田赛并行、兼项间隔约束、锁定保护、
 * 时段偏好、缺口报告与边界场景。
 *
 * @author TrackMeet
 */
class SchedulePackerTest {

    private static final LocalDate DAY = LocalDate.of(2025, 10, 18);

    @Test
    @DisplayName("基本装箱：径赛串行占用主场地，田赛在另一场地并行")
    void shouldPackTrackAndFieldInParallel() {
        List<SessionSpec> sessions = Collections.singletonList(morningSession(1L, 1L, 2L));
        List<ScheduleTask> tasks = Arrays.asList(
                trackTask(1L, 1L, 20, 10, 101L, 102L, "100米"),
                trackTask(2L, 2L, 15, 20, 103L, null, "200米"),
                fieldTask(3L, 3L, 30, 30, 104L, "跳远"));

        SchedulePlan plan = SchedulePacker.pack(sessions, tasks, ScheduleOptions.builder().build());

        assertThat(plan.getItems()).hasSize(3);
        assertThat(plan.getUnscheduledCount()).isZero();

        ScheduledItemResult first = find(plan, 1L);
        ScheduledItemResult second = find(plan, 2L);
        ScheduledItemResult third = find(plan, 3L);
        assertThat(first.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(first.getEndTime()).isEqualTo(LocalTime.of(8, 20));
        assertThat(first.getVenueId()).isEqualTo(1L);
        assertThat(second.getStartTime()).isEqualTo(LocalTime.of(8, 20));
        assertThat(second.getEndTime()).isEqualTo(LocalTime.of(8, 35));
        // 田赛使用田赛场地且与径赛并行从 08:00 开始
        assertThat(third.getVenueId()).isEqualTo(2L);
        assertThat(third.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(third.getEndTime()).isEqualTo(LocalTime.of(8, 30));
    }

    @Test
    @DisplayName("兼项间隔约束：同一运动员的两个项目之间至少间隔 30 分钟")
    void shouldRespectAthleteInterval() {
        List<SessionSpec> sessions = Collections.singletonList(morningSession(1L, 1L, 2L));
        List<ScheduleTask> tasks = Arrays.asList(
                trackTask(1L, 1L, 20, 10, 101L, null, "100米"),
                fieldTask(2L, 2L, 20, 20, 101L, "跳远"));

        SchedulePlan plan = SchedulePacker.pack(sessions, tasks, ScheduleOptions.builder()
                .athleteIntervalMin(30)
                .build());

        ScheduledItemResult first = find(plan, 1L);
        ScheduledItemResult second = find(plan, 2L);
        assertThat(first.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(first.getEndTime()).isEqualTo(LocalTime.of(8, 20));
        // 08:00 + 20 分钟 + 30 分钟间隔 = 08:50
        assertThat(second.getStartTime()).isEqualTo(LocalTime.of(8, 50));
        assertThat(plan.getConflicts()).isEmpty();
    }

    @Test
    @DisplayName("锁定保护：已锁定条目保持原时间，未锁定任务排在其后")
    void shouldKeepLockedItemUntouched() {
        List<SessionSpec> sessions = Collections.singletonList(morningSession(1L, 1L, 2L));
        ScheduleTask locked = ScheduleTask.builder()
                .eventId(1L)
                .roundId(1L)
                .eventName("100米")
                .roundCode("FINAL")
                .track(true)
                .participantCount(8)
                .heatCount(1)
                .durationMin(20)
                .priority(1)
                .periodPreference("ANY")
                .locked(true)
                .lockedStart(LocalDateTime.of(DAY, LocalTime.of(9, 0)))
                .lockedSessionId(1L)
                .venueId(1L)
                .athleteIds(Collections.singletonList(101L))
                .build();
        ScheduleTask pending = trackTask(2L, 2L, 20, 10, 102L, null, "200米");

        SchedulePlan plan = SchedulePacker.pack(sessions, Arrays.asList(locked, pending),
                ScheduleOptions.builder().build());

        ScheduledItemResult lockedResult = find(plan, 1L);
        ScheduledItemResult pendingResult = find(plan, 2L);
        assertThat(lockedResult.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(lockedResult.getEndTime()).isEqualTo(LocalTime.of(9, 20));
        // 未锁定任务不会被排到锁定条目之前
        assertThat(pendingResult.getStartTime()).isEqualTo(LocalTime.of(9, 20));
    }

    @Test
    @DisplayName("时段偏好：偏好下午的任务排入下午单元")
    void shouldRespectPeriodPreference() {
        List<SessionSpec> sessions = Arrays.asList(morningSession(1L, 1L, 2L), afternoonSession(2L, 1L, 2L));
        ScheduleTask task = trackTask(1L, 1L, 20, 10, 101L, null, "100米");
        task.setPeriodPreference("AFTERNOON");

        SchedulePlan plan = SchedulePacker.pack(sessions, Collections.singletonList(task),
                ScheduleOptions.builder().build());

        assertThat(plan.getItems()).hasSize(1);
        assertThat(plan.getItems().get(0).getSessionId()).isEqualTo(2L);
        assertThat(plan.getItems().get(0).getStartTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    @DisplayName("缺口报告：单元时长不足时给出未排项目与建议")
    void shouldReportShortage() {
        SessionSpec shortSession = SessionSpec.builder()
                .sessionId(1L)
                .sessionName("第一天上午")
                .date(DAY)
                .period("MORNING")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .mainVenueId(1L)
                .fieldVenueIds(new ArrayList<>())
                .build();
        List<ScheduleTask> tasks = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            tasks.add(trackTask(i, i, 20, (int) i, 100L + i, null, "项目" + i));
        }

        SchedulePlan plan = SchedulePacker.pack(Collections.singletonList(shortSession), tasks,
                ScheduleOptions.builder().build());

        assertThat(plan.getScheduledCount()).isEqualTo(3);
        assertThat(plan.getUnscheduledCount()).isEqualTo(2);
        assertThat(plan.getShortageMessages()).hasSize(1);
        assertThat(plan.getShortageMessages().get(0)).contains("未能排入").contains("建议");
    }

    @Test
    @DisplayName("无竞赛单元：全部任务未排并给出配置提示")
    void shouldReportMissingSessions() {
        SchedulePlan plan = SchedulePacker.pack(new ArrayList<>(),
                Collections.singletonList(trackTask(1L, 1L, 20, 10, 101L, null, "100米")),
                ScheduleOptions.builder().build());

        assertThat(plan.getItems()).isEmpty();
        assertThat(plan.getUnscheduledCount()).isEqualTo(1);
        assertThat(plan.getShortageMessages()).hasSize(1);
        assertThat(plan.getShortageMessages().get(0)).contains("竞赛单元");
    }

    @Test
    @DisplayName("边界：无任务时返回空计划")
    void shouldHandleEmptyTasks() {
        SchedulePlan plan = SchedulePacker.pack(Collections.singletonList(morningSession(1L, 1L, 2L)),
                new ArrayList<>(), null);

        assertThat(plan.getItems()).isEmpty();
        assertThat(plan.getScheduledCount()).isZero();
        assertThat(plan.getUnscheduledCount()).isZero();
        assertThat(plan.getShortageMessages()).isEmpty();
    }

    @Test
    @DisplayName("多单元：上午排满后自动使用下午单元")
    void shouldOverflowToNextSession() {
        SessionSpec shortMorning = SessionSpec.builder()
                .sessionId(1L)
                .date(DAY)
                .period("MORNING")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(8, 30))
                .mainVenueId(1L)
                .fieldVenueIds(new ArrayList<>())
                .build();
        List<SessionSpec> sessions = Arrays.asList(shortMorning, afternoonSession(2L, 1L, 2L));
        List<ScheduleTask> tasks = Arrays.asList(
                trackTask(1L, 1L, 20, 10, 101L, null, "项目1"),
                trackTask(2L, 2L, 20, 20, 102L, null, "项目2"));

        SchedulePlan plan = SchedulePacker.pack(sessions, tasks, ScheduleOptions.builder().build());

        assertThat(plan.getItems()).hasSize(2);
        assertThat(find(plan, 1L).getSessionId()).isEqualTo(1L);
        assertThat(find(plan, 2L).getSessionId()).isEqualTo(2L);
        assertThat(find(plan, 2L).getStartTime()).isEqualTo(LocalTime.of(14, 0));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private SessionSpec morningSession(Long sessionId, Long mainVenueId, Long fieldVenueId) {
        return SessionSpec.builder()
                .sessionId(sessionId)
                .sessionName("第一天上午")
                .date(DAY)
                .period("MORNING")
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .mainVenueId(mainVenueId)
                .fieldVenueIds(fieldVenueId == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(fieldVenueId)))
                .build();
    }

    private SessionSpec afternoonSession(Long sessionId, Long mainVenueId, Long fieldVenueId) {
        return SessionSpec.builder()
                .sessionId(sessionId)
                .sessionName("第一天下午")
                .date(DAY)
                .period("AFTERNOON")
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(18, 0))
                .mainVenueId(mainVenueId)
                .fieldVenueIds(fieldVenueId == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(fieldVenueId)))
                .build();
    }

    private ScheduleTask trackTask(Long eventId, Long roundId, int durationMin, int priority,
                                   Long athleteId, Long secondAthleteId, String eventName) {
        List<Long> athletes = new ArrayList<>();
        if (athleteId != null) {
            athletes.add(athleteId);
        }
        if (secondAthleteId != null) {
            athletes.add(secondAthleteId);
        }
        return ScheduleTask.builder()
                .eventId(eventId)
                .roundId(roundId)
                .eventName(eventName)
                .roundCode("PRE")
                .track(true)
                .participantCount(athletes.size() * 2)
                .heatCount(1)
                .durationMin(durationMin)
                .priority(priority)
                .periodPreference("ANY")
                .athleteIds(athletes)
                .build();
    }

    private ScheduleTask fieldTask(Long eventId, Long roundId, int durationMin, int priority,
                                   Long athleteId, String eventName) {
        return ScheduleTask.builder()
                .eventId(eventId)
                .roundId(roundId)
                .eventName(eventName)
                .roundCode("FINAL")
                .track(false)
                .participantCount(12)
                .heatCount(1)
                .durationMin(durationMin)
                .priority(priority)
                .periodPreference("ANY")
                .athleteIds(athleteId == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(athleteId)))
                .build();
    }

    private ScheduledItemResult find(SchedulePlan plan, Long eventId) {
        return plan.getItems().stream()
                .filter(item -> eventId.equals(item.getEventId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到项目 " + eventId + " 的编排结果"));
    }
}
