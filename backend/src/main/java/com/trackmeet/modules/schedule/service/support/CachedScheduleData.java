package com.trackmeet.modules.schedule.service.support;

import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import com.trackmeet.modules.schedule.vo.EventBriefRowVO;
import com.trackmeet.modules.schedule.vo.EventRuleBriefVO;
import com.trackmeet.modules.schedule.vo.EventScheduleRuleVO;
import com.trackmeet.modules.schedule.vo.MeetRuleBriefVO;
import com.trackmeet.modules.schedule.vo.VenueBriefVO;
import com.trackmeet.modules.schedule.vo.ScheduleTaskRowVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 编排装配的只读数据快照（届次内）。
 * <p>
 * 一次「自动编排 / 分组分道」会多次读取场地、项目、项目级规则、届次级规则与任务装配行，
 * 本类把它们一次性载入并按主键建索引，避免循环内逐条查询（N+1）。
 *
 * @author TrackMeet
 */
@Component
@RequiredArgsConstructor
public class CachedScheduleData {

    private static final long serialVersionUID = 1L;

    private final ScheduleMapper scheduleMapper;

    private final ScheduleRuleMatcher ruleMatcher;

    /** 届次 ID */
    @Getter
    private Long meetId;

    /** 场地：id → 场地投影 */
    @Getter
    private Map<Long, VenueBriefVO> venues = new HashMap<>();

    /** 项目：id → 项目简要投影 */
    @Getter
    private Map<Long, EventBriefRowVO> events = new HashMap<>();

    /** 项目级规则：eventId → 规则投影 */
    @Getter
    private Map<Long, EventRuleBriefVO> eventRules = new HashMap<>();

    /** 届次级规则（可能为 null 表示未配置） */
    @Getter
    private MeetRuleBriefVO meetRule;

    /** 项目时长与顺序默认规则（全局表） */
    @Getter
    private List<EventScheduleRuleVO> scheduleRules = new ArrayList<>();

    /** 任务装配行：eventId → 轮次行列表 */
    @Getter
    private Map<Long, List<ScheduleTaskRowVO>> taskRowsByEvent = new HashMap<>();

    /**
     * 按需载入届次数据；重复调用会重新载入。
     *
     * @param meetId        届次 ID
     * @param eventIds      项目过滤（空表示全部）
     * @param groupIds      组别过滤（空表示全部）
     * @param withTaskRows  是否载入任务装配行（分组分道场景不需要）
     * @return 本对象自身，便于链式调用
     */
    public CachedScheduleData load(Long meetId, Collection<Long> eventIds, Collection<Long> groupIds,
                                   boolean withTaskRows) {
        this.meetId = meetId;
        this.venues = indexVenues(meetId);
        this.events = indexEvents(meetId, eventIds, groupIds);
        List<Long> eventIdList = new ArrayList<>(this.events.keySet());
        this.eventRules = indexEventRules(meetId, eventIdList.isEmpty() ? null : eventIdList);
        this.meetRule = scheduleMapper.selectMeetRuleBrief(meetId);
        this.scheduleRules = ruleMatcher.loadRules();
        this.taskRowsByEvent = withTaskRows
                ? indexTaskRows(meetId, eventIds, groupIds)
                : new HashMap<>();
        return this;
    }

    /**
     * 场地投影。
     *
     * @param venueId 场地 ID
     * @return 场地投影，不存在返回 null
     */
    public VenueBriefVO venue(Long venueId) {
        return venueId == null ? null : venues.get(venueId);
    }

    /**
     * 场地道数：场地配置 → 届次默认 → 8。
     *
     * @param venueId 场地 ID
     * @return 道数
     */
    public int laneCount(Long venueId) {
        VenueBriefVO venue = venue(venueId);
        if (venue != null && venue.getLaneCount() != null && venue.getLaneCount() > 0) {
            return venue.getLaneCount();
        }
        if (meetRule != null && meetRule.getDefaultLaneCount() != null && meetRule.getDefaultLaneCount() > 0) {
            return meetRule.getDefaultLaneCount();
        }
        return ScheduleRuleMatcher.DEFAULT_LANE_COUNT;
    }

