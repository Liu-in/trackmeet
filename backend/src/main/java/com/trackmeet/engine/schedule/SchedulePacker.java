package com.trackmeet.engine.schedule;

import com.trackmeet.engine.conflict.ConflictDetector;
import com.trackmeet.engine.model.ConflictItem;
import com.trackmeet.engine.model.ConflictOptions;
import com.trackmeet.engine.model.ScheduleOptions;
import com.trackmeet.engine.model.SchedulePlan;
import com.trackmeet.engine.model.ScheduleTask;
import com.trackmeet.engine.model.ScheduledItem;
import com.trackmeet.engine.model.ScheduledItemResult;
import com.trackmeet.engine.model.SessionSpec;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 竞赛日程自动编排（装箱）算法。
 *
 * <p>策略：</p>
 * <ol>
 *   <li><b>锁定优先</b>：已锁定条目直接占用时间与运动员，参与后续约束计算，但不被移动；</li>
 *   <li><b>会话排序</b>：按 日期 → 时段（上午/下午/晚上）→ 开始时刻 依次排定；</li>
 *   <li><b>任务排序</b>：优先级 → 径赛优先 → 人数多者优先 → 项目 ID；</li>
 *   <li><b>场地时间轴</b>：径赛串行占用主场地（跑道游标），田赛按场地分别维护游标并可并行
 *       （并行数量受 {@code maxFieldVenues} 限制）；</li>
 *   <li><b>约束</b>：运动员既不能时间重叠，两项目之间还需满足兼项最小间隔；同项目相邻轮次需满足最小间隔；</li>
 *   <li><b>时段偏好</b>：优先放入偏好时段（MORNING/AFTERNOON/EVENING），放不下时退化为任意时段；</li>
 *   <li><b>缺口报告</b>：全部会话都放不下的项目汇总为短缺提示，并给出建议；</li>
 *   <li><b>冲突检测</b>：编排完成后调用 {@link ConflictDetector} 输出冲突清单（场地/兼项/间隔）。</li>
 * </ol>
 *
 * @author TrackMeet
 */
public final class SchedulePacker {

    /** 单个任务在会话内的最大试探次数 */
    private static final int MAX_PLACE_ATTEMPTS = 50;
    /** 运动员约束的最多推进次数 */
    private static final int MAX_ATHLETE_ADJUST_LOOPS = 100;
    /** 缺口报告列出的项目名上限 */
    private static final int SHORTAGE_NAME_LIMIT = 20;
    /** 无会话时的提示 */
    private static final String NO_SESSION_MESSAGE = "未配置竞赛单元（半天时段），请先在「竞赛单元」中创建单元后再执行自动编排。";

    private SchedulePacker() {
    }

