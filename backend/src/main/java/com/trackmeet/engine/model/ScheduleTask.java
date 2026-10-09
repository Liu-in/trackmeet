package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 待编排任务（一个项目的某个轮次）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 项目名称 */
    private String eventName;

    /** 轮次码：QUALIFY/PRE/R1/R2/SEMI/FINAL */
    private String roundCode;

    /** 是否径赛（占用跑道） */
    private boolean track;

    /** 参赛人数 */
    @Builder.Default
    private int participantCount = 0;

    /** 组数 */
    @Builder.Default
    private int heatCount = 1;

    /** 预计时长（分钟） */
    @Builder.Default
    private int durationMin = 15;

    /** 优先级（越小越先排） */
    @Builder.Default
    private int priority = 100;

    /** 偏好时段：MORNING/AFTERNOON/EVENING/ANY */
    @Builder.Default
    private String periodPreference = "ANY";

    /** 指定场地（可空） */
    private Long venueId;

    /** 是否已锁定（锁定任务不参与重排，仅占用时间与运动员） */
    private boolean locked;

    /** 锁定任务的开始时间 */
    private LocalDateTime lockedStart;

    /** 锁定任务所属单元 */
    private Long lockedSessionId;

    /** 参赛运动员 ID 列表（用于兼项约束） */
    @Builder.Default
    private List<Long> athleteIds = new ArrayList<>();
}
