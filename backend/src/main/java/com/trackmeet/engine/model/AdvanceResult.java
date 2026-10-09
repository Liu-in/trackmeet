package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 晋级名单条目。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvanceResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 运动员姓名 */
    private String athleteName;

    /** 参赛单位 ID */
    private Long unitId;

    /** 来源组次 */
    private Integer sourceHeatNo;

    /** 来源名次 */
    private Integer sourceRank;

    /** 来源成绩数值 */
    private BigDecimal sourceResultValue;

    /** 晋级方式：GROUP_TOP 每组前 N / BEST_LOSER 最好成绩递补 / MANUAL 人工指定 */
    private String advanceType;

    /** 下一轮出场顺序（从 1 开始） */
    private int advanceOrder;
}
