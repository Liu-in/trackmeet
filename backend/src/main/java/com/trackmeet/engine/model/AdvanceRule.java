package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 晋级规则 —— 来自 event_round_rule（每组前 N + 剩余最好成绩递补）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvanceRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 每组前 N 名直接晋级 */
    @Builder.Default
    private int groupTopN = 2;

    /** 剩余人员按最好成绩递补的人数 */
    @Builder.Default
    private int bestLoserCount = 8;

    /** 目标晋级总人数（>0 时优先满足；不足则继续从剩余人员递补） */
    @Builder.Default
    private int targetCount = 16;

    /** 是否剔除超风速成绩（不参与递补） */
    @Builder.Default
    private boolean excludeIllegalWind = true;

    /** 是否剔除无效成绩（DNS/DNF/DSQ/NM/SCRATCH） */
    @Builder.Default
    private boolean excludeInvalidStatus = true;

    /** 边界并列处理：ALL 全部录取（可超出目标人数）/ TRUNCATE 严格截断 */
    @Builder.Default
    private String tiePolicy = "ALL";
}
