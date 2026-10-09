package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 晋级名单行视图：{@code advance_result} 全字段 + 项目、轮次、运动员、单位、组别展示字段。
 *
 * @author TrackMeet
 */
@Data
public class AdvanceRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 晋级名单 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 来源轮次 ID */
    private Long fromRoundId;

    /** 目标轮次 ID */
    private Long toRoundId;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 来源组号 */
    private Integer sourceHeatNo;

    /** 来源组内名次 */
    private Integer sourceRank;

    /** 来源轮次成绩数值 */
    private BigDecimal sourceResultValue;

    /** 来源轮次成绩原文 */
    private String sourceResultRaw;

    /** 晋级方式：GROUP_TOP/BEST_LOSER/MANUAL */
    private String advanceType;

    /** 晋级后在新轮次中的顺序号 */
    private Integer advanceOrder;

    /** 名单是否已确认生效：1 已确认 / 0 未确认 */
    private Integer isConfirmed;

    /** 确认人 ID */
    private Long confirmBy;

    /** 确认时间 */
    private LocalDateTime confirmTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /* ======================= 关联展示字段 ======================= */

    /** 项目名称 */
    private String eventName;

    /** 项目类型 */
    private String eventType;

    /** 来源轮次码 */
    private String fromRoundCode;

    /** 来源轮次名称 */
    private String fromRoundName;

    /** 目标轮次码 */
    private String toRoundCode;

    /** 目标轮次名称 */
    private String toRoundName;

    /** 号码布编号 */
    private String bibNo;

    /** 姓名（接力为队名） */
    private String athleteName;

    /** 参赛单位名称 */
    private String unitName;

    /** 组别名称 */
    private String groupName;

    /** 是否接力报名：0 否 / 1 是 */
    private Integer relayFlag;
}
