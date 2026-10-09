package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分明细投影行：{@code points} 表联 {@code event} / {@code event_round} / {@code unit} /
 * {@code athlete} / {@code registration} 的只读明细，用于积分下钻与积分明细表打印。
 *
 * @author TrackMeet
 */
@Data
public class StandingPointRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 积分明细 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 来源成绩 ID */
    private Long resultId;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 计分单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 性别：MALE / FEMALE / MIXED */
    private String gender;

    /** 计分名次 */
    private Integer rankNo;

    /** 名次分 */
    private BigDecimal rankPoint;

    /** 破纪录加分合计 */
    private BigDecimal bonusPoint;

    /** 接力倍数 */
    private BigDecimal relayFactor;

    /** 合计分 */
    private BigDecimal totalPoint;

    /** 名次分方案 ID */
    private Long scoreRuleId;

    /** 方案编码快照 */
    private String scoreRuleCode;

    /** 命中的加分规则 ID 列表 */
    private String bonusRuleIds;

    /** 计算时间 */
    private LocalDateTime calcTime;

    /** 是否人工调整：1 是 / 0 否 */
    private Integer isManual;

    /** 人工调整原因 */
    private String adjustReason;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 项目类型 */
    private String eventType;

    /** 是否接力项目：0 否 / 1 是 */
    private Integer relay;

    /** 轮次码 */
    private String roundCode;

    /** 轮次名称 */
    private String roundName;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 组别名称 */
    private String groupName;

    /** 运动员姓名 */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 接力队名 */
    private String teamName;

    /** 名次显示文本 */
    private String rankDisplay;
}