    /**
     * 场地道次优先顺序（JSON 文本，可能为空）。
     */
    public String laneOrderJson(Long venueId) {
        VenueBriefVO venue = venue(venueId);
        return venue == null ? null : venue.getLaneOrderJson();
    }

    /**
     * 项目简要投影。
     */
    public EventBriefRowVO event(Long eventId) {
        return eventId == null ? null : events.get(eventId);
    }

    /**
     * 项目级规则投影。
     */
    public EventRuleBriefVO eventRule(Long eventId) {
        return eventId == null ? null : eventRules.get(eventId);
    }

    /**
     * 届次兼项最小间隔（分钟）。
     */
    public int conflictIntervalMin() {
        if (meetRule != null && meetRule.getConflictIntervalMin() != null && meetRule.getConflictIntervalMin() > 0) {
            return meetRule.getConflictIntervalMin();
        }
        return ScheduleRuleMatcher.DEFAULT_CONFLICT_INTERVAL_MIN;
    }

    /**
     * 届次裁判连场最小间隔（分钟）。
     */
    public int refereeIntervalMin() {
        if (meetRule != null && meetRule.getRefereeIntervalMin() != null && meetRule.getRefereeIntervalMin() > 0) {
            return meetRule.getRefereeIntervalMin();
        }
        return ScheduleRuleMatcher.DEFAULT_REFEREE_INTERVAL_MIN;
    }

    /**
     * 项目级兼项间隔（分钟）：项目级 → 届次级。
     */
    public int conflictIntervalMin(Long eventId) {
        EventRuleBriefVO rule = eventRule(eventId);
        if (rule != null && rule.getConflictIntervalMin() != null && rule.getConflictIntervalMin() > 0) {
            return rule.getConflictIntervalMin();
        }
        return conflictIntervalMin();
    }

    /**
     * 田赛试跳/试掷次数：项目级 → 届次级 → 空（由引擎兜底默认 3 次）。
     */
    public Integer attemptCount(Long eventId) {
        EventRuleBriefVO rule = eventRule(eventId);
        if (rule != null && rule.getAttemptCount() != null && rule.getAttemptCount() > 0) {
            return rule.getAttemptCount();
        }
        if (meetRule != null && meetRule.getFieldAttemptCount() != null && meetRule.getFieldAttemptCount() > 0) {
            return meetRule.getFieldAttemptCount();
        }
        return null;
    }

    /**
     * 项目计划每组人数：项目级 draw_group_size → 空。
     */
    public Integer drawGroupSize(Long eventId) {
        EventRuleBriefVO rule = eventRule(eventId);
        if (rule != null && rule.getDrawGroupSize() != null && rule.getDrawGroupSize() > 0) {
            return rule.getDrawGroupSize();
        }
        return null;
    }

    /**
     * 项目是否避开同单位：项目级配置 → 默认 true。
     */
    public boolean avoidSameUnit(Long eventId) {
        EventRuleBriefVO rule = eventRule(eventId);
        if (rule != null && rule.getDrawAvoidSameUnit() != null) {
            return rule.getDrawAvoidSameUnit() == 1;
        }
        return true;
    }

    /**
     * 项目级道数：项目级 → 场地 → 届次级 → 8。
     */
    public int laneCount(Long eventId, Long venueId) {
        EventRuleBriefVO rule = eventRule(eventId);
        if (rule != null && rule.getLaneCount() != null && rule.getLaneCount() > 0) {
            return rule.getLaneCount();
        }
        return laneCount(venueId);
    }