    /**
     * 执行自动编排。
     *
     * @param sessions 竞赛单元（时间容器）
     * @param tasks    待排任务（项目 × 轮次）
     * @param options  编排参数
     * @return 编排计划（条目 + 缺口报告 + 冲突清单）
     */
    public static SchedulePlan pack(List<SessionSpec> sessions, List<ScheduleTask> tasks, ScheduleOptions options) {
        ScheduleOptions opts = options == null ? ScheduleOptions.builder().build() : options;
        SchedulePlan plan = new SchedulePlan();
        List<ScheduleTask> allTasks = new ArrayList<>();
        if (tasks != null) {
            for (ScheduleTask task : tasks) {
                if (task != null) {
                    allTasks.add(task);
                }
            }
        }
        if (allTasks.isEmpty()) {
            return plan;
        }

        List<SessionSpec> orderedSessions = new ArrayList<>();
        if (sessions != null) {
            for (SessionSpec session : sessions) {
                if (session != null && session.getDate() != null) {
                    orderedSessions.add(session);
                }
            }
        }
        orderedSessions.sort(Comparator
                .comparing(SessionSpec::getDate)
                .thenComparingInt(SessionSpec::periodOrder)
                .thenComparing(session -> session.getStartTime() == null ? LocalTime.MAX : session.getStartTime()));

        if (orderedSessions.isEmpty()) {
            plan.setUnscheduledCount(allTasks.size());
            plan.getShortageMessages().add(NO_SESSION_MESSAGE);
            return plan;
        }

        // 运行时状态
        Map<Long, List<TimeRange>> athleteBusy = new HashMap<>();
        Map<Long, LocalDateTime> eventLastEnd = new HashMap<>();
        Map<Long, LocalDateTime> trackCursorBySession = new HashMap<>();
        Map<Long, Map<Long, LocalDateTime>> fieldCursorBySession = new HashMap<>();
        List<ScheduledItem> scheduledItems = new ArrayList<>();

        List<ScheduleTask> pending = new ArrayList<>();
        for (ScheduleTask task : allTasks) {
            if (task.isLocked() && task.getLockedStart() != null) {
                LocalDateTime start = task.getLockedStart();
                LocalDateTime end = start.plusMinutes(durationOf(task));
                ScheduledItemResult result = toResult(task, task.getLockedSessionId(), start, end, task.getVenueId());
                plan.getItems().add(result);
                markBusy(athleteBusy, task, start, end);
                if (task.getEventId() != null) {
                    eventLastEnd.put(task.getEventId(), end);
                }
                SessionSpec lockedSession = findSession(orderedSessions, task.getLockedSessionId());
                if (lockedSession != null) {
                    if (task.isTrack()) {
                        trackCursorBySession.merge(lockedSession.getSessionId(), end, SchedulePacker::latest);
                    } else if (task.getVenueId() != null) {
                        fieldCursor(lockedSession, fieldCursorBySession).merge(task.getVenueId(), end, SchedulePacker::latest);
                    }
                }
                scheduledItems.add(toScheduledItem(result, task));
            } else {
                pending.add(task);
            }
        }

        pending.sort(Comparator
                .comparingInt(ScheduleTask::getPriority)
                .thenComparing(task -> task.isTrack() ? 0 : 1)
                .thenComparing(task -> -task.getParticipantCount())
                .thenComparing(task -> task.getEventId() == null ? 0L : task.getEventId()));

        List<ScheduleTask> unscheduled = new ArrayList<>();
        for (ScheduleTask task : pending) {
            Placement placement = null;
            SessionSpec placedSession = null;
            // 第一轮：遵守时段偏好
            for (SessionSpec session : orderedSessions) {
                if (!matchesPreference(session, task)) {
                    continue;
                }
                placement = tryPlace(session, task, opts, athleteBusy, eventLastEnd, trackCursorBySession, fieldCursorBySession);
                if (placement != null) {
                    placedSession = session;
                    break;
                }
            }
            // 第二轮：忽略时段偏好（保证尽量排入）
            if (placement == null && !"ANY".equalsIgnoreCase(task.getPeriodPreference())) {
                for (SessionSpec session : orderedSessions) {
                    placement = tryPlace(session, task, opts, athleteBusy, eventLastEnd, trackCursorBySession, fieldCursorBySession);
                    if (placement != null) {
                        placedSession = session;
                        break;
                    }
                }
            }
            if (placement == null || placedSession == null) {
                unscheduled.add(task);
                continue;
            }
            ScheduledItemResult result = toResult(task, placedSession.getSessionId(), placement.start, placement.end, placement.venueId);
            plan.getItems().add(result);
            markBusy(athleteBusy, task, placement.start, placement.end);
            if (task.getEventId() != null) {
                eventLastEnd.put(task.getEventId(), placement.end);
            }
            if (task.isTrack()) {
                trackCursorBySession.put(placedSession.getSessionId(), placement.end);
            } else if (placement.venueId != null) {
                fieldCursor(placedSession, fieldCursorBySession).put(placement.venueId, placement.end);
            }
            scheduledItems.add(toScheduledItem(result, task));
        }

        plan.getItems().sort(Comparator
                .comparing((ScheduledItemResult item) -> item.getDate() == null ? LocalDate.MAX : item.getDate())
                .thenComparing(item -> item.getStartTime() == null ? LocalTime.MAX : item.getStartTime())
                .thenComparing(item -> item.getEventId() == null ? 0L : item.getEventId()));

        plan.setScheduledCount(plan.getItems().size());
        plan.setUnscheduledCount(unscheduled.size());
        if (!unscheduled.isEmpty()) {
            plan.getShortageMessages().add(buildShortageMessage(unscheduled));
        }

        ConflictOptions conflictOptions = ConflictOptions.builder()
                .athleteIntervalMin(opts.getAthleteIntervalMin())
                .checkAthlete(true)
                .checkVenue(true)
                .checkReferee(false)
                .build();
        List<ConflictItem> conflicts = ConflictDetector.detect(scheduledItems, conflictOptions);
        plan.setConflicts(conflicts);
        return plan;
    }

