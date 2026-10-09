package com.trackmeet.modules.standing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * points 积分明细表：一条「成绩 → 得分」的计算结果，记录名次分、破纪录加分与接力倍数的合成过程。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code points} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, meet_id, event_id, round_id, result_id, registration_id, athlete_id, unit_id, group_id,
 * gender, rank_no, rank_point, bonus_point, relay_factor, total_point, score_rule_id,
 * score_rule_code, bonus_rule_ids, calc_time, is_manual, adjust_reason, remark,
 * create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * <p>
 * <b>幂等重建要点</b>：唯一键 {@code uk_event_round_registration (event_id, round_id, registration_id)}
 * 不含 {@code deleted}，因此重算时必须<b>物理删除</b>后再插入（见
 * {@code StandingMapper#deletePoints}），否则逻辑删除残留行会与新行争抢唯一键。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("points")
public class Points extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 性别口径：男子 */
    public static final String GENDER_MALE = "MALE";
    /** 性别口径：女子 */
    public static final String GENDER_FEMALE = "FEMALE";
    /** 性别口径：混合 */
    public static final String GENDER_MIXED = "MIXED";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 项目 ID，关联 event.id */
    @TableField("event_id")
    private Long eventId;

    /** 轮次 ID，关联 event_round.id */
    @TableField("round_id")
    private Long roundId;

    /** 来源成绩 ID，关联 result.id */
    @TableField("result_id")
    private Long resultId;

    /** 报名记录 ID，关联 registration.id */
    @TableField("registration_id")
    private Long registrationId;

    /** 运动员 ID，关联 athlete.id（接力为队首人） */
    @TableField("athlete_id")
    private Long athleteId;

    /** 计分单位 ID，关联 unit.id */
    @TableField("unit_id")
    private Long unitId;

    /** 组别 ID，关联 meet_group.id */
    @TableField("group_id")
    private Long groupId;

    /** 性别：MALE 男子 / FEMALE 女子 / MIXED 混合 */
    @TableField("gender")
    private String gender;

    /** 计分名次 */
    @TableField("rank_no")
    private Integer rankNo;

    /** 名次分，查 score_rule_item 得 */
    @TableField("rank_point")
    private BigDecimal rankPoint;

    /** 破纪录加分合计 */
    @TableField("bonus_point")
    private BigDecimal bonusPoint;

    /** 接力倍数，非接力为 1 */
    @TableField("relay_factor")
    private BigDecimal relayFactor;

    /** 合计分 =（名次分 + 加分）× 倍数 */
    @TableField("total_point")
    private BigDecimal totalPoint;

    /** 名次分方案 ID，关联 score_rule.id */
    @TableField("score_rule_id")
    private Long scoreRuleId;

    /** 方案编码快照 */
    @TableField("score_rule_code")
    private String scoreRuleCode;

    /** 命中的加分规则 ID 列表，逗号分隔 */
    @TableField("bonus_rule_ids")
    private String bonusRuleIds;

    /** 计算时间 */
    @TableField("calc_time")
    private LocalDateTime calcTime;

    /** 是否人工调整：1 手工赋值 / 0 系统计算 */
    @TableField("is_manual")
    private Integer isManual;

    /** 人工调整原因 */
    @TableField("adjust_reason")
    private String adjustReason;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
