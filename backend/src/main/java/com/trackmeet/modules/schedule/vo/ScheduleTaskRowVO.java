package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 自动编排任务装配行（跨模块取数：event × event_round × event_rule_config）。
 * 参赛人数单独由 {@link EventCountRowVO} 汇总后回填。
 *
 * @author TrackMeet
 */
@Data
public class ScheduleTaskRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long eventId;
    private Long roundId;
    private String eventName;
    private String eventType;
    private String subType;
    private String gender;
    private Integer isRelay;
    private Long groupId;
    private Integer sortNo;
    private Integer eventDurationMin;

    private String roundCode;
    private Short roundNo;
    private Integer isFinal;
    private Integer planHeatCount;
    private Integer planHeatSize;
    private String roundStatus;

    private Integer ruleLaneCount;
    private Integer ruleDurationMin;
    private Integer drawGroupSize;
    private String drawMode;
    private Integer drawAvoidSameUnit;
    private Integer attemptCount;
    private Integer ruleConflictIntervalMin;
}