    /**
     * 在指定会话内尝试放置任务：取游标与运动员约束后的最早可行时间。
     */
    private static Placement tryPlace(SessionSpec session, ScheduleTask task, ScheduleOptions opts,
                                      Map<Long, List<TimeRange>> athleteBusy,
                                      Map<Long, LocalDateTime> eventLastEnd,
                                      Map<Long, LocalDateTime> trackCursorBySession,
                                      Map<Long, Map<Long, LocalDateTime>> fieldCursorBySession) {
        LocalDateTime sessionStart = session.startDateTime();
        LocalDateTime sessionEnd = session.endDateTime();
        int duration = durationOf(task);

        Long venueId;
        LocalDateTime cursor;
        if (task.isTrack()) {
            venueId = task.getVenueId() != null ? task.getVenueId() : session.getMainVenueId();
            cursor = trackCursorBySession.getOrDefault(session.getSessionId(), sessionStart);
        } else {
            Map<Long, LocalDateTime> cursorMap = fieldCursor(session, fieldCursorBySession);
            venueId = pickFieldVenue(session, task, cursorMap, opts.getMaxFieldVenues());
            cursor = venueId == null ? sessionStart : cursorMap.getOrDefault(venueId, sessionStart);
        }
        if (!opts.isParallelFieldEvents() && !task.isTrack()) {
            // 田赛不并行时，退化为使用主场地串行
            venueId = session.getMainVenueId();
            cursor = trackCursorBySession.getOrDefault(session.getSessionId(), sessionStart);
        }

        LocalDateTime candidate = cursor.isBefore(sessionStart) ? sessionStart : cursor;
        LocalDateTime previousRoundEnd = task.getEventId() == null ? null : eventLastEnd.get(task.getEventId());

        for (int attempt = 0; attempt < MAX_PLACE_ATTEMPTS; attempt++) {
            LocalDateTime start = candidate;
            LocalDateTime adjusted = adjustForAthletes(start, task, athleteBusy, opts.getAthleteIntervalMin(), duration);
            if (previousRoundEnd != null) {
                LocalDateTime earliestByRound = previousRoundEnd.plusMinutes(Math.max(0, opts.getMinGapMin()));
                if (adjusted.isBefore(earliestByRound)) {
                    adjusted = adjustForAthletes(earliestByRound, task, athleteBusy, opts.getAthleteIntervalMin(), duration);
                }
            }
            if (adjusted.plusMinutes(duration).isAfter(sessionEnd)) {
                return null;
            }
            if (adjusted.equals(start)) {
                return new Placement(adjusted, adjusted.plusMinutes(duration), venueId);
            }
            candidate = adjusted;
        }
        return null;
    }

    /**
     * 依据运动员已占时段推进开始时间，使新任务既不重叠、也满足兼项最小间隔。
     */
    private static LocalDateTime adjustForAthletes(LocalDateTime candidate, ScheduleTask task,
                                                   Map<Long, List<TimeRange>> athleteBusy,
                                                   int intervalMin, int duration) {
        LocalDateTime current = candidate;
        if (task.getAthleteIds() == null || task.getAthleteIds().isEmpty()) {
            return current;
        }
        for (int loop = 0; loop < MAX_ATHLETE_ADJUST_LOOPS; loop++) {
            boolean moved = false;
            for (Long athleteId : task.getAthleteIds()) {
                if (athleteId == null) {
                    continue;
                }
                for (TimeRange range : athleteBusy.getOrDefault(athleteId, Collections.emptyList())) {
                    LocalDateTime end = current.plusMinutes(duration);
                    if (current.isBefore(range.end) && range.start.isBefore(end)) {
                        // 时间重叠 → 推到已有时段结束 + 间隔
                        current = range.end.plusMinutes(Math.max(0, intervalMin));
                        moved = true;
                    } else if (!current.isBefore(range.end)) {
                        long gap = Duration.between(range.end, current).toMinutes();
                        if (gap < intervalMin) {
                            current = range.end.plusMinutes(Math.max(0, intervalMin));
                            moved = true;
                        }
                    } else {
                        long gap = Duration.between(end, range.start).toMinutes();
                        if (gap < intervalMin) {
                            current = range.start.plusMinutes(Math.max(0, intervalMin));
                            moved = true;
                        }
                    }
                }
            }
            if (!moved) {
                break;
            }
        }
        return current;
    }

