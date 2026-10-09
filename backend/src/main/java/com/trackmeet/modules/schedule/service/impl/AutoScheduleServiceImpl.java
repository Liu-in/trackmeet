package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.engine.model.ConflictItem;
import com.trackmeet.engine.model.ScheduleOptions;
import com.trackmeet.engine.model.SchedulePlan;
import com.trackmeet.engine.model.ScheduleTask;
import com.trackmeet.engine.model.ScheduledItemResult;
import com.trackmeet.engine.model.SessionSpec;
import com.trackmeet.engine.schedule.SchedulePacker;
import com.trackmeet.modules.schedule.dto.ScheduleAutoDTO;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.entity.ScheduleSession;
import com.trackmeet.modules.schedule.service.AutoScheduleService;
import com.trackmeet.modules.schedule.service.ScheduleService;
import com.trackmeet.modules.schedule.service.support.CachedScheduleData;
import com.trackmeet.modules.schedule.service.support.JsonTextUtils;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.service.support.ScheduleDataProvider;
import com.trackmeet.modules.schedule.service.support.ScheduleRuleMatcher;
import com.trackmeet.modules.schedule.vo.AutoPlanVO;
import com.trackmeet.modules.schedule.vo.ConflictVO;
import com.trackmeet.modules.schedule.vo.EventBriefRowVO;
import com.trackmeet.modules.schedule.vo.EventScheduleRuleVO;
import com.trackmeet.modules.schedule.vo.ScheduleTaskRowVO;
import com.trackmeet.modules.schedule.vo.VenueBriefVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 日程自动编排服务实现。
 * <p>
 * 装配策略：
 * <ol>
 *   <li><b>单元 → {@link SessionSpec}</b>：读取 {@code schedule_session}（日期/时段/起止时刻/主场地/
 *       {@code venue_ids_json} 可用场地）；{@code is_locked=1} 的单元不参与排入（不再接受新条目）；</li>
 *   <li><b>项目×轮次 → {@link ScheduleTask}</b>：读当届 {@code event} + {@code event_round} + 有效报名人数
 *       （{@code registration.status='APPROVED'}）+ {@code event_schedule_rule} / {@code event.duration_min}
 *       （缺失时走 {@link com.trackmeet.engine.schedule.DurationEstimator}）+ 该轮次参赛运动员 ID；</li>
 *   <li><b>锁定保护</b>：{@code schedule.is_locked=1} 的既有条目转为 {@code locked=true} 且带
 *       {@code lockedStart/lockedSessionId} 的任务，由引擎作为固定约束占位，落库阶段整条跳过；</li>
 *   <li><b>规则阈值</b>：{@code meet_rule_config.conflict_interval_min / referee_interval_min} 等全部来自配置表；</li>
 *   <li><b>落库</b>：{@code source_type=AUTO}、同批次 {@code batch_no} 一致、{@code order_no} 按时间顺序递增，
 *       按 {@code (meet_id, event_id, round_id)} 匹配则更新、否则插入。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class AutoScheduleServiceImpl implements AutoScheduleService {

    /** 编排批次号前缀 */
    private static final String BATCH_PREFIX = "AUTO";

    /** 单次生成的批次号时间格式 */
    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ScheduleService scheduleService;
    private final ScheduleDataProvider dataProvider;
    private final CachedScheduleData cachedData;
    private final ScheduleRuleMatcher ruleMatcher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutoPlanVO autoSchedule(ScheduleAutoDTO dto) {
        ScheduleAutoDTO request = dto == null ? new ScheduleAutoDTO() : dto;
        Long meetId = MeetGuard.resolve(request.getMeetId());
        boolean apply = Boolean.TRUE.equals(request.getApply());
        boolean keepLocked = request.getKeepLocked() == null || Boolean.TRUE.equals(request.getKeepLocked());
        List<Long> scopeSessionIds = resolveScopeSessionIds(meetId, request);

        // 既有日程（含锁定条目与增量匹配基准）
        List<Schedule> existingSchedules = dataProvider.listSchedules(meetId, null);
        Map<String, Schedule> existingByEventRound = new HashMap<>();
        for (Schedule schedule : existingSchedules) {
            existingByEventRound.put(eventRoundKey(schedule.getEventId(), schedule.getRoundId()), schedule);
        }
        List<Schedule> lockedSchedules = new ArrayList<>();
        if (keepLocked) {
            for (Schedule schedule : existingSchedules) {
                if (schedule.getIsLocked() != null && schedule.getIsLocked() == Schedule.LOCKED) {
                    lockedSchedules.add(schedule);
                }
            }
        }

        List<ScheduleSession> allSessions = dataProvider.sessionMap(meetId, null).values().stream()
                .sorted(java.util.Comparator.comparing(ScheduleSession::getSessionDate,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(session -> session.getSortNo() == null ? Integer.MAX_VALUE : session.getSortNo())
                        .thenComparing(session -> session.getId() == null ? Long.MAX_VALUE : session.getId()))
                .collect(Collectors.toList());
        cachedData.load(meetId, distinct(request.getEventIds()), distinct(request.getGroupIds()), true);
        List<Long> eventIds = resolveScopeEventIds(meetId, request);
        boolean restrictEvents = eventIds != null;
        if (eventIds == null) {
            eventIds = new ArrayList<>(cachedData.getEvents().keySet());
        }
        Map<Long, List<Long>> athletesByEvent = dataProvider.athleteIdsByEvent(meetId, eventIds);
        Map<Long, Integer> participantByEvent = dataProvider.participantCountByEvent(meetId, eventIds);
        Map<Long, VenueBriefVO> venueMap = dataProvider.venueMap(meetId);

        // 单元：锁定单元不再接受新排入
        List<SessionSpec> sessionSpecs = new ArrayList<>();
        Map<Long, ScheduleSession> sessionById = new LinkedHashMap<>();
        for (ScheduleSession session : allSessions) {
            sessionById.put(session.getId(), session);
            if (session.getIsLocked() != null && session.getIsLocked() == ScheduleSession.LOCKED) {
                continue;
            }
            if (scopeSessionIds != null && !scopeSessionIds.contains(session.getId())) {
                continue;
            }
            sessionSpecs.add(toSessionSpec(session));
        }

        List<ScheduleTask> tasks = buildTasks(meetId, eventIds,
                participantByEvent, athletesByEvent, venueMap);
        List<ScheduleTask> lockedTasks = buildLockedTasks(lockedSchedules, scopeSessionIds,
                restrictEvents ? new LinkedHashSet<>(eventIds) : null);
        List<ScheduleTask> allTasks = new ArrayList<>(tasks);
        allTasks.addAll(lockedTasks);

        ScheduleOptions options = ScheduleOptions.builder()
                .athleteIntervalMin(resolveAthleteIntervalMin(request))
                .minGapMin(resolveMinGapMin())
                .parallelFieldEvents(Boolean.TRUE.equals(request.getParallelFieldEvents()))
                .maxFieldVenues(resolveMaxFieldVenues())
                .build();

        SchedulePlan plan = SchedulePacker.pack(sessionSpecs, allTasks, options);
        AutoPlanVO vo = buildPlanVo(plan, sessionById, venueMap, meetId, request, apply, existingByEventRound);
        if (apply && !plan.getItems().isEmpty()) {
            applyPlan(meetId, plan);
            vo.setBatchNo(plan.getBatchNo() == null ? vo.getBatchNo() : plan.getBatchNo());
        }
        return vo;
    }

    @Override
    public AutoPlanVO regenerate(ScheduleAutoDTO dto) {
        ScheduleAutoDTO request = dto == null ? new ScheduleAutoDTO() : dto;
        request.setApply(Boolean.TRUE);
        return autoSchedule(request);
    }

    // ------------------------------------------------------------------
    // 装配
    // ------------------------------------------------------------------

    /**
     * 范围 → 单元 ID 列表（null 表示不限）。
     */
    private List<Long> resolveScopeSessionIds(Long meetId, ScheduleAutoDTO request) {
        String scope = normalizeScope(request.getScope());
        if (ScheduleAutoDTO.SCOPE_SESSION.equals(scope)) {
            List<Long> sessionIds = distinct(request.getSessionIds());
            if (sessionIds.isEmpty()) {
                throw new BizException(ResultCode.PARAM_ERROR, "按单元编排时 sessionIds 不能为空");
            }
            return sessionIds;
        }
        if (ScheduleAutoDTO.SCOPE_GROUP.equals(scope) || ScheduleAutoDTO.SCOPE_EVENT.equals(scope)) {
            // 组别/项目范围下单元不限，仅按项目过滤
            return null;
        }
        return null;
    }

    /**
     * 范围 → 项目 ID 列表（null 表示全部启用项目）。
     */
    private List<Long> resolveScopeEventIds(Long meetId, ScheduleAutoDTO request) {
        String scope = normalizeScope(request.getScope());
        if (ScheduleAutoDTO.SCOPE_EVENT.equals(scope)) {
            List<Long> eventIds = distinct(request.getEventIds());
            if (eventIds.isEmpty()) {
                throw new BizException(ResultCode.PARAM_ERROR, "按项目编排时 eventIds 不能为空");
            }
            return eventIds;
        }
        if (ScheduleAutoDTO.SCOPE_GROUP.equals(scope)) {
            List<Long> groupIds = distinct(request.getGroupIds());
            if (groupIds.isEmpty()) {
                throw new BizException(ResultCode.PARAM_ERROR, "按组别编排时 groupIds 不能为空");
            }
            List<Long> eventIds = new ArrayList<>();
            for (EventBriefRowVO event : cachedData.getEvents().values()) {
                if (event.getGroupId() != null && groupIds.contains(event.getGroupId())
                        && !eventIds.contains(event.getEventId())) {
                    eventIds.add(event.getEventId());
                }
            }
            return eventIds;
        }
        if (ScheduleAutoDTO.SCOPE_SESSION.equals(scope)) {
            // 单元范围：仅编排本单元内已有日程涉及的项目
            List<Long> sessionIds = distinct(request.getSessionIds());
            Set<Long> eventIds = new LinkedHashSet<>();
            for (Schedule schedule : dataProvider.listSchedules(meetId, sessionIds)) {
                if (schedule.getEventId() != null) {
                    eventIds.add(schedule.getEventId());
                }
            }
            return new ArrayList<>(eventIds);
        }
        return null;
    }

    /**
     * 单元实体 → 引擎单元规格。
     */
    private SessionSpec toSessionSpec(ScheduleSession session) {
        List<Long> venueIds = JsonTextUtils.parseLongArray(objectMapper, session.getVenueIdsJson());
        List<Long> fieldVenueIds = new ArrayList<>(venueIds);
        if (session.getVenueId() != null && !fieldVenueIds.contains(session.getVenueId())) {
            fieldVenueIds.add(session.getVenueId());
        }
        return SessionSpec.builder()
                .sessionId(session.getId())
                .sessionName(session.getSessionName())
                .date(session.getSessionDate())
                .period(session.getPeriod())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .mainVenueId(session.getVenueId())
                .fieldVenueIds(fieldVenueIds)
                .build();
    }

    /**
     * 项目 × 轮次 → 引擎任务。
     */
    private List<ScheduleTask> buildTasks(Long meetId, List<Long> eventIds,
                                          Map<Long, Integer> participantByEvent,
                                          Map<Long, List<Long>> athletesByEvent,
                                          Map<Long, VenueBriefVO> venueMap) {
        List<ScheduleTask> tasks = new ArrayList<>();
        for (Long eventId : eventIds) {
            if (eventId == null) {
                continue;
            }
            EventBriefRowVO event = cachedData.event(eventId);
            if (event == null) {
                continue;
            }
            List<ScheduleTaskRowVO> rows = cachedData.taskRows(eventId);
            if (rows.isEmpty()) {
                // 无轮次（或指定项目已停用）：跳过该项目的编排
                continue;
            }
            int participantCount = participantByEvent.getOrDefault(eventId, 0);
            Long trackVenueId = pickTrackVenue(venueMap);
            Long fieldVenueId = pickFieldVenue(venueMap);
            boolean track = isTrack(event.getEventType());
            for (ScheduleTaskRowVO row : rows) {
                int heatCount = estimateHeatCount(event, row, participantCount, venueMap);
                EventScheduleRuleVO rule = ruleMatcher.match(cachedData.getScheduleRules(), event.getEventType(),
                        event.getSubType(), event.getGender());
                int duration = ruleMatcher.resolveDuration(rule, row.getEventDurationMin(), event.getEventType(),
                        event.getSubType(), participantCount, heatCount, cachedData.attemptCount(eventId));
                List<Long> athleteIds = athletesByEvent.getOrDefault(eventId, new ArrayList<>());
                tasks.add(ScheduleTask.builder()
                        .eventId(eventId)
                        .roundId(row.getRoundId())
                        .eventName(event.getEventName())
                        .roundCode(row.getRoundCode())
                        .track(track)
                        .participantCount(participantCount)
                        .heatCount(heatCount)
                        .durationMin(duration)
                        .priority(ruleMatcher.resolvePriority(rule))
                        .periodPreference(ruleMatcher.resolvePeriodPreference(rule))
                        .venueId(track ? trackVenueId : fieldVenueId)
                        .locked(false)
                        .athleteIds(new ArrayList<>(athleteIds))
                        .build());
            }
        }
        return tasks;
    }

    /**
     * 已锁定日程 → 固定约束任务（引擎按 {@code lockedStart} 占位，不参与移动）。
     *
     * @param lockedSchedules 锁定日程
     * @param sessionIds      本次编排的单元范围（null 表示不限）
     * @param eventIds        本次编排的项目范围（null 表示不限）
     */
    private List<ScheduleTask> buildLockedTasks(List<Schedule> lockedSchedules, List<Long> sessionIds,
                                                Set<Long> eventIds) {
        List<ScheduleTask> tasks = new ArrayList<>();
        for (Schedule schedule : lockedSchedules) {
            if (sessionIds != null && !sessionIds.contains(schedule.getSessionId())) {
                continue;
            }
            if (eventIds != null && !eventIds.contains(schedule.getEventId())) {
                continue;
            }
            if (schedule.getPlanStartTime() == null) {
                continue;
            }
            EventBriefRowVO event = cachedData.event(schedule.getEventId());
            if (event == null) {
                continue;
            }
            int duration = schedule.getDurationMin() == null ? 1 : schedule.getDurationMin();
            tasks.add(ScheduleTask.builder()
                    .eventId(schedule.getEventId())
                    .roundId(schedule.getRoundId())
                    .eventName(event.getEventName())
                    .roundCode(null)
                    .track(isTrack(event.getEventType()))
                    .participantCount(0)
                    .heatCount(schedule.getHeatCount() == null ? 1 : schedule.getHeatCount())
                    .durationMin(duration)
                    .locked(true)
                    .lockedStart(schedule.getPlanStartTime())
                    .lockedSessionId(schedule.getSessionId())
                    .venueId(schedule.getVenueId())
                    .athleteIds(new ArrayList<>())
                    .build());
        }
        return tasks;
    }

    /**
     * 预计组数：项目级每组人数 → 场地道数 → 8，按 {@code ceil(人数/每组人数)} 推算。
     */
    private int estimateHeatCount(EventBriefRowVO event, ScheduleTaskRowVO row, int participantCount,
                                  Map<Long, VenueBriefVO> venueMap) {
        if (participantCount <= 0) {
            return row.getPlanHeatCount() != null && row.getPlanHeatCount() > 0 ? row.getPlanHeatCount() : 1;
        }
        int heatSize = 0;
        if (row.getPlanHeatSize() != null && row.getPlanHeatSize() > 0) {
            heatSize = row.getPlanHeatSize();
        } else if (row.getDrawGroupSize() != null && row.getDrawGroupSize() > 0) {
            heatSize = row.getDrawGroupSize();
        }
        if (heatSize <= 0) {
            Long venueId = isTrack(event.getEventType()) ? pickTrackVenue(venueMap) : pickFieldVenue(venueMap);
            heatSize = cachedData.laneCount(event.getEventId(), venueId);
        }
        if (heatSize <= 0) {
            heatSize = ScheduleRuleMatcher.DEFAULT_LANE_COUNT;
        }
        int heatCount = (int) Math.ceil((double) participantCount / heatSize);
        return Math.max(1, Math.min(heatCount, participantCount));
    }

    private Long pickTrackVenue(Map<Long, VenueBriefVO> venueMap) {
        for (VenueBriefVO venue : venueMap.values()) {
            String type = venue.getVenueType() == null ? "" : venue.getVenueType().trim().toUpperCase();
            if ("TRACK".equals(type)) {
                return venue.getId();
            }
        }
        return null;
    }

    private Long pickFieldVenue(Map<Long, VenueBriefVO> venueMap) {
        for (VenueBriefVO venue : venueMap.values()) {
            String type = venue.getVenueType() == null ? "" : venue.getVenueType().trim().toUpperCase();
            if ("FIELD".equals(type) || "INDOOR".equals(type)) {
                return venue.getId();
            }
        }
        return null;
    }

    private boolean isTrack(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return false;
        }
        String type = eventType.trim().toUpperCase();
        return "TRACK".equals(type) || "RELAY".equals(type);
    }

    // ------------------------------------------------------------------
    // 预览装配
    // ------------------------------------------------------------------

    private AutoPlanVO buildPlanVo(SchedulePlan plan, Map<Long, ScheduleSession> sessionById,
                                   Map<Long, VenueBriefVO> venueMap, Long meetId, ScheduleAutoDTO request,
                                   boolean apply, Map<String, Schedule> existingByEventRound) {
        AutoPlanVO vo = new AutoPlanVO();
        vo.setApplied(apply);
        vo.setMeetId(meetId);
        vo.setScope(normalizeScope(request.getScope()));
        for (ScheduledItemResult item : plan.getItems()) {
            AutoPlanVO.Item target = new AutoPlanVO.Item();
            target.setEventId(item.getEventId());
            target.setRoundId(item.getRoundId());
            target.setEventName(item.getEventName());
            target.setRoundCode(item.getRoundCode());
            target.setSessionId(item.getSessionId());
            ScheduleSession session = sessionById.get(item.getSessionId());
            if (session != null) {
                target.setSessionName(session.getSessionName());
            }
            target.setDate(item.getDate());
            target.setStartTime(item.getStartTime());
            target.setEndTime(item.getEndTime());
            target.setVenueId(item.getVenueId());
            VenueBriefVO venue = venueMap.get(item.getVenueId());
            if (venue != null) {
                target.setVenueName(venue.getVenueName());
            }
            target.setHeatCount(item.getHeatCount());
            target.setTrack(item.isTrack());
            target.setParticipantCount(item.getParticipantCount());
            Schedule matched = existingByEventRound.get(eventRoundKey(item.getEventId(), item.getRoundId()));
            target.setScheduleId(matched == null ? null : matched.getId());
            target.setPlanStartTime(item.getDate() == null || item.getStartTime() == null
                    ? null : LocalDateTime.of(item.getDate(), item.getStartTime()));
            target.setPlanEndTime(item.getDate() == null || item.getEndTime() == null
                    ? null : LocalDateTime.of(item.getDate(), item.getEndTime()));
            vo.getItems().add(target);
        }
        for (ConflictItem conflict : plan.getConflicts()) {
            ConflictVO conflictVO = new ConflictVO();
            conflictVO.setConflictType(conflict.getConflictType());
            conflictVO.setLevel(conflict.getLevel());
            conflictVO.setScheduleId(conflict.getScheduleId());
            conflictVO.setRelatedScheduleId(conflict.getRelatedScheduleId());
            conflictVO.setAthleteId(conflict.getAthleteId());
            conflictVO.setRefereeId(conflict.getRefereeId());
            conflictVO.setVenueId(conflict.getVenueId());
            conflictVO.setOverlapMinutes((int) conflict.getOverlapMinutes());
            conflictVO.setIntervalMinutes(conflict.getIntervalMinutes() == null
                    ? null : conflict.getIntervalMinutes().intValue());
            conflictVO.setRequiredIntervalMin(conflict.getRequiredIntervalMin() == null
                    ? null : conflict.getRequiredIntervalMin().intValue());
            conflictVO.setMessage(conflict.getMessage());
            vo.getConflicts().add(conflictVO);
        }
        vo.setShortageMessages(plan.getShortageMessages() == null
                ? new ArrayList<>() : new ArrayList<>(plan.getShortageMessages()));
        vo.setScheduledCount(plan.getScheduledCount());
        vo.setUnscheduledCount(plan.getUnscheduledCount());

        AutoPlanVO.Summary summary = new AutoPlanVO.Summary();
        summary.setSessionCount((int) sessionById.values().stream()
                .filter(session -> session.getIsLocked() == null || session.getIsLocked() != ScheduleSession.LOCKED)
                .count());
        summary.setItemCount(plan.getItems().size());
        summary.setScheduledCount(plan.getScheduledCount());
        summary.setUnscheduledCount(plan.getUnscheduledCount());
        summary.setConflictCount(plan.getConflicts().size());
        summary.setErrorCount((int) plan.getConflicts().stream()
                .filter(conflict -> "ERROR".equalsIgnoreCase(conflict.getLevel())).count());
        summary.setWarnCount((int) plan.getConflicts().stream()
                .filter(conflict -> "WARN".equalsIgnoreCase(conflict.getLevel())).count());
        summary.setShortageCount(plan.getShortageMessages() == null ? 0 : plan.getShortageMessages().size());
        vo.setSummary(summary);
        return vo;
    }

    // ------------------------------------------------------------------
    // 落库
    // ------------------------------------------------------------------

    /**
     * 落库：锁定日程跳过；已有 {@code (meet_id, event_id, round_id)} 匹配则更新，否则插入。
     */
    private void applyPlan(Long meetId, SchedulePlan plan) {
        String batchNo = buildBatchNo();
        Map<String, Schedule> existing = new HashMap<>();
        for (Schedule schedule : dataProvider.listSchedules(meetId, null)) {
            existing.put(eventRoundKey(schedule.getEventId(), schedule.getRoundId()), schedule);
        }
        Map<Long, Integer> orderCursor = new HashMap<>();
        List<ScheduledItemResult> items = new ArrayList<>(plan.getItems());
        items.sort(java.util.Comparator
                .comparing((ScheduledItemResult item) -> item.getDate() == null
                        ? java.time.LocalDate.MAX : item.getDate())
                .thenComparing(item -> item.getStartTime() == null
                        ? java.time.LocalTime.MAX : item.getStartTime()));
        for (ScheduledItemResult item : items) {
            if (item.getEventId() == null || item.getRoundId() == null || item.getSessionId() == null
                    || item.getDate() == null || item.getStartTime() == null) {
                continue;
            }
            String key = eventRoundKey(item.getEventId(), item.getRoundId());
            Schedule matched = existing.get(key);
            if (matched != null && matched.getIsLocked() != null && matched.getIsLocked() == Schedule.LOCKED) {
                // 锁定日程不修改
                continue;
            }
            LocalDateTime start = LocalDateTime.of(item.getDate(), item.getStartTime());
            int plannedDuration = planDuration(item);
            LocalDateTime end = item.getEndTime() == null
                    ? start.plusMinutes(plannedDuration)
                    : LocalDateTime.of(item.getDate(), item.getEndTime());
            int duration = (int) java.time.Duration.between(start, end).toMinutes();
            int orderNo = orderCursor.merge(item.getSessionId(), 1, Integer::sum);
            if (matched == null) {
                Schedule entity = new Schedule();
                entity.setMeetId(meetId);
                entity.setSessionId(item.getSessionId());
                entity.setEventId(item.getEventId());
                entity.setRoundId(item.getRoundId());
                entity.setVenueId(item.getVenueId());
                entity.setPlanStartTime(start);
                entity.setPlanEndTime(end);
                entity.setDurationMin(duration <= 0 ? planDuration(item) : duration);
                entity.setHeatCount(Math.max(1, item.getHeatCount()));
                entity.setMemberCount(item.getParticipantCount());
                entity.setOrderNo(orderNo);
                entity.setSourceType(Schedule.SOURCE_AUTO);
                entity.setBatchNo(batchNo);
                entity.setIsLocked(Schedule.UNLOCKED);
                entity.setMoveCount(0);
                entity.setConflictFlag(0);
                entity.setConflictCount(0);
                entity.setPublishStatus(Schedule.PUBLISH_DRAFT);
                scheduleService.save(entity);
                existing.put(key, entity);
            } else {
                Schedule patch = new Schedule();
                patch.setId(matched.getId());
                patch.setSessionId(item.getSessionId());
                patch.setVenueId(item.getVenueId());
                patch.setPlanStartTime(start);
                patch.setPlanEndTime(end);
                patch.setDurationMin(duration <= 0 ? planDuration(item) : duration);
                patch.setHeatCount(Math.max(1, item.getHeatCount()));
                patch.setMemberCount(item.getParticipantCount());
                patch.setOrderNo(orderNo);
                patch.setBatchNo(batchNo);
                scheduleService.updateById(patch);
            }
        }
        // 单元统计回填
        Set<Long> sessionIds = items.stream()
                .map(ScheduledItemResult::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long sessionId : sessionIds) {
            scheduleService.refreshSessionStat(meetId, sessionId);
        }
    }

    private int planDuration(ScheduledItemResult item) {
        if (item.getDate() == null || item.getStartTime() == null || item.getEndTime() == null) {
            return 15;
        }
        long minutes = java.time.Duration.between(
                LocalDateTime.of(item.getDate(), item.getStartTime()),
                LocalDateTime.of(item.getDate(), item.getEndTime())).toMinutes();
        return minutes <= 0 ? 15 : (int) minutes;
    }

    // ------------------------------------------------------------------
    // 规则与工具
    // ------------------------------------------------------------------

    private int resolveAthleteIntervalMin(ScheduleAutoDTO request) {
        if (request.getAthleteIntervalMin() != null && request.getAthleteIntervalMin() > 0) {
            return request.getAthleteIntervalMin();
        }
        return cachedData.conflictIntervalMin();
    }

    private int resolveMinGapMin() {
        return cachedData.conflictIntervalMin();
    }

    private int resolveMaxFieldVenues() {
        return 2;
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return ScheduleAutoDTO.SCOPE_ALL;
        }
        String value = scope.trim().toUpperCase();
        if (ScheduleAutoDTO.SCOPE_SESSION.equals(value)
                || ScheduleAutoDTO.SCOPE_GROUP.equals(value)
                || ScheduleAutoDTO.SCOPE_EVENT.equals(value)) {
            return value;
        }
        return ScheduleAutoDTO.SCOPE_ALL;
    }

    private List<Long> distinct(List<Long> values) {
        return dataProvider.distinctIds(values);
    }

    private String eventRoundKey(Long eventId, Long roundId) {
        return eventId + "#" + roundId;
    }

    private String buildBatchNo() {
        return BATCH_PREFIX + LocalDateTime.now().format(BATCH_TIME_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
