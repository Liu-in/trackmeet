package com.trackmeet.modules.schedule.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.entity.ScheduleSession;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.LaneMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleSessionMapper;
import com.trackmeet.modules.schedule.mapper.FieldOrderMapper;
import com.trackmeet.modules.schedule.vo.AthleteEntryRowVO;
import com.trackmeet.modules.schedule.vo.EventAthleteRowVO;
import com.trackmeet.modules.schedule.vo.EventBriefRowVO;
import com.trackmeet.modules.schedule.vo.EventCountRowVO;
import com.trackmeet.modules.schedule.vo.FieldOrderVO;
import com.trackmeet.modules.schedule.vo.HeatMemberVO;
import com.trackmeet.modules.schedule.vo.HeatVO;
import com.trackmeet.modules.schedule.vo.LaneVO;
import com.trackmeet.modules.schedule.vo.RefereeAssignRowVO;
import com.trackmeet.modules.schedule.vo.VenueBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 编排模块的跨表数据装配器（只读）。
 * <p>
 * 职责：把「日程 → 分组 → 组内人员 → 道次/出场顺序」的链式读取集中在一处，
 * 供日程详情、甘特视图、分组分道、冲突检测与快照复用，避免各 Service 各写一套查询。
 * 单表 CRUD 走 MyBatis-Plus（自动带 {@code meet_id} 与 {@code deleted}），
 * 跨模块查询走 {@link ScheduleMapper} 的显式投影（SQL 自带 {@code meet_id}/{@code deleted}）。
 *
 * @author TrackMeet
 */
@Component
@RequiredArgsConstructor
public class ScheduleDataProvider {

    private final ScheduleMapper scheduleMapper;
    private final ScheduleSessionMapper scheduleSessionMapper;
    private final HeatMapper heatMapper;
    private final HeatMemberMapper heatMemberMapper;
    private final LaneMapper laneMapper;
    private final FieldOrderMapper fieldOrderMapper;

