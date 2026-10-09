package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.EventRound;
import com.trackmeet.modules.event.mapper.EventMapper;
import com.trackmeet.modules.event.mapper.EventRoundMapper;
import com.trackmeet.modules.schedule.dto.ScheduleMoveDTO;
import com.trackmeet.modules.schedule.dto.ScheduleQuery;
import com.trackmeet.modules.schedule.dto.ScheduleSaveDTO;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.entity.ScheduleSession;
import com.trackmeet.modules.schedule.mapper.FieldOrderMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.mapper.LaneMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import com.trackmeet.modules.schedule.service.ScheduleService;
import com.trackmeet.modules.schedule.service.ScheduleSessionService;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.service.support.ScheduleDataProvider;
import com.trackmeet.modules.schedule.vo.FieldOrderVO;
import com.trackmeet.modules.schedule.vo.GanttRowVO;
import com.trackmeet.modules.schedule.vo.HeatMemberVO;
import com.trackmeet.modules.schedule.vo.HeatVO;
import com.trackmeet.modules.schedule.vo.LaneVO;
import com.trackmeet.modules.schedule.vo.ScheduleVO;
import com.trackmeet.modules.schedule.vo.VenueBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 日程条目服务实现。
 * <p>
 * 规则要点：
 * <ol>
 *   <li><b>锁定保护</b>：{@code is_locked=1} 的日程不允许修改/删除/拖拽；</li>
 *   <li><b>派生字段</b>：{@code plan_end_time = plan_start_time + duration_min}、
 *       {@code order_no} 在单元内按时间顺序重排、单元统计（条目数/已用时长）在写操作后回填；</li>
 *   <li><b>跨模块校验</b>：单元/项目/轮次必须属于同一届次，避免编排数据跨届次串链；</li>
 *   <li><b>级联清理</b>：删除日程时物理清理分组 → 组内人员 → 道次/出场顺序，避免逻辑删除残留唯一键占位行。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl extends ServiceImpl<ScheduleMapper, Schedule> implements ScheduleService {

    /** 计划结束时间兜底时长（分钟），用于日程未配置预计时长时推导结束时间 */
    private static final int FALLBACK_DURATION_MIN = 15;

    /** 拖拽已锁定日程的提示文案（前端按此文案定位解锁入口） */
    private static final String LOCKED_MOVE_MESSAGE = "日程已锁定，请先解锁";

    private final ScheduleSessionService scheduleSessionService;
    private final ScheduleDataProvider dataProvider;
    private final EventMapper eventMapper;
    private final EventRoundMapper eventRoundMapper;
    private final HeatMapper heatMapper;
    private final HeatMemberMapper heatMemberMapper;
    private final LaneMapper laneMapper;
    private final FieldOrderMapper fieldOrderMapper;

    @Override
    public PageResult<ScheduleVO> pageSchedule(ScheduleQuery query) {
        ScheduleQuery condition = query == null ? new ScheduleQuery() : query;
        // 届次隔离：入参 meetId 优先，缺省取当前届次上下文，二者不一致按越权处理
        Long meetId = MeetGuard.resolve(condition.getMeetId());
        List<Long> sessionIds = resolveSessionIds(meetId, condition);
        if (sessionIds != null && sessionIds.isEmpty()) {
            return PageResult.empty(condition.pageNo(), condition.pageSize());
        }
        List<Long> eventIds = resolveEventIds(meetId, condition);
        if (eventIds != null && eventIds.isEmpty()) {
            return PageResult.empty(condition.pageNo(), condition.pageSize());
        }

        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Schedule::getMeetId, meetId)
                .eq(condition.getSessionId() != null, Schedule::getSessionId, condition.getSessionId())
                .eq(condition.getVenueId() != null, Schedule::getVenueId, condition.getVenueId())
                .eq(condition.getEventId() != null, Schedule::getEventId, condition.getEventId())
                .eq(condition.getRoundId() != null, Schedule::getRoundId, condition.getRoundId())
                .eq(StringUtils.hasText(condition.getPublishStatus()), Schedule::getPublishStatus,
                        condition.getPublishStatus())
                .eq(condition.getIsLocked() != null, Schedule::getIsLocked, condition.getIsLocked())
                .eq(condition.getConflictFlag() != null, Schedule::getConflictFlag, condition.getConflictFlag())
                .eq(StringUtils.hasText(condition.getSourceType()), Schedule::getSourceType, condition.getSourceType());
        if (sessionIds != null) {
            wrapper.in(Schedule::getSessionId, sessionIds);
        }
        if (eventIds != null) {
            wrapper.in(Schedule::getEventId, eventIds);
        }
        // date 按 plan_start_time 的日期过滤（含时分秒，避免与单元日期混用）
        if (condition.getDate() != null) {
            LocalDateTime from = condition.getDate().atStartOfDay();
            wrapper.ge(Schedule::getPlanStartTime, from)
                    .lt(Schedule::getPlanStartTime, from.plusDays(1));
        }
        wrapper.orderByAsc(Schedule::getPlanStartTime).orderByAsc(Schedule::getOrderNo).orderByAsc(Schedule::getId);

        Page<Schedule> page = this.page(condition.toPage(), wrapper);
        List<ScheduleVO> records = toVoList(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ScheduleVO getDetail(Long id) {
        Schedule schedule = getEntity(id);
        ScheduleVO vo = toVoList(java.util.Collections.singletonList(schedule)).get(0);
        Long meetId = schedule.getMeetId();
        List<HeatVO> heats = dataProvider.listHeats(meetId, id);
        if (!heats.isEmpty()) {
            List<Long> heatIds = dataProvider.heatIdsOf(heats);
            Map<Long, List<HeatMemberVO>> membersByHeat = dataProvider.listMembers(meetId, heatIds).stream()
                    .collect(Collectors.groupingBy(HeatMemberVO::getHeatId, LinkedHashMap::new, Collectors.toList()));
            Map<Long, List<LaneVO>> lanesByHeat = dataProvider.listLanes(meetId, heatIds).stream()
                    .collect(Collectors.groupingBy(LaneVO::getHeatId, LinkedHashMap::new, Collectors.toList()));
            Map<Long, List<FieldOrderVO>> ordersByHeat = dataProvider.listFieldOrders(meetId, heatIds).stream()
                    .collect(Collectors.groupingBy(FieldOrderVO::getHeatId, LinkedHashMap::new, Collectors.toList()));
            for (HeatVO heat : heats) {
                heat.setMembers(membersByHeat.getOrDefault(heat.getId(), new ArrayList<>()));
                heat.setLanes(lanesByHeat.getOrDefault(heat.getId(), new ArrayList<>()));
                heat.setFieldOrders(ordersByHeat.getOrDefault(heat.getId(), new ArrayList<>()));
            }
        }
        vo.setHeats(heats);
        return vo;
    }

    @Override
    public List<ScheduleVO> listDetail(Long meetId, List<Long> sessionIds) {
        Long resolvedMeetId = MeetGuard.resolve(meetId);
        List<Schedule> schedules = dataProvider.listSchedules(resolvedMeetId, sessionIds);
        return toVoList(schedules);
    }

    @Override
    public List<GanttRowVO> gantt(Long meetId, Long sessionId) {
        Long resolvedMeetId = MeetGuard.resolve(meetId);
        List<Long> sessionIds = sessionId == null ? null : new ArrayList<>(java.util.Collections.singletonList(sessionId));
        List<ScheduleVO> schedules = listDetail(resolvedMeetId, sessionIds);

        Map<Long, ScheduleSession> sessionMap = dataProvider.sessionMap(resolvedMeetId, null);
        Map<Long, VenueBriefVO> venueMap = dataProvider.venueMap(resolvedMeetId);

        // 先按单元归集，再在单元内按场地拆行（同一单元可有多个并行场地）
        Map<Long, List<ScheduleVO>> bySession = new LinkedHashMap<>();
        for (ScheduleVO vo : schedules) {
            bySession.computeIfAbsent(vo.getSessionId(), key -> new ArrayList<>()).add(vo);
        }
        if (sessionId != null && !bySession.containsKey(sessionId)) {
            bySession.put(sessionId, new ArrayList<>());
        }

        List<GanttRowVO> rows = new ArrayList<>();
        for (Map.Entry<Long, List<ScheduleVO>> entry : bySession.entrySet()) {
            ScheduleSession session = sessionMap.get(entry.getKey());
            if (session == null) {
                continue;
            }
            Map<Long, List<ScheduleVO>> byVenue = new LinkedHashMap<>();
            for (ScheduleVO vo : entry.getValue()) {
                byVenue.computeIfAbsent(vo.getVenueId(), key -> new ArrayList<>()).add(vo);
            }
            if (byVenue.isEmpty()) {
                byVenue.put(session.getVenueId(), new ArrayList<>());
            }
            for (Map.Entry<Long, List<ScheduleVO>> venueEntry : byVenue.entrySet()) {
                GanttRowVO row = new GanttRowVO();
                row.setSessionId(session.getId());
                row.setSessionName(session.getSessionName());
                row.setSessionDate(session.getSessionDate());
                row.setPeriod(session.getPeriod());
                row.setDayNo(session.getDayNo());
                row.setVenueId(venueEntry.getKey());
                VenueBriefVO venue = venueMap.get(venueEntry.getKey());
                row.setVenueName(venue == null ? null : venue.getVenueName());
                row.setStartTime(session.getStartTime());
                row.setEndTime(session.getEndTime());
                row.setTotalMinutes(session.getTotalMinutes());
                row.setUsedMinutes(session.getUsedMinutes());
                row.setItemCount(session.getItemCount());
                row.setIsLocked(session.getIsLocked());
                row.setPublishStatus(session.getPublishStatus());
                row.setSortNo(session.getSortNo());
                row.setRemark(session.getRemark());
                List<GanttRowVO.Bar> bars = new ArrayList<>();
                for (ScheduleVO vo : venueEntry.getValue()) {
                    bars.add(toBar(vo));
                }
                bars.sort(Comparator
                        .comparing((GanttRowVO.Bar bar) -> bar.getPlanStartTime() == null
                                ? LocalDateTime.MAX : bar.getPlanStartTime())
                        .thenComparing(bar -> bar.getOrderNo() == null ? Integer.MAX_VALUE : bar.getOrderNo()));
                row.setBars(bars);
                rows.add(row);
            }
        }
        rows.sort(Comparator
                .comparing((GanttRowVO row) -> row.getSessionDate() == null
                        ? java.time.LocalDate.MAX : row.getSessionDate())
                .thenComparing(row -> row.getSortNo() == null ? Integer.MAX_VALUE : row.getSortNo())
                .thenComparing(row -> row.getVenueId() == null ? Long.MAX_VALUE : row.getVenueId()));
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSchedule(ScheduleSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程参数不能为空");
        }
        Long meetId = MeetGuard.resolve(dto.getMeetId());
        ScheduleSession session = scheduleSessionService.getEntity(dto.getSessionId());
        requireSameMeet(meetId, session.getMeetId(), "竞赛单元");
        Event event = requireEvent(meetId, dto.getEventId());
        requireRound(meetId, dto.getEventId(), dto.getRoundId());

        Schedule entity = new Schedule();
        entity.setMeetId(meetId);
        entity.setSessionId(session.getId());
        entity.setEventId(dto.getEventId());
        entity.setRoundId(dto.getRoundId());
        entity.setVenueId(dto.getVenueId() == null ? session.getVenueId() : dto.getVenueId());
        entity.setPlanStartTime(dto.getPlanStartTime());
        entity.setDurationMin(dto.getDurationMin() == null
                ? (event.getDurationMin() == null ? FALLBACK_DURATION_MIN : event.getDurationMin())
                : dto.getDurationMin());
        entity.setPlanEndTime(dto.getPlanEndTime() == null
                ? plusMinutes(dto.getPlanStartTime(), entity.getDurationMin())
                : dto.getPlanEndTime());
        entity.setHeatCount(dto.getHeatCount());
        entity.setMemberCount(dto.getMemberCount());
        entity.setSourceType(StringUtils.hasText(dto.getSourceType()) ? dto.getSourceType() : Schedule.SOURCE_MANUAL);
        entity.setIsLocked(Schedule.UNLOCKED);
        entity.setMoveCount(0);
        entity.setConflictFlag(0);
        entity.setConflictCount(0);
        entity.setPublishStatus(Schedule.PUBLISH_DRAFT);
        entity.setRemark(dto.getRemark());
        entity.setOrderNo(dto.getOrderNo() == null ? nextOrderNoValue(meetId, session.getId()) : dto.getOrderNo());
        this.save(entity);
        refreshSessionStat(meetId, session.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSchedule(Long id, ScheduleSaveDTO dto) {
        Schedule exists = getEntity(id);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程参数不能为空");
        }
        if (dto.getMeetId() != null && !Objects.equals(dto.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改日程");
        }
        requireUnlocked(exists, "修改");
        Long sessionId = dto.getSessionId() == null ? exists.getSessionId() : dto.getSessionId();
        Long roundId = dto.getRoundId() == null ? exists.getRoundId() : dto.getRoundId();
        Long eventId = dto.getEventId() == null ? exists.getEventId() : dto.getEventId();
        ScheduleSession session = scheduleSessionService.getEntity(sessionId);
        requireSameMeet(exists.getMeetId(), session.getMeetId(), "竞赛单元");
        requireEvent(exists.getMeetId(), eventId);
        requireRound(exists.getMeetId(), eventId, roundId);

        Schedule entity = new Schedule();
        entity.setId(id);
        entity.setVersion(exists.getVersion());
        entity.setMeetId(exists.getMeetId());
        entity.setSessionId(sessionId);
        entity.setEventId(eventId);
        entity.setRoundId(roundId);
        entity.setVenueId(dto.getVenueId());
        entity.setPlanStartTime(dto.getPlanStartTime());
        entity.setDurationMin(dto.getDurationMin());
        entity.setPlanEndTime(dto.getPlanEndTime());
        entity.setHeatCount(dto.getHeatCount());
        entity.setMemberCount(dto.getMemberCount());
        entity.setOrderNo(dto.getOrderNo());
        entity.setSourceType(StringUtils.hasText(dto.getSourceType()) ? dto.getSourceType() : null);
        entity.setRemark(dto.getRemark());
        if (entity.getPlanStartTime() != null) {
            int duration = entity.getDurationMin() != null
                    ? entity.getDurationMin()
                    : (exists.getDurationMin() == null ? FALLBACK_DURATION_MIN : exists.getDurationMin());
            entity.setDurationMin(duration);
            if (entity.getPlanEndTime() == null) {
                entity.setPlanEndTime(plusMinutes(entity.getPlanStartTime(), duration));
            }
        }
        if (!this.updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "日程已被其他用户修改，请刷新后重试");
        }
        if (!Objects.equals(exists.getSessionId(), sessionId)) {
            refreshSessionStat(exists.getMeetId(), exists.getSessionId());
        }
        refreshSessionStat(exists.getMeetId(), sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSchedule(Long id) {
        Schedule exists = getEntity(id);
        requireUnlocked(exists, "删除");
        List<Heat> heats = heatMapper.selectList(new LambdaQueryWrapper<Heat>()
                .eq(Heat::getMeetId, exists.getMeetId())
                .eq(Heat::getScheduleId, id));
        if (!heats.isEmpty()) {
            List<Long> heatIds = heats.stream().map(Heat::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (!heatIds.isEmpty()) {
                heatMemberMapper.deleteByHeatIdsPhysically(heatIds);
                laneMapper.deleteByHeatIdsPhysically(heatIds);
                fieldOrderMapper.deleteByHeatIdsPhysically(heatIds);
            }
            heatMapper.deleteBySchedulePhysically(exists.getMeetId(), id);
        }
        laneMapper.deleteBySchedulePhysically(exists.getMeetId(), id);
        fieldOrderMapper.deleteBySchedulePhysically(exists.getMeetId(), id);
        heatMemberMapper.deleteBySchedulePhysically(exists.getMeetId(), id);
        this.removeById(id);
        refreshSessionStat(exists.getMeetId(), exists.getSessionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveSchedule(Long id, ScheduleMoveDTO dto) {
        Schedule exists = getEntity(id);
        if (exists.getIsLocked() != null && exists.getIsLocked() == Schedule.LOCKED) {
            throw new BizException(ResultCode.CONFLICT, LOCKED_MOVE_MESSAGE);
        }
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "移动参数不能为空");
        }
        Long sourceSessionId = exists.getSessionId();
        Long targetSessionId = dto.getSessionId() == null ? sourceSessionId : dto.getSessionId();
        ScheduleSession targetSession = scheduleSessionService.getEntity(targetSessionId);
        requireSameMeet(exists.getMeetId(), targetSession.getMeetId(), "竞赛单元");

        LocalDateTime startTime = dto.getStartTime() == null ? exists.getPlanStartTime() : dto.getStartTime();
        int duration = exists.getDurationMin() == null ? FALLBACK_DURATION_MIN : exists.getDurationMin();
        Long venueId = dto.getVenueId() == null ? exists.getVenueId() : dto.getVenueId();

        LambdaUpdateWrapper<Schedule> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Schedule::getId, id)
                .set(Schedule::getSessionId, targetSessionId)
                .set(Schedule::getPlanStartTime, startTime)
                .set(Schedule::getPlanEndTime, plusMinutes(startTime, duration))
                .set(Schedule::getVenueId, venueId)
                .set(Schedule::getAdjustReason, dto.getAdjustReason())
                .set(Schedule::getMoveCount, (exists.getMoveCount() == null ? 0 : exists.getMoveCount()) + 1);
        if (!this.update(wrapper)) {
            throw new BizException(ResultCode.CONFLICT, "日程移动失败，请刷新后重试");
        }
        refreshSessionStat(exists.getMeetId(), sourceSessionId);
        if (!Objects.equals(sourceSessionId, targetSessionId)) {
            refreshSessionStat(exists.getMeetId(), targetSessionId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockSchedule(Long id, Integer isLocked) {
        Schedule exists = getEntity(id);
        int locked = isLocked != null && isLocked == Schedule.LOCKED ? Schedule.LOCKED : Schedule.UNLOCKED;
        LambdaUpdateWrapper<Schedule> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Schedule::getId, id).set(Schedule::getIsLocked, locked);
        if (locked == Schedule.LOCKED) {
            wrapper.set(Schedule::getLockedBy, SecurityUtils.getUserId())
                    .set(Schedule::getLockTime, LocalDateTime.now());
        } else {
            wrapper.set(Schedule::getLockedBy, null).set(Schedule::getLockTime, null);
        }
        if (!this.update(wrapper)) {
            throw new BizException(ResultCode.CONFLICT, "日程锁定状态更新失败，请刷新后重试");
        }
        if (exists.getMeetId() != null && exists.getSessionId() != null) {
            refreshSessionStat(exists.getMeetId(), exists.getSessionId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishSchedule(Long id, String publishStatus) {
        Schedule exists = getEntity(id);
        String status = normalizePublishStatus(publishStatus);
        LambdaUpdateWrapper<Schedule> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Schedule::getId, id).set(Schedule::getPublishStatus, status);
        if (Schedule.PUBLISH_PUBLISHED.equals(status)) {
            wrapper.set(Schedule::getPublishTime, LocalDateTime.now())
                    .set(Schedule::getPublisherId, SecurityUtils.getUserId());
        } else {
            wrapper.set(Schedule::getPublishTime, null).set(Schedule::getPublisherId, null);
        }
        if (!this.update(wrapper)) {
            throw new BizException(ResultCode.CONFLICT, "日程发布状态更新失败，请刷新后重试");
        }
        // 单元发布状态为单元内日程的派生值：全部发布即已发布
        syncSessionPublishStatus(exists.getMeetId(), exists.getSessionId());
    }

    @Override
    public Schedule getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程 ID 不能为空");
        }
        Schedule schedule = this.getById(id);
        if (schedule == null) {
            throw new BizException(ResultCode.NOT_FOUND, "日程条目不存在");
        }
        MeetGuard.check(schedule.getMeetId());
        return schedule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshSessionStat(Long meetId, Long sessionId) {
        if (meetId == null || sessionId == null) {
            return;
        }
        List<Schedule> schedules = this.list(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMeetId, meetId)
                .eq(Schedule::getSessionId, sessionId));
        int usedMinutes = 0;
        for (Schedule schedule : schedules) {
            Integer duration = schedule.getDurationMin();
            usedMinutes += duration == null ? 0 : Math.max(0, duration);
        }
        ScheduleSession patch = new ScheduleSession();
        patch.setId(sessionId);
        patch.setItemCount(schedules.size());
        patch.setUsedMinutes(usedMinutes);
        scheduleSessionService.updateById(patch);
        recalcOrderNo(meetId, sessionId);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 单元内排序号：按 计划开始时间 → 原排序号 重排为 1..N。
     */
    private void recalcOrderNo(Long meetId, Long sessionId) {
        List<Schedule> schedules = this.list(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMeetId, meetId)
                .eq(Schedule::getSessionId, sessionId));
        schedules.sort(Comparator
                .comparing((Schedule schedule) -> schedule.getPlanStartTime() == null
                        ? LocalDateTime.MAX : schedule.getPlanStartTime())
                .thenComparing(schedule -> schedule.getOrderNo() == null ? Integer.MAX_VALUE : schedule.getOrderNo())
                .thenComparing(schedule -> schedule.getId() == null ? Long.MAX_VALUE : schedule.getId()));
        int orderNo = 1;
        for (Schedule schedule : schedules) {
            if (Objects.equals(schedule.getOrderNo(), orderNo)) {
                orderNo++;
                continue;
            }
            LambdaUpdateWrapper<Schedule> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Schedule::getId, schedule.getId()).set(Schedule::getOrderNo, orderNo++);
            this.update(wrapper);
        }
    }

    /**
     * 单元发布状态同步：单元内存在草稿日程时回到 DRAFT，全部发布时置 PUBLISHED。
     */
    private void syncSessionPublishStatus(Long meetId, Long sessionId) {
        if (meetId == null || sessionId == null) {
            return;
        }
        List<Schedule> schedules = this.list(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMeetId, meetId)
                .eq(Schedule::getSessionId, sessionId));
        if (schedules.isEmpty()) {
            // 单元内无日程时保持原发布状态，不凭空置为已发布
            return;
        }
        boolean allPublished = schedules.stream()
                .allMatch(schedule -> Schedule.PUBLISH_PUBLISHED.equals(schedule.getPublishStatus()));
        ScheduleSession patch = new ScheduleSession();
        patch.setId(sessionId);
        if (allPublished) {
            patch.setPublishStatus(ScheduleSession.PUBLISH_PUBLISHED);
            patch.setPublishTime(LocalDateTime.now());
            patch.setPublisherId(SecurityUtils.getUserId());
        } else {
            patch.setPublishStatus(ScheduleSession.PUBLISH_DRAFT);
        }
        scheduleSessionService.updateById(patch);
    }

    private void requireUnlocked(Schedule schedule, String action) {
        if (schedule.getIsLocked() != null && schedule.getIsLocked() == Schedule.LOCKED) {
            throw new BizException(ResultCode.CONFLICT, "日程已锁定，不允许" + action + "，请先解锁");
        }
    }

    private void requireSameMeet(Long expected, Long actual, String target) {
        if (actual != null && !Objects.equals(expected, actual)) {
            throw new BizException(ResultCode.PARAM_ERROR, target + "不属于当前届次");
        }
    }

    private Event requireEvent(Long meetId, Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || !Objects.equals(event.getMeetId(), meetId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目不存在或不属于当前届次");
        }
        return event;
    }

    private EventRound requireRound(Long meetId, Long eventId, Long roundId) {
        EventRound round = eventRoundMapper.selectById(roundId);
        if (round == null || !Objects.equals(round.getEventId(), eventId) || !Objects.equals(round.getMeetId(), meetId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次不存在或不属于该项目");
        }
        return round;
    }

    private String normalizePublishStatus(String publishStatus) {
        if (!StringUtils.hasText(publishStatus)) {
            throw new BizException(ResultCode.PARAM_ERROR, "发布状态不能为空");
        }
        String status = publishStatus.trim().toUpperCase();
        if (!Schedule.PUBLISH_PUBLISHED.equals(status) && !Schedule.PUBLISH_DRAFT.equals(status)) {
            throw new BizException(ResultCode.PARAM_ERROR, "发布状态只能是 PUBLISHED 或 DRAFT");
        }
        return status;
    }

    private int nextOrderNoValue(Long meetId, Long sessionId) {
        List<Schedule> schedules = this.list(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMeetId, meetId)
                .eq(Schedule::getSessionId, sessionId));
        int max = 0;
        for (Schedule schedule : schedules) {
            int orderNo = schedule.getOrderNo() == null ? 0 : schedule.getOrderNo();
            max = Math.max(max, orderNo);
        }
        return max + 1;
    }

    private LocalDateTime plusMinutes(LocalDateTime start, Integer minutes) {
        if (start == null) {
            return null;
        }
        int value = minutes == null || minutes <= 0 ? FALLBACK_DURATION_MIN : minutes;
        return start.plusMinutes(value);
    }

    /**
     * 查询条件解析：date / groupId / keyword 需要先在 event / schedule_session 上求解 ID 集合。
     */
    private List<Long> resolveSessionIds(Long meetId, ScheduleQuery condition) {
        if (condition.getDate() == null) {
            return null;
        }
        LambdaQueryWrapper<ScheduleSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleSession::getMeetId, meetId)
                .eq(ScheduleSession::getSessionDate, condition.getDate());
        return scheduleSessionService.list(wrapper).stream()
                .map(ScheduleSession::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Long> resolveEventIds(Long meetId, ScheduleQuery condition) {
        boolean byGroup = condition.getGroupId() != null;
        boolean byKeyword = StringUtils.hasText(condition.getKeyword());
        if (!byGroup && !byKeyword) {
            return null;
        }
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Event::getMeetId, meetId)
                .eq(byGroup, Event::getGroupId, condition.getGroupId());
        if (byKeyword) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(w -> w.like(Event::getEventName, keyword).or().like(Event::getEventCode, keyword));
        }
        List<Long> eventIds = eventMapper.selectList(wrapper).stream()
                .map(Event::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (byKeyword) {
            // 轮次码命中同样视为匹配（keyword 语义覆盖项目名与轮次码）
            List<Long> roundEventIds = eventRoundMapper.selectList(new LambdaQueryWrapper<EventRound>()
                            .eq(EventRound::getMeetId, meetId)
                            .like(EventRound::getRoundCode, condition.getKeyword().trim()))
                    .stream().map(EventRound::getEventId).filter(Objects::nonNull).collect(Collectors.toList());
            for (Long eventId : roundEventIds) {
                if (!eventIds.contains(eventId)) {
                    eventIds.add(eventId);
                }
            }
        }
        return eventIds;
    }

    /**
     * 实体列表 → 视图列表（批量装配单元、项目、轮次、场地冗余字段，避免 N+1）。
     */
    private List<ScheduleVO> toVoList(List<Schedule> schedules) {
        List<ScheduleVO> result = new ArrayList<>();
        if (schedules == null || schedules.isEmpty()) {
            return result;
        }
        Long meetId = schedules.get(0).getMeetId();
        Collection<Long> sessionIds = schedules.stream().map(Schedule::getSessionId).filter(Objects::nonNull)
                .collect(Collectors.toList());
        Collection<Long> eventIds = schedules.stream().map(Schedule::getEventId).filter(Objects::nonNull)
                .collect(Collectors.toList());
        Collection<Long> roundIds = schedules.stream().map(Schedule::getRoundId).filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, ScheduleSession> sessionMap = dataProvider.sessionMap(meetId, sessionIds);
        Map<Long, VenueBriefVO> venueMap = dataProvider.venueMap(meetId);
        Map<Long, Event> eventMap = new HashMap<>();
        if (!eventIds.isEmpty()) {
            for (Event event : eventMapper.selectBatchIds(eventIds)) {
                eventMap.put(event.getId(), event);
            }
        }
        Map<Long, EventRound> roundMap = new HashMap<>();
        if (!roundIds.isEmpty()) {
            for (EventRound round : eventRoundMapper.selectBatchIds(roundIds)) {
                roundMap.put(round.getId(), round);
            }
        }

        for (Schedule schedule : schedules) {
            ScheduleVO vo = new ScheduleVO();
            vo.setId(schedule.getId());
            vo.setMeetId(schedule.getMeetId());
            vo.setSessionId(schedule.getSessionId());
            ScheduleSession session = sessionMap.get(schedule.getSessionId());
            if (session != null) {
                vo.setSessionName(session.getSessionName());
                vo.setSessionDate(session.getSessionDate());
                vo.setPeriod(session.getPeriod());
            }
            vo.setEventId(schedule.getEventId());
            Event event = eventMap.get(schedule.getEventId());
            if (event != null) {
                vo.setEventName(event.getEventName());
                vo.setEventType(event.getEventType());
                vo.setSubType(event.getSubType());
                vo.setGroupId(event.getGroupId());
                vo.setTrack(Event.TYPE_TRACK.equalsIgnoreCase(event.getEventType())
                        || Event.TYPE_RELAY.equalsIgnoreCase(event.getEventType()));
            }
            vo.setRoundId(schedule.getRoundId());
            EventRound round = roundMap.get(schedule.getRoundId());
            if (round != null) {
                vo.setRoundCode(round.getRoundCode());
                vo.setRoundNo(round.getRoundNo() == null ? null : round.getRoundNo().shortValue());
            }
            vo.setVenueId(schedule.getVenueId());
            VenueBriefVO venue = venueMap.get(schedule.getVenueId());
            if (venue != null) {
                vo.setVenueName(venue.getVenueName());
            }
            vo.setPlanStartTime(schedule.getPlanStartTime());
            vo.setPlanEndTime(schedule.getPlanEndTime());
            vo.setStartTime(localTime(schedule.getPlanStartTime()));
            vo.setEndTime(localTime(schedule.getPlanEndTime()));
            vo.setActualStartTime(schedule.getActualStartTime());
            vo.setActualEndTime(schedule.getActualEndTime());
            vo.setDurationMin(schedule.getDurationMin());
            vo.setActualDurationMin(schedule.getActualDurationMin());
            vo.setHeatCount(schedule.getHeatCount());
            vo.setMemberCount(schedule.getMemberCount());
            vo.setOrderNo(schedule.getOrderNo());
            vo.setSourceType(schedule.getSourceType());
            vo.setBatchNo(schedule.getBatchNo());
            vo.setIsLocked(schedule.getIsLocked());
            vo.setLockedBy(schedule.getLockedBy());
            vo.setLockTime(schedule.getLockTime());
            vo.setMoveCount(schedule.getMoveCount());
            vo.setAdjustReason(schedule.getAdjustReason());
            vo.setPublishStatus(schedule.getPublishStatus());
            vo.setPublishTime(schedule.getPublishTime());
            vo.setPublisherId(schedule.getPublisherId());
            vo.setConflictFlag(schedule.getConflictFlag());
            vo.setConflictCount(schedule.getConflictCount());
            vo.setRemark(schedule.getRemark());
            vo.setCreateTime(schedule.getCreateTime());
            vo.setUpdateTime(schedule.getUpdateTime());
            result.add(vo);
        }
        return result;
    }

    private GanttRowVO.Bar toBar(ScheduleVO vo) {
        GanttRowVO.Bar bar = new GanttRowVO.Bar();
        bar.setScheduleId(vo.getId());
        bar.setEventId(vo.getEventId());
        bar.setEventName(vo.getEventName());
        bar.setEventType(vo.getEventType());
        bar.setRoundId(vo.getRoundId());
        bar.setRoundCode(vo.getRoundCode());
        bar.setVenueId(vo.getVenueId());
        bar.setVenueName(vo.getVenueName());
        bar.setPlanStartTime(vo.getPlanStartTime());
        bar.setPlanEndTime(vo.getPlanEndTime());
        bar.setStartTime(vo.getStartTime());
        bar.setEndTime(vo.getEndTime());
        bar.setDurationMin(vo.getDurationMin());
        bar.setHeatCount(vo.getHeatCount());
        bar.setMemberCount(vo.getMemberCount());
        bar.setOrderNo(vo.getOrderNo());
        bar.setIsLocked(vo.getIsLocked());
        bar.setPublishStatus(vo.getPublishStatus());
        bar.setConflictFlag(vo.getConflictFlag());
        bar.setConflictCount(vo.getConflictCount());
        bar.setTrack(vo.getTrack());
        return bar;
    }

    private LocalTime localTime(LocalDateTime value) {
        return value == null ? null : value.toLocalTime().withSecond(0).withNano(0);
    }
}
