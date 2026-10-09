package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目级规则简要投影（跨模块取数：event_rule_config）。
 *
 * @author TrackMeet
 */
@Data
public class EventRuleBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long eventId;
    private String drawMode;
    private Integer drawAvoidSameUnit;
    private Integer drawGroupSize;
    private Integer laneCount;
    private String laneOrderJson;
    private String laneAssignMode;
    private Integer conflictIntervalMin;
    private Integer attemptCount;
    private Integer durationMin;
    private String extraJson;
}
