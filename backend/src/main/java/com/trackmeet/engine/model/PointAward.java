package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 积分明细（一条成绩对应的积分结果）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointAward implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 性别（MALE/FEMALE，用于团体总分口径） */
    private String gender;

    /** 名次 */
    private int rankNo;

    /** 名次分 */
    private BigDecimal rankPoint;

    /** 破纪录加分 */
    private BigDecimal bonusPoint;

    /** 接力倍数（非接力为 1） */
    private BigDecimal relayFactor;

    /** 合计 = (名次分 + 加分) × 倍数 */
    private BigDecimal totalPoint;

    /** 命中的加分规则编码 */
    @Builder.Default
    private List<String> appliedBonusCodes = new ArrayList<>();
}
