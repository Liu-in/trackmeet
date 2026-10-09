package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * score_rule 名次分方案表：并列计分方式、适用范围与默认标记。
 * <p>
 * 字段与 {@code sql/03-schema-event-schedule.sql} 的 {@code score_rule} 表逐列一致；
 * {@code rule_code} 在届次内唯一（{@code uk_scorerule_meet_code}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("score_rule")
public class ScoreRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 并列计分：均分法 */
    public static final String TIE_AVERAGE = "AVERAGE";
    /** 并列计分：满额法 */
    public static final String TIE_FULL = "FULL";
    /** 并列计分：顺延法 */
    public static final String TIE_ORDER = "ORDER";

    /** 状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 方案名称（如「单项前八名 9-7-6-5」） */
    @TableField("rule_name")
    private String ruleName;

    /** 方案编码，届次内唯一 */
    @TableField("rule_code")
    private String ruleCode;

    /** 并列计分：AVERAGE 均分 / FULL 满额 / ORDER 顺延 */
    @TableField("tie_policy")
    private String tiePolicy;

    /** 计分对象：EVENT 单项 / TEAM 团体 */
    @TableField("score_target")
    private String scoreTarget;

    /** 计名次数量（明细行数），保存明细时回写 */
    @TableField("rank_count")
    private Integer rankCount;

    /** 计分基准分（用于系数换算） */
    @TableField("point_base")
    private BigDecimal pointBase;

    /** 是否届次默认方案：0 否 / 1 是（默认方案禁止删除） */
    @TableField("is_default")
    private Integer isDefault;

    /** 适用项目类型：TRACK/FIELD/ALLROUND/RELAY/ALL */
    @TableField("apply_event_type")
    private String applyEventType;

    /** 适用性别：MALE/FEMALE/MIXED/ALL */
    @TableField("apply_gender")
    private String applyGender;

    /** 适用组别 ID，关联 meet_group.id，空为全部组别 */
    @TableField("group_id")
    private Long groupId;

    /** 生效轮次码：QUALIFY/PRE/R1/R2/SEMI/FINAL，空默认决赛 */
    @TableField("effective_round_code")
    private String effectiveRoundCode;

    /** 是否计入团体总分：0 否 / 1 是 */
    @TableField("is_team_score")
    private Integer isTeamScore;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