    /**
     * 选择田赛场地：优先任务指定场地，其次在单元可用田赛场地中选负载最轻者（游标最早）。
     */
    private static Long pickFieldVenue(SessionSpec session, ScheduleTask task,
                                       Map<Long, LocalDateTime> cursorMap, int maxFieldVenues) {
        if (task.getVenueId() != null) {
            return task.getVenueId();
        }
        List<Long> candidates = new ArrayList<>();
        if (session.getFieldVenueIds() != null) {
            for (Long venueId : session.getFieldVenueIds()) {
                if (venueId != null && !candidates.contains(venueId)) {
                    candidates.add(venueId);
                }
            }
        }
        if (candidates.isEmpty() && session.getMainVenueId() != null) {
            candidates.add(session.getMainVenueId());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (maxFieldVenues > 0 && candidates.size() > maxFieldVenues) {
            candidates = new ArrayList<>(candidates.subList(0, maxFieldVenues));
        }
        LocalDateTime sessionStart = session.startDateTime();
        Long best = candidates.get(0);
        LocalDateTime bestCursor = cursorMap.getOrDefault(best, sessionStart);
        for (Long venueId : candidates) {
            LocalDateTime cursor = cursorMap.getOrDefault(venueId, sessionStart);
            if (cursor.isBefore(bestCursor)) {
                best = venueId;
                bestCursor = cursor;
            }
        }
        return best;
    }

    private static boolean matchesPreference(SessionSpec session, ScheduleTask task) {
        String preference = task.getPeriodPreference();
        if (preference == null || preference.isEmpty() || "ANY".equalsIgnoreCase(preference)) {
            return true;
        }
        return preference.equalsIgnoreCase(session.getPeriod());
    }

    private static Map<Long, LocalDateTime> fieldCursor(SessionSpec session,
                                                        Map<Long, Map<Long, LocalDateTime>> fieldCursorBySession) {
        return fieldCursorBySession.computeIfAbsent(session.getSessionId(), key -> new HashMap<>());
    }

    private static SessionSpec findSession(List<SessionSpec> sessions, Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        for (SessionSpec session : sessions) {
            if (sessionId.equals(session.getSessionId())) {
                return session;
            }
        }
        return null;
    }

    private static void markBusy(Map<Long, List<TimeRange>> athleteBusy, ScheduleTask task,
                                 LocalDateTime start, LocalDateTime end) {
        if (task.getAthleteIds() == null) {
            return;
        }
        for (Long athleteId : task.getAthleteIds()) {
            if (athleteId == null) {
                continue;
            }
            athleteBusy.computeIfAbsent(athleteId, key -> new ArrayList<>()).add(new TimeRange(start, end));
        }
    }

    private static int durationOf(ScheduleTask task) {
        return Math.max(1, task.getDurationMin());
    }

    private static LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private static ScheduledItemResult toResult(ScheduleTask task, Long sessionId,
                                                LocalDateTime start, LocalDateTime end, Long venueId) {
        ScheduledItemResult result = new ScheduledItemResult();
        result.setEventId(task.getEventId());
        result.setRoundId(task.getRoundId());
        result.setEventName(task.getEventName());
        result.setRoundCode(task.getRoundCode());
        result.setSessionId(sessionId);
        result.setDate(start.toLocalDate());
        result.setStartTime(start.toLocalTime().withSecond(0).withNano(0));
        result.setEndTime(end.toLocalTime().withSecond(0).withNano(0));
        result.setVenueId(venueId);
        result.setHeatCount(Math.max(1, task.getHeatCount()));
        result.setTrack(task.isTrack());
        result.setParticipantCount(task.getParticipantCount());
        return result;
    }

    private static ScheduledItem toScheduledItem(ScheduledItemResult result, ScheduleTask task) {
        return ScheduledItem.builder()
                .sessionId(result.getSessionId())
                .venueId(result.getVenueId())
                .eventId(result.getEventId())
                .roundId(result.getRoundId())
                .itemName(buildItemName(task))
                .startTime(LocalDateTime.of(result.getDate(), result.getStartTime()))
                .endTime(LocalDateTime.of(result.getDate(), result.getEndTime()))
                .track(result.isTrack())
                .heatCount(result.getHeatCount())
                .athleteIds(task.getAthleteIds() == null ? new ArrayList<>() : new ArrayList<>(task.getAthleteIds()))
                .refereeIds(new ArrayList<>())
                .build();
    }

    private static String buildItemName(ScheduleTask task) {
        String name = task.getEventName() == null ? "未命名项目" : task.getEventName();
        if (task.getRoundCode() != null && !task.getRoundCode().isEmpty()) {
            return name + " " + task.getRoundCode();
        }
        return name;
    }

    private static String buildShortageMessage(List<ScheduleTask> unscheduled) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下 ").append(unscheduled.size()).append(" 个项目未能排入可用单元：");
        int limit = Math.min(unscheduled.size(), SHORTAGE_NAME_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(buildItemName(unscheduled.get(i)));
        }
        if (unscheduled.size() > limit) {
            builder.append(" 等");
        }
        builder.append("。建议：增加竞赛单元、延长单元时长、增加可用场地，或在项目级规则中下调预计时长/兼项间隔。");
        return builder.toString();
    }

    /**
     * 放置结果。
     */
    private static class Placement {

        private final LocalDateTime start;
        private final LocalDateTime end;
        private final Long venueId;

        Placement(LocalDateTime start, LocalDateTime end, Long venueId) {
            this.start = start;
            this.end = end;
            this.venueId = venueId;
        }
    }

    /**
     * 运动员已占时段。
     */
    private static class TimeRange {

        private final LocalDateTime start;
        private final LocalDateTime end;

        TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
