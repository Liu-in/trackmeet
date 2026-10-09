package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.meet.entity.Meet;
import com.trackmeet.modules.meet.entity.Venue;
import com.trackmeet.modules.meet.mapper.MeetMapper;
import com.trackmeet.modules.meet.mapper.VenueMapper;
import com.trackmeet.modules.schedule.dto.SessionQuery;
import com.trackmeet.modules.schedule.dto.SessionSaveDTO;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.entity.ScheduleSession;
import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleSessionMapper;
import com.trackmeet.modules.schedule.service.ScheduleSessionService;
import com.trackmeet.modules.schedule.service.support.JsonTextUtils;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 竞赛单元服务实现。
 * <p>
 * 规则要点：
 * <ol>
 *   <li>唯一性：{@code uk_session_meet_date_period(meet_id, session_date, period)}，同一天同一时段只允许一个单元；</li>
 *   <li>派生字段：{@code day_no} 按届次开始日期推算、{@code total_minutes} 按起止时刻推算；</li>
 *   <li>锁定保护：{@code is_locked=1} 的单元不允许删除，修改时仅接受锁定/发布字段（避免改动编排容器）；</li>
 *   <li>届次隔离：MyBatis-Plus 多租户拦截器按 {@code MeetContext} 追加 {@code meet_id}，服务层再做一致性核对。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class ScheduleSessionServiceImpl extends ServiceImpl<ScheduleSessionMapper, ScheduleSession>
        implements ScheduleSessionService {

    /** 单元可用总时长兜底（分钟）：起止时刻缺失时按半天 4 小时计 */
    private static final int DEFAULT_TOTAL_MINUTES = 240;

    /** 时段兜底排序权重 */
    private static final Map<String, Integer> PERIOD_ORDER = Map.of(
            ScheduleSession.PERIOD_MORNING, 1,
            ScheduleSession.PERIOD_AFTERNOON, 2,
            ScheduleSession.PERIOD_EVENING, 3);

    private final ScheduleMapper scheduleMapper;
    private final VenueMapper venueMapper;
    private final MeetMapper meetMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<SessionVO> pageSession(SessionQuery query) {
        SessionQuery condition = query == null ? new SessionQuery() : query;
        // 届次隔离：入参 meetId 优先，缺省取当前届次上下文，二者不一致按越权处理
        Long meetId = MeetGuard.resolve(condition.getMeetId());
        LambdaQueryWrapper<ScheduleSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleSession::getMeetId, meetId)
                .eq(condition.getDate() != null, ScheduleSession::getSessionDate, condition.getDate())
                .eq(StringUtils.hasText(condition.getPeriod()), ScheduleSession::getPeriod, condition.getPeriod())
                .eq(condition.getVenueId() != null, ScheduleSession::getVenueId, condition.getVenueId())
                .eq(condition.getDayNo() != null, ScheduleSession::getDayNo, condition.getDayNo())
                .eq(StringUtils.hasText(condition.getPublishStatus()), ScheduleSession::getPublishStatus,
                        condition.getPublishStatus())
                .eq(condition.getIsLocked() != null, ScheduleSession::getIsLocked, condition.getIsLocked());
        if (StringUtils.hasText(condition.getKeyword())) {
            wrapper.like(ScheduleSession::getSessionName, condition.getKeyword().trim());
        }
        wrapper.orderByAsc(ScheduleSession::getSessionDate)
                .orderByAsc(ScheduleSession::getSortNo)
                .orderByAsc(ScheduleSession::getId);

        Page<ScheduleSession> page = this.page(condition.toPage(), wrapper);
        List<SessionVO> records = toVoList(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<ScheduleSession> listSessions(Long meetId, List<Long> sessionIds, boolean onlyUnlocked) {
        Long resolvedMeetId = MeetGuard.resolve(meetId);
        LambdaQueryWrapper<ScheduleSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleSession::getMeetId, resolvedMeetId);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            List<Long> ids = sessionIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (ids.isEmpty()) {
                return new ArrayList<>();
            }
            wrapper.in(ScheduleSession::getId, ids);
        }
        if (onlyUnlocked) {
            wrapper.and(w -> w.isNull(ScheduleSession::getIsLocked)
                    .or().ne(ScheduleSession::getIsLocked, ScheduleSession.LOCKED));
        }
        wrapper.orderByAsc(ScheduleSession::getSessionDate)
                .orderByAsc(ScheduleSession::getSortNo)
                .orderByAsc(ScheduleSession::getId);

        List<ScheduleSession> sessions = this.list(wrapper);
        sessions.sort((first, second) -> {
            int byDate = compare(first.getSessionDate(), second.getSessionDate());
            if (byDate != 0) {
                return byDate;
            }
            int byPeriod = Integer.compare(periodOrder(first.getPeriod()), periodOrder(second.getPeriod()));
            if (byPeriod != 0) {
                return byPeriod;
            }
            int byStart = compare(first.getStartTime(), second.getStartTime());
            if (byStart != 0) {
                return byStart;
            }
            return compare(first.getId(), second.getId());
        });
        return sessions;
    }

    @Override
    public ScheduleSession getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "竞赛单元 ID 不能为空");
        }
        ScheduleSession session = this.getById(id);
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "竞赛单元不存在");
        }
        MeetGuard.check(session.getMeetId());
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSession(SessionSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "竞赛单元参数不能为空");
        }
        Long meetId = MeetGuard.resolve(dto.getMeetId());
        checkPeriod(dto.getPeriod());
        checkTimeRange(dto.getStartTime(), dto.getEndTime());
        checkUnique(meetId, dto.getSessionDate(), dto.getPeriod(), null);

        ScheduleSession entity = new ScheduleSession();
        entity.setMeetId(meetId);
        applySaveDto(entity, dto);
        if (entity.getDayNo() == null) {
            entity.setDayNo(resolveDayNo(meetId, dto.getSessionDate()));
        }
        if (entity.getTotalMinutes() == null) {
            entity.setTotalMinutes(resolveTotalMinutes(dto.getStartTime(), dto.getEndTime()));
        }
        if (!StringUtils.hasText(entity.getPublishStatus())) {
            entity.setPublishStatus(ScheduleSession.PUBLISH_DRAFT);
        }
        if (entity.getIsLocked() == null) {
            entity.setIsLocked(ScheduleSession.UNLOCKED);
        }
        if (entity.getSortNo() == null) {
            entity.setSortNo(nextSortNo(meetId));
        }
        applyLockTrace(entity, entity.getIsLocked());
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSession(Long id, SessionSaveDTO dto) {
        ScheduleSession exists = getEntity(id);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "竞赛单元参数不能为空");
        }
        if (dto.getMeetId() != null && !Objects.equals(dto.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改竞赛单元");
        }
        if (dto.getSessionDate() != null && !Objects.equals(dto.getSessionDate(), exists.getSessionDate())) {
            checkUnitMutable(exists, "单元日期");
        }
        if (StringUtils.hasText(dto.getPeriod()) && !dto.getPeriod().equalsIgnoreCase(exists.getPeriod())) {
            checkUnitMutable(exists, "时段");
        }
        checkPeriod(dto.getPeriod());
        checkTimeRange(dto.getStartTime(), dto.getEndTime());
        checkUnique(exists.getMeetId(), dto.getSessionDate(), dto.getPeriod(), id);

        ScheduleSession entity = new ScheduleSession();
        entity.setId(id);
        entity.setMeetId(exists.getMeetId());
        entity.setVersion(exists.getVersion());
        applySaveDto(entity, dto);
        if (entity.getDayNo() == null && dto.getSessionDate() != null) {
            entity.setDayNo(resolveDayNo(exists.getMeetId(), dto.getSessionDate()));
        }
        if (entity.getTotalMinutes() == null
                && (dto.getStartTime() != null || dto.getEndTime() != null)) {
            LocalTime start = dto.getStartTime() == null ? exists.getStartTime() : dto.getStartTime();
            LocalTime end = dto.getEndTime() == null ? exists.getEndTime() : dto.getEndTime();
            entity.setTotalMinutes(resolveTotalMinutes(start, end));
        }
        if (entity.getIsLocked() != null) {
            applyLockTrace(entity, entity.getIsLocked());
        }
        if (!this.updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "竞赛单元已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long id) {
        ScheduleSession exists = getEntity(id);
        if (exists.getIsLocked() != null && exists.getIsLocked() == ScheduleSession.LOCKED) {
            throw new BizException(ResultCode.CONFLICT, "竞赛单元已锁定，请先解锁");
        }
        Long count = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMeetId, exists.getMeetId())
                .eq(Schedule::getSessionId, id));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该单元下存在日程条目（" + count + " 条），禁止删除；请先移除对应日程");
        }
        this.removeById(id);
    }

    @Override
    public List<SessionVO> toVoList(List<ScheduleSession> sessions) {
        List<SessionVO> result = new ArrayList<>();
        if (sessions == null || sessions.isEmpty()) {
            return result;
        }
        Map<Long, String> venueNames = loadVenueNames(sessions);
        for (ScheduleSession session : sessions) {
            result.add(toVo(session, venueNames));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private SessionVO toVo(ScheduleSession session, Map<Long, String> venueNames) {
        SessionVO vo = new SessionVO();
        vo.setId(session.getId());
        vo.setMeetId(session.getMeetId());
        vo.setSessionName(session.getSessionName());
        vo.setSessionDate(session.getSessionDate());
        vo.setDayNo(session.getDayNo());
        vo.setPeriod(session.getPeriod());
        vo.setStartTime(session.getStartTime());
        vo.setEndTime(session.getEndTime());
        vo.setVenueId(session.getVenueId());
        vo.setVenueName(session.getVenueId() == null ? null : venueNames.get(session.getVenueId()));
        List<Long> venueIds = JsonTextUtils.parseLongArray(objectMapper, session.getVenueIdsJson());
        if (venueIds.isEmpty() && session.getVenueId() != null) {
            venueIds = new ArrayList<>(Collections.singletonList(session.getVenueId()));
        }
        vo.setVenueIds(venueIds);
        vo.setVenueNames(venueIds.stream()
                .map(venueId -> venueNames.getOrDefault(venueId, null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        vo.setVenueIdsJson(session.getVenueIdsJson());
        vo.setTotalMinutes(session.getTotalMinutes());
        vo.setUsedMinutes(session.getUsedMinutes());
        vo.setItemCount(session.getItemCount());
        vo.setIsLocked(session.getIsLocked());
        vo.setLockedBy(session.getLockedBy());
        vo.setLockTime(session.getLockTime());
        vo.setPublishStatus(session.getPublishStatus());
        vo.setPublishTime(session.getPublishTime());
        vo.setPublisherId(session.getPublisherId());
        vo.setSortNo(session.getSortNo());
        vo.setBatchNo(session.getBatchNo());
        vo.setRemark(session.getRemark());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }

    /**
     * 批量取场地名称（主场地 + 可用场地），避免逐行查询。
     */
    private Map<Long, String> loadVenueNames(List<ScheduleSession> sessions) {
        Set<Long> venueIds = new LinkedHashSet<>();
        for (ScheduleSession session : sessions) {
            if (session.getVenueId() != null) {
                venueIds.add(session.getVenueId());
            }
            for (Long venueId : JsonTextUtils.parseLongArray(objectMapper, session.getVenueIdsJson())) {
                venueIds.add(venueId);
            }
        }
        if (venueIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> names = new HashMap<>();
        for (Venue venue : venueMapper.selectBatchIds(venueIds)) {
            names.put(venue.getId(), venue.getVenueName());
        }
        return names;
    }

    private void applySaveDto(ScheduleSession entity, SessionSaveDTO dto) {
        if (dto.getSessionName() != null) {
            entity.setSessionName(dto.getSessionName().trim());
        }
        entity.setSessionDate(dto.getSessionDate());
        entity.setDayNo(dto.getDayNo());
        if (StringUtils.hasText(dto.getPeriod())) {
            entity.setPeriod(dto.getPeriod().trim().toUpperCase());
        }
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setVenueId(dto.getVenueId());
        if (dto.getVenueIds() != null) {
            entity.setVenueIdsJson(JsonTextUtils.writeLongArray(objectMapper, dto.getVenueIds()));
        }
        entity.setTotalMinutes(dto.getTotalMinutes());
        entity.setIsLocked(dto.getIsLocked());
        entity.setSortNo(dto.getSortNo());
        if (StringUtils.hasText(dto.getBatchNo())) {
            entity.setBatchNo(dto.getBatchNo().trim());
        }
        entity.setRemark(dto.getRemark());
    }

    /**
     * 锁定留痕：解锁时清空锁定人/锁定时间，锁定且无留痕时补齐。
     */
    private void applyLockTrace(ScheduleSession entity, Integer isLocked) {
        if (isLocked == null) {
            return;
        }
        if (isLocked == ScheduleSession.LOCKED) {
            entity.setLockedBy(SecurityUtils.getUserId());
            entity.setLockTime(LocalDateTime.now());
        } else {
            entity.setLockedBy(null);
            entity.setLockTime(null);
        }
    }

    private void checkUnitMutable(ScheduleSession exists, String field) {
        if (exists.getIsLocked() != null && exists.getIsLocked() == ScheduleSession.LOCKED) {
            throw new BizException(ResultCode.CONFLICT, "竞赛单元已锁定，不允许修改" + field + "，请先解锁");
        }
        Long itemCount = exists.getItemCount() == null ? 0L : exists.getItemCount().longValue();
        if (itemCount > 0) {
            throw new BizException(ResultCode.CONFLICT, "竞赛单元已排入日程（" + itemCount + " 条），不允许修改" + field);
        }
    }

    private void checkPeriod(String period) {
        if (!StringUtils.hasText(period)) {
            throw new BizException(ResultCode.PARAM_ERROR, "时段不能为空");
        }
        String value = period.trim().toUpperCase();
        if (!PERIOD_ORDER.containsKey(value)) {
            throw new BizException(ResultCode.PARAM_ERROR, "时段只能是 MORNING/AFTERNOON/EVENING");
        }
    }

    private void checkTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new BizException(ResultCode.PARAM_ERROR, "单元结束时刻必须晚于开始时刻");
        }
    }

    /**
     * UK(meet_id, session_date, period) 校验。
     */
    private void checkUnique(Long meetId, LocalDate date, String period, Long excludeId) {
        if (date == null || !StringUtils.hasText(period)) {
            return;
        }
        LambdaQueryWrapper<ScheduleSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleSession::getMeetId, meetId)
                .eq(ScheduleSession::getSessionDate, date)
                .eq(ScheduleSession::getPeriod, period.trim().toUpperCase())
                .ne(excludeId != null, ScheduleSession::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该日期该时段已存在竞赛单元：" + date + " " + period);
        }
    }

    /**
     * 赛期第几天：单元日期 − 届次开始日期 + 1。
     */
    private Integer resolveDayNo(Long meetId, LocalDate date) {
        if (date == null) {
            return null;
        }
        Meet meet = meetMapper.selectById(meetId);
        if (meet == null || meet.getStartDate() == null) {
            return 1;
        }
        long days = Duration.between(meet.getStartDate().atStartOfDay(), date.atStartOfDay()).toDays();
        return (int) Math.max(1, days + 1);
    }

    private Integer resolveTotalMinutes(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return DEFAULT_TOTAL_MINUTES;
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return minutes <= 0 ? DEFAULT_TOTAL_MINUTES : (int) minutes;
    }

    private int nextSortNo(Long meetId) {
        Long count = this.count(new LambdaQueryWrapper<ScheduleSession>()
                .eq(ScheduleSession::getMeetId, meetId));
        return (count == null ? 0 : count.intValue()) + 1;
    }

    private int periodOrder(String period) {
        if (!StringUtils.hasText(period)) {
            return 9;
        }
        return PERIOD_ORDER.getOrDefault(period.trim().toUpperCase(), 9);
    }

    private <T extends Comparable<T>> int compare(T first, T second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return first.compareTo(second);
    }
}
