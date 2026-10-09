package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 平局（并列）处理规则 —— 来自 event_rule_config.tie_break_json，不硬编码。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TieBreakRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 依次比较的规则序列，取值：
     * SECOND_BEST（次优成绩）/ BEST_ATTEMPT_ROUND（最远成功轮次）/ DRAW（抽签）。
     */
    @Builder.Default
    private java.util.List<String> order = new java.util.ArrayList<>();

    /** 仍无法区分时是否允许并列名次（true 并列、false 按报名号顺序强制区分） */
    @Builder.Default
    private boolean allowParallelRank = true;

    /**
     * 默认规则：次优成绩 → 最远成功轮次 → 允许并列。
     */
    public static TieBreakRule defaultRule() {
        TieBreakRule rule = new TieBreakRule();
        rule.getOrder().add("SECOND_BEST");
        rule.getOrder().add("BEST_ATTEMPT_ROUND");
        rule.setAllowParallelRank(true);
        return rule;
    }
}
