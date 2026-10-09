package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 名次分表明细行 —— 来自 score_rule_item。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreRuleRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 名次序号（从 1 开始） */
    private int rankNo;

    /** 该名次分值 */
    private BigDecimal pointValue;

    /** 并列计分：AVERAGE 均分 / FULL 满额 / ORDER 顺延（为空取主方案或默认 AVERAGE） */
    private String tiePolicy;

    /**
     * 生效轮次码（对应 score_rule.effective_round_code；为空表示适用于该项目的所有轮次）。
     * 用于避免同一项目多轮次重复计分。
     */
    private String effectiveRoundCode;
}
