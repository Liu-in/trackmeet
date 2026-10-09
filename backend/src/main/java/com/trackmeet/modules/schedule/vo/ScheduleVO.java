package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 日程条目视图对象：包含项目/轮次/单元/场地的冗余展示字段，
 * 详情接口额外挂载分组（含组内人员与道次/出场顺序）。
 *
 * @author TrackMeet
 */
@Data
public class ScheduleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private Long sessionId;
    private String sessionName;
    private LocalDate sessionDate;
    private String period;
    private Long eventId;
    private String eventName;
    private String eventType;
    private String subType;
    private Long groupId;
    private Long roundId;
    private String roundCode;
    private Short roundNo;
    private Long venueId;
    private String venueName;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationMin;
    private Integer actualDurationMin;
    private Integer heatCount;
    private Integer memberCount;
    private Integer orderNo;
    private String sourceType;
    private String batchNo;
    private Integer isLocked;
    private Long lockedBy;
    private LocalDateTime lockTime;
    private Integer moveCount;
    private String adjustReason;
    private String publishStatus;
    private LocalDateTime publishTime;
    private Long publisherId;
    private Integer conflictFlag;
    private Integer conflictCount;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 计划开始时刻（展示用） */
    private LocalTime startTime;
    /** 计划结束时刻（展示用） */
    private LocalTime endTime;
    /** 是否径赛（TRACK/RELAY） */
    private Boolean track;

    /** 分组明细（仅详情接口返回） */
    private List<HeatVO> heats;
}
