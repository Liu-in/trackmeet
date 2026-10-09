package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 加分规则 —— 来自 bonus_rule（破纪录加分 / 接力双倍 / 破平局）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BonusRuleRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String ruleCode;

    /** 类型：RECORD 破纪录 / RELAY 接力双倍 / BREAK_TIE 破平局 */
    private String bonusType;

    /** 破纪录对标的层级（RECORD 用） */
    private String recordType;

    /** 加分值（RECORD/BREAK_TIE 用） */
    private BigDecimal bonusPoint;

    /** 多层级纪录是否叠加 */
    @Builder.Default
    private boolean stackable = false;

    /** 倍数（RELAY 用，默认 2.00 由调用方传入） */
    private BigDecimal factor;

    /** 加分是否计入团体总分 */
    @Builder.Default
    private boolean inTeamScore = true;

    /** 优先级（越小越优先） */
    @Builder.Default
    private int priority = 100;
}
