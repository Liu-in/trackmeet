package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 奖牌榜行（按 单位 × 组别 汇总金/银/铜）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedalRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 参赛单位 ID */
    private Long unitId;

    /** 参赛单位名称 */
    private String unitName;

    /** 组别 ID（可为空表示全部组别） */
    private Long groupId;

    /** 金牌数 */
    private int gold;

    /** 银牌数 */
    private int silver;

    /** 铜牌数 */
    private int bronze;

    /** 奖牌总数 */
    private int totalMedal;

    /** 排序分（默认 金牌×100 + 银牌×10 + 铜牌） */
    private BigDecimal sortScore;

    /** 名次（同分并列） */
    private int rankNo;
}
