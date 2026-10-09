package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 道次分配参数。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaneOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 道次优先顺序（如 8 道 [4,5,3,6,2,7,1,8]）；为空时按 laneCount 取默认顺序 */
    @Builder.Default
    private List<Integer> laneOrder = new ArrayList<>();

    /** 可用道数（6 或 8；<=0 时取 laneOrder 长度，再兜底 8） */
    @Builder.Default
    private int laneCount = 0;

    /** 分配方式：SEED 按成绩 / DRAW 抽签 / SERPENT 蛇形 / ORDER 输入顺序 */
    @Builder.Default
    private String mode = "SEED";

    /** 抽签随机种子 */
    @Builder.Default
    private long randomSeed = 20250101L;
}
