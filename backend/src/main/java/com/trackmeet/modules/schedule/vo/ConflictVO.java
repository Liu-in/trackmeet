package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 冲突记录视图对象：含项目/人员/场地冗余展示字段与冲突说明。
 *
 * @author TrackMeet
 */
@Data
public class ConflictVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private String conflictType;
    private String level;
    private Long scheduleId;
    private Long relatedScheduleId;
    private String eventName;
    private String relatedEventName;
    private Long heatId;
    private Long relatedHeatId;
    private Long athleteId;
    private String athleteName;
    private Long registrationId;
    private Long refereeId;
    private Long venueId;
    private String venueName;
    private Long sessionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime relatedStartTime;
    private LocalDateTime relatedEndTime;
    private Integer overlapMinutes;
    private Integer intervalMinutes;
    private Integer requiredIntervalMin;
    private String detailJson;
    private String message;
    private String batchNo;
    private LocalDateTime detectTime;
    private Integer resolved;
    private String resolveRemark;
    private Long resolveBy;
    private LocalDateTime resolveTime;
}
