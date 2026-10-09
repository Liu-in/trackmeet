package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 自动编排结果条目（可直接落库到 schedule 表）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledItemResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 项目名称 */
    private String eventName;

    /** 轮次码 */
    private String roundCode;

    /** 单元 ID */
    private Long sessionId;

    /** 日期 */
    private LocalDate date;

    /** 计划开始时刻 */
    private LocalTime startTime;

    /** 计划结束时刻 */
    private LocalTime endTime;

    /** 场地 ID */
    private Long venueId;

    /** 组数 */
    private int heatCount;

    /** 是否径赛 */
    private boolean track;

    /** 参赛人数 */
    private int participantCount;
}
