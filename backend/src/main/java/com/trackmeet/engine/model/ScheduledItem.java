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
 * 已排定（或待检测）的日程条目 —— 冲突检测的输入。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日程 ID（未落库时可为空） */
    private Long scheduleId;

    /** 竞赛单元 ID */
    private Long sessionId;

    /** 场地 ID */
    private Long venueId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 项目/轮次显示名（用于冲突提示文案） */
    private String itemName;

    /** 计划开始时间 */
    private LocalDateTime startTime;

    /** 计划结束时间 */
    private LocalDateTime endTime;

    /** 是否径赛（占用跑道） */
    private boolean track;

    /** 组数 */
    private Integer heatCount;

    /** 参赛运动员 ID 列表 */
    @Builder.Default
    private List<Long> athleteIds = new ArrayList<>();

    /** 参与裁判 ID 列表 */
    @Builder.Default
    private List<Long> refereeIds = new ArrayList<>();
}
