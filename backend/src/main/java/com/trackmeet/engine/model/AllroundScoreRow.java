package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 全能计分表行（成绩区间 → 得分），来自 allround_score_table（全局表）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllroundScoreRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 计分表编码，如 DECATHLON_M_100M */
    private String tableCode;

    /** 适用性别：MALE/FEMALE */
    private String gender;

    /** 单项子类型：SPRINT/JUMP/THROW/HURDLE 等 */
    private String subEventType;

    /** 成绩单位：s/m/cm */
    private String unit;

    /** 区间下限 */
    private BigDecimal performanceMin;

    /** 区间上限 */
    private BigDecimal performanceMax;

    /** 该区间得分 */
    private BigDecimal pointValue;

    /** 是否手计时表 */
    private boolean handTime;

    /** 计分方向：ASC 越大越好 / DESC 越小越好 */
    private String scoreDirection;
}
