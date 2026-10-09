package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目时长与顺序默认规则投影（跨模块取数：event_schedule_rule，全局表无 meet_id）。
 *
 * @author TrackMeet
 */
@Data
public class EventScheduleRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String eventType;
    private String subType;
    private String gender;
    private Integer defaultDurationMin;
    private Integer callAheadMin;
    private Integer defaultOrder;
    private Integer trackPriority;
    private Integer defaultLaneCount;
    private String periodPreference;
    private Integer maxRoundsPerDay;
    private Integer eventIntervalMin;
    private Integer isFieldOrderRequired;
}
