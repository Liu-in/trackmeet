package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动编排计划：排定条目 + 缺口报告 + 冲突清单。
 *
 * @author TrackMeet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排定的日程条目 */
    private List<ScheduledItemResult> items = new ArrayList<>();

    /** 缺口报告（未能排入的项目及原因建议） */
    private List<String> shortageMessages = new ArrayList<>();

    /** 冲突清单（排定结果的冲突检测结果） */
    private List<ConflictItem> conflicts = new ArrayList<>();

    /** 已排条目数 */
    private int scheduledCount;

    /** 未排条目数 */
    private int unscheduledCount;
}