    /**
     * 日程条目列表（按 计划开始时间 → 单元内排序号 排序）。
     *
     * @param meetId     届次 ID
     * @param sessionIds 单元过滤（空表示全部）
     * @return 日程实体列表
     */
    public List<Schedule> listSchedules(Long meetId, Collection<Long> sessionIds) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Schedule::getMeetId, meetId);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            wrapper.in(Schedule::getSessionId, sessionIds);
        }
        wrapper.orderByAsc(Schedule::getPlanStartTime).orderByAsc(Schedule::getOrderNo).orderByAsc(Schedule::getId);
        return scheduleMapper.selectList(wrapper);
    }

    /**
     * 日程 → 单元映射。
     */
    public Map<Long, ScheduleSession> sessionMap(Long meetId, Collection<Long> sessionIds) {
        LambdaQueryWrapper<ScheduleSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleSession::getMeetId, meetId);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            wrapper.in(ScheduleSession::getId, sessionIds);
        }
        List<ScheduleSession> sessions = scheduleSessionMapper.selectList(wrapper);
        Map<Long, ScheduleSession> map = new LinkedHashMap<>();
        for (ScheduleSession session : sessions) {
            map.put(session.getId(), session);
        }
        return map;
    }

    /**
     * 场地投影（id → 场地）。
     */
    public Map<Long, VenueBriefVO> venueMap(Long meetId) {
        Map<Long, VenueBriefVO> map = new HashMap<>();
        List<VenueBriefVO> venues = scheduleMapper.selectVenueBriefs(meetId);
        if (venues != null) {
            for (VenueBriefVO venue : venues) {
                if (venue != null && venue.getId() != null) {
                    map.put(venue.getId(), venue);
                }
            }
        }
        return map;
    }

    /**
     * 项目简要投影（id → 项目），只取指定项目。
     */
    public Map<Long, EventBriefRowVO> eventMap(Long meetId, Collection<Long> eventIds) {
        Map<Long, EventBriefRowVO> map = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return map;
        }
        List<EventBriefRowVO> events = scheduleMapper.selectEventBriefs(meetId, distinctIds(eventIds), null);
        if (events != null) {
            for (EventBriefRowVO event : events) {
                if (event != null && event.getEventId() != null) {
                    map.put(event.getEventId(), event);
                }
            }
        }
        return map;
    }

    /**
     * 分组的道次列表（含人员展示字段）。
     *
     * @param meetId  届次 ID
     * @param heatIds 分组 ID（为空返回空列表）
     * @return 道次视图列表
     */
    public List<LaneVO> listLanes(Long meetId, Collection<Long> heatIds) {
        if (heatIds == null || heatIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<LaneVO> lanes = laneMapper.selectLaneVOs(meetId, distinctIds(heatIds));
        return lanes == null ? new ArrayList<>() : lanes;
    }

    /**
     * 分组的田赛出场顺序。
     */
    public List<FieldOrderVO> listFieldOrders(Long meetId, Collection<Long> heatIds) {
        if (heatIds == null || heatIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<FieldOrderVO> orders = fieldOrderMapper.selectFieldOrderVOs(meetId, distinctIds(heatIds));
        return orders == null ? new ArrayList<>() : orders;
    }

    /**
     * 分组内的组内人员列表（按 heat_id、order_no 升序）。
     */
    public List<HeatMemberVO> listMembers(Long meetId, Collection<Long> heatIds) {
        if (heatIds == null || heatIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<HeatMemberVO> members = heatMemberMapper.selectMemberVOs(meetId, distinctIds(heatIds));
        return members == null ? new ArrayList<>() : members;
    }

    /**
     * 日程下的分组列表（不含人员/道次）。
     */
    public List<HeatVO> listHeats(Long meetId, Long scheduleId) {
        if (scheduleId == null) {
            return new ArrayList<>();
        }
        List<HeatVO> heats = heatMapper.selectHeatVOs(meetId, scheduleId);
        return heats == null ? new ArrayList<>() : heats;
    }

    /**
     * 日程的报名装配行（分组/田赛顺序的引擎输入），仅取已通过报名。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 装配行列表
     */
    public List<AthleteEntryRowVO> seedEntries(Long meetId, Long eventId) {
        if (eventId == null) {
            return new ArrayList<>();
        }
        List<AthleteEntryRowVO> rows = heatMemberMapper.selectSeedEntries(meetId, eventId);
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * 项目参赛运动员（接力按队员展开）：eventId → 运动员 ID 列表。
     *
     * @param meetId   届次 ID
     * @param eventIds 项目 ID 集合
     * @return 映射（保持插入顺序，元素去重）
     */
    public Map<Long, List<Long>> athleteIdsByEvent(Long meetId, Collection<Long> eventIds) {
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return map;
        }
        List<EventAthleteRowVO> rows = scheduleMapper.selectEventAthleteRows(meetId, distinctIds(eventIds));
        if (rows == null) {
            return map;
        }
        for (EventAthleteRowVO row : rows) {
            if (row == null || row.getEventId() == null || row.getAthleteId() == null) {
                continue;
            }
            List<Long> athletes = map.computeIfAbsent(row.getEventId(), key -> new ArrayList<>());
            if (!athletes.contains(row.getAthleteId())) {
                athletes.add(row.getAthleteId());
            }
        }
        return map;
    }

    /**
     * 项目有效报名人数（status = APPROVED）：eventId → 人数。
     */
    public Map<Long, Integer> participantCountByEvent(Long meetId, Collection<Long> eventIds) {
        Map<Long, Integer> map = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return map;
        }
        List<EventCountRowVO> rows = scheduleMapper.selectEventParticipantCounts(meetId, distinctIds(eventIds));
        if (rows == null) {
            return map;
        }
        for (EventCountRowVO row : rows) {
            if (row != null && row.getEventId() != null) {
                map.put(row.getEventId(), row.getApprovedCount() == null ? 0 : row.getApprovedCount());
            }
        }
        return map;
    }

    /**
     * 日程 → 裁判 ID 列表（{@code referee_assignment}；表为空时返回空映射）。
     *
     * @param meetId      届次 ID
     * @param scheduleIds 日程 ID 集合（为空时返回该届次全部裁判分工）
     * @return 映射
     */
    public Map<Long, List<Long>> refereeIdsBySchedule(Long meetId, Collection<Long> scheduleIds) {
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        List<RefereeAssignRowVO> rows = scheduleMapper.selectRefereeAssignRows(meetId,
                scheduleIds == null || scheduleIds.isEmpty() ? null : distinctIds(scheduleIds));
        if (rows == null) {
            return map;
        }
        for (RefereeAssignRowVO row : rows) {
            if (row == null || row.getScheduleId() == null || row.getRefereeId() == null) {
                continue;
            }
            List<Long> referees = map.computeIfAbsent(row.getScheduleId(), key -> new ArrayList<>());
            if (!referees.contains(row.getRefereeId())) {
                referees.add(row.getRefereeId());
            }
        }
        return map;
    }

    /**
     * 按 ID 集合去重（去 null，保持顺序）。
     */
    public List<Long> distinctIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 分组 ID 列表（按日程），用于分道/出场顺序批量查询。
     */
    public List<Long> heatIdsOf(List<HeatVO> heats) {
        if (heats == null) {
            return new ArrayList<>();
        }
        return heats.stream()
                .map(HeatVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
