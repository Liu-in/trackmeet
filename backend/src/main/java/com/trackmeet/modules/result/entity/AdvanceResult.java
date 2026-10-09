package com.trackmeet.modules.result.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * advance_result 晋级名单表：记录「从哪一轮、以什么方式晋级到哪一轮」的名单，
 * 是自动晋级与人工递补的统一结果。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code advance_result} 表逐列一致。
 * 表上唯一键 {@code uk_event_to_round_registration (event_id, to_round_id, registration_id)}
 * 要求「同一目标轮次内同一报名只出现一次」，自动晋级采用「先物理删除后插入」的幂等重建策略。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("advance_result")
public class AdvanceResult extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 晋级方式：每组前 N */
    public static final String TYPE_GROUP_TOP = "GROUP_TOP";
    /** 晋级方式：最好成绩递补 */
    public static final String TYPE_BEST_LOSER = "BEST_LOSER";
    /** 晋级方式：人工指定 */
    public static final String TYPE_MANUAL = "MANUAL";

    /** 已确认 */
    public static final Integer CONFIRMED = 1;
    /** 未确认 */
    public static final Integer UNCONFIRMED = 0;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 项目 ID（event.id） */
    @TableField("event_id")
    private Long eventId;

    /** 来源轮次 ID（event_round.id） */
    @TableField("from_round_id")
    private Long fromRoundId;

    /** 目标轮次 ID（event_round.id） */
    @TableField("to_round_id")
    private Long toRoundId;

    /** 报名记录 ID（registration.id） */
    @TableField("registration_id")
    private Long registrationId;

    /** 运动员 ID（athlete.id） */
    @TableField("athlete_id")
    private Long athleteId;

    /** 参赛单位 ID（unit.id） */
    @TableField("unit_id")
    private Long unitId;

    /** 组别 ID（meet_group.id） */
    @TableField("group_id")
    private Long groupId;

    /** 来源组号 */
    @TableField("source_heat_no")
    private Integer sourceHeatNo;

    /** 来源组内名次 */
    @TableField("source_rank")
    private Integer sourceRank;

    /** 来源轮次成绩数值（用于递补排序） */
    @TableField("source_result_value")
    private BigDecimal sourceResultValue;

    /** 来源轮次成绩原文 */
    @TableField("source_result_raw")
    private String sourceResultRaw;

    /** 晋级方式：GROUP_TOP / BEST_LOSER / MANUAL */
    @TableField("advance_type")
    private String advanceType;

    /** 晋级后在新轮次中的顺序号 */
    @TableField("advance_order")
    private Integer advanceOrder;

    /** 名单是否已确认生效：1 已确认 / 0 未确认 */
    @TableField("is_confirmed")
    private Integer isConfirmed;

    /** 确认人 ID（sys_user.id） */
    @TableField("confirm_by")
    private Long confirmBy;

    /** 确认时间 */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
