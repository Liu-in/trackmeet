package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分组参数（全部可配置，算法内不硬编码业务阈值）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupingOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标组数（优先于 heatSize；<=0 时按 heatSize 推算） */
    @Builder.Default
    private int heatCount = 0;

    /** 每组人数上限（通常为场地道数；<=0 时按 heatCount 平分） */
    @Builder.Default
    private int heatSize = 0;

    /** 是否尽量把同单位运动员分散到不同组 */
    @Builder.Default
    private boolean avoidSameUnit = true;

    /** 种子排序方向：true 数值越小越好（径赛）／false 数值越大越好（田赛） */
    @Builder.Default
    private boolean seedAscending = true;

    /** 无种子成绩者是否随机排列（false 时按号码排序，保证可复现） */
    @Builder.Default
    private boolean randomizeUnseeded = false;

    /** 随机种子（保证结果可复现） */
    @Builder.Default
    private long randomSeed = 20250101L;
}
