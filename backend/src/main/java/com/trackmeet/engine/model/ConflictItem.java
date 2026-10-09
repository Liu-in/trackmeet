package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 冲突项。
 * <p>
 * 级别约定：时间重叠（兼项/场地/裁判）为 ERROR；间隔不足为 WARN（可由编排员判断是否接受）。
 *
 * @author TrackMeet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 冲突类型：ATHLETE 兼项 / VENUE 场地 / REFEREE 裁判 / INTERVAL 间隔不足 */
    private String conflictType;

    /** 级别：ERROR / WARN */
    private String level;

    /** 主日程 ID */
    private Long scheduleId;

    /** 关联日程 ID */
    private Long relatedScheduleId;

    /** 涉及运动员 ID */
    private Long athleteId;

    /** 涉及裁判 ID */
    private Long refereeId;

    /** 涉及场地 ID */
    private Long venueId;

    /** 冲突说明（中文，可直接展示） */
    private String message;

    /** 重叠分钟数（重叠类冲突） */
    private long overlapMinutes;

    /** 实际间隔分钟数（间隔类冲突） */
    private Long intervalMinutes;

    /** 要求的间隔分钟数（间隔类冲突） */
    private Long requiredIntervalMin;
}
