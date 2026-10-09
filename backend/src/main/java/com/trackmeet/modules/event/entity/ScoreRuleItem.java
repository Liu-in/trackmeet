package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * score_rule_item 名次分明细表：名次（或名次区间）到分值的映射明细，一表多方案。
 * <p>
 * 字段与 {@code sql/03-schema-event-schedule.sql} 的 {@code score_rule_item} 表逐列一致；
 * {@code (rule_id, rank_no)} 上有唯一键 {@code uk_scoreitem_rule_rank}。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("score_rule_item")
public class ScoreRuleItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 所属方案 ID，关联 score_rule.id */
    @TableField("rule_id")
    private Long ruleId;

    /** 名次序号（区间行填区间起点） */
    @TableField("rank_no")
    private Integer rankNo;

    /** 名次区间起（含） */
    @TableField("rank_from")
    private Integer rankFrom;

    /** 名次区间止（含） */
    @TableField("rank_to")
    private Integer rankTo;

    /** 名次显示名（如「第一名」） */
    @TableField("rank_label")
    private String rankLabel;

    /** 该名次 / 区间对应分值 */
    @TableField("point_value")
    private BigDecimal pointValue;

    /** 该名次附加分 */
    @TableField("bonus_point")
    private BigDecimal bonusPoint;

    /** 该名次并列处理：AVERAGE/FULL/ORDER，空继承主表 */
    @TableField("tie_policy")
    private String tiePolicy;

    /** 该名次分是否计入团体总分：0 否 / 1 是 */
    @TableField("is_team_score")
    private Integer isTeamScore;

    /** 显示排序号 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