    /**
     * 项目级道次顺序 JSON → 场地道次顺序 JSON → 届次默认。
     */
    public String laneOrderJson(Long eventId, Long venueId) {
        EventRuleBriefVO rule = eventRule(eventId);
        if (rule != null && rule.getLaneOrderJson() != null && !rule.getLaneOrderJson().isBlank()) {
            return rule.getLaneOrderJson();
        }
        String venueOrder = laneOrderJson(venueId);
        if (venueOrder != null && !venueOrder.isBlank()) {
            return venueOrder;
        }
        return meetRule == null ? null : meetRule.getDefaultLaneOrderJson();
    }

    /**
     * 项目级分组方式：项目级 draw_mode → 空。
     */
    public String drawMode(Long eventId) {
        EventRuleBriefVO rule = eventRule(eventId);
        return rule == null ? null : rule.getDrawMode();
    }

    /**
     * 项目下的轮次装配行（按 round_no 升序）。
     */
    public List<ScheduleTaskRowVO> taskRows(Long eventId) {
        List<ScheduleTaskRowVO> rows = taskRowsByEvent.get(eventId);
        return rows == null ? new ArrayList<>() : rows;
    }

    // ------------------------------------------------------------------
    // 索引构建
    // ------------------------------------------------------------------

    private Map<Long, VenueBriefVO> indexVenues(Long meetId) {
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

    private Map<Long, EventBriefRowVO> indexEvents(Long meetId, Collection<Long> eventIds, Collection<Long> groupIds) {
        Map<Long, EventBriefRowVO> map = new HashMap<>();
        List<EventBriefRowVO> events = scheduleMapper.selectEventBriefs(meetId,
                emptyToNull(eventIds), emptyToNull(groupIds));
        if (events != null) {
            for (EventBriefRowVO event : events) {
                if (event != null && event.getEventId() != null) {
                    map.put(event.getEventId(), event);
                }
            }
        }
        return map;
    }

    private Map<Long, EventRuleBriefVO> indexEventRules(Long meetId, Collection<Long> eventIds) {
        Map<Long, EventRuleBriefVO> map = new HashMap<>();
        List<EventRuleBriefVO> rules = scheduleMapper.selectEventRuleBriefs(meetId, emptyToNull(eventIds));
        if (rules != null) {
            for (EventRuleBriefVO rule : rules) {
                if (rule != null && rule.getEventId() != null) {
                    map.put(rule.getEventId(), rule);
                }
            }
        }
        return map;
    }

    private Map<Long, List<ScheduleTaskRowVO>> indexTaskRows(Long meetId, Collection<Long> eventIds,
                                                             Collection<Long> groupIds) {
        Map<Long, List<ScheduleTaskRowVO>> map = new HashMap<>();
        List<ScheduleTaskRowVO> rows = scheduleMapper.selectTaskRows(meetId,
                emptyToNull(eventIds), emptyToNull(groupIds));
        if (rows != null) {
            for (ScheduleTaskRowVO row : rows) {
                if (row == null || row.getEventId() == null) {
                    continue;
                }
                map.computeIfAbsent(row.getEventId(), key -> new ArrayList<>()).add(row);
            }
        }
        return map;
    }

    private Collection<Long> emptyToNull(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Long> filtered = new ArrayList<>();
        for (Long value : values) {
            if (value != null) {
                filtered.add(value);
            }
        }
        return filtered.isEmpty() ? null : filtered;
    }

    /**
     * 项目性别（用于规则匹配），缺失返回 null。
     */
    public String gender(Long eventId) {
        EventBriefRowVO event = event(eventId);
        return event == null ? null : event.getGender();
    }

    /**
     * 项目类型（TRACK/FIELD/ALLROUND/RELAY）。
     */
    public String eventType(Long eventId) {
        EventBriefRowVO event = event(eventId);
        return event == null ? null : event.getEventType();
    }

    /**
     * 是否接力项目。
     */
    public boolean relay(Long eventId) {
        EventBriefRowVO event = event(eventId);
        return event != null && Objects.equals(event.getIsRelay(), 1);
    }
}
