package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 团体总分（按 单位 × 组别 × 口径 汇总）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamScoreRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 参赛单位 ID */
    private Long unitId;

    /** 参赛单位名称（展示用） */
    private String unitName;

    /** 组别 ID */
    private Long groupId;

    /** 口径：MALE 男子团体 / FEMALE 女子团体 / TOTAL 总团体 */
    private String scoreType;

    /** 名次分合计 */
    private BigDecimal rankPointSum;

    /** 破纪录加分合计 */
    private BigDecimal bonusPointSum;

    /** 总分 */
    private BigDecimal totalPoint;

    /** 名次（同分并列） */
    private int rankNo;
}
