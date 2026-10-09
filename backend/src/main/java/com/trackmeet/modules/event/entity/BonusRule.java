package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * bonus_rule 加分规则表：破纪录加分、接力双倍、破平局加分的可配置规则。
 * <p>
 * 字段与 {@code sql/03-schema-event-schedule.sql} 的 {@code bonus_rule} 表逐列一致；
 * {@code rule_code} 在届次内唯一（{@code uk_bonus_meet_code}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bonus_rule")
public class BonusRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 加分类型：破纪录 */
    public static final String TYPE_RECORD = "RECORD";
    /** 加分类型：接力双倍 */
    public static final String TYPE_RELAY = "RELAY";
    /** 加分类型：破平局 */
    public static final String TYPE_BREAK_TIE = "BREAK_TIE";

    /** 状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 加分规则名称 */
    @TableField("rule_name")
    private String ruleName;

    /** 规则编码，届次内唯一 */
    @TableField("rule_code")
    private String ruleCode;

    /** 加分类型：RECORD 破纪录 / RELAY 接力双倍 / BREAK_TIE 破平局 */
    @TableField("bonus_type")
    private String bonusType;

    /** 纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    @TableField("record_type")
    private String recordType;

    /** 破纪录加分值（默认赛会纪录 +9） */
    @TableField("bonus_point")
    private BigDecimal bonusPoint;

    /** 多层级纪录加分是否叠加：0 否 / 1 是 */
    @TableField("is_stackable")
    private Integer isStackable;

    /** 倍数（接力双倍为 2.00） */
    @TableField("factor")
    private BigDecimal factor;

    /** 适用项目 ID，关联 event.id，空为全部项目 */
    @TableField("event_id")
    private Long eventId;

    /** 适用组别 ID，关联 meet_group.id，空为全部组别 */
    @TableField("group_id")
    private Long groupId;

    /** 适用项目类型：ALL/TRACK/FIELD/ALLROUND/RELAY */
    @TableField("apply_event_type")
    private String applyEventType;

    /** 适用性别：ALL/MALE/FEMALE */
    @TableField("apply_gender")
    private String applyGender;

    /** 生效轮次码，逗号分隔（QUALIFY/PRE/R1/R2/SEMI/FINAL），空为全部 */
    @TableField("effective_round_codes")
    private String effectiveRoundCodes;

    /** 加分是否计入团体总分：0 否 / 1 是 */
    @TableField("is_in_team_score")
    private Integer isInTeamScore;

    /** 多条命中时的优先顺序 */
    @TableField("priority")
    private Integer priority;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
