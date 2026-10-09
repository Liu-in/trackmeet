package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * event_round_rule 轮次级规则表：三层继承最内层 —— 本轮录取名次与晋级 / 递补规则。
 * <p>
 * 字段与 {@code sql/03-schema-event-schedule.sql} 的 {@code event_round_rule} 表逐列一致；
 * {@code round_id} 上有唯一键 {@code uk_roundrule_round}，与轮次一对一。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_round_rule")
public class EventRoundRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 轮次 ID，关联 event_round.id，一对一 */
    @TableField("round_id")
    private Long roundId;

    /** 所属项目 ID，关联 event.id，便于按项目查询 */
    @TableField("event_id")
    private Long eventId;

    /** 本轮录取名次（取前 N 名） */
    @TableField("admit_rank")
    private Integer admitRank;

    /** 每组直接晋级前 N 名（默认 2） */
    @TableField("group_top_n")
    private Integer groupTopN;

    /** 剩余最好成绩递补人数（默认 8） */
    @TableField("best_loser_count")
    private Integer bestLoserCount;

    /** 目标晋级总人数 */
    @TableField("target_count")
    private Integer targetCount;

    /** 晋级规则扩展 JSON，如 {"groupTop":2,"bestLoser":8,"target":16} */
    @TableField("advance_json")
    private String advanceJson;

    /** 本轮平局判定规则 JSON（次优成绩 → 最远成功轮次 → 并列） */
    @TableField("tie_break_json")
    private String tieBreakJson;

    /** 晋级来源轮次 ID（上一轮），关联 event_round.id */
    @TableField("source_round_id")
    private Long sourceRoundId;

    /** 未配置项是否继承项目级规则：0 否 / 1 是 */
    @TableField("is_inherit")
    private Integer isInherit;

    /** 超风速成绩是否排除出递补：0 否 / 1 是 */
    @TableField("exclude_illegal_wind")
    private Integer excludeIllegalWind;

    /** 人工指定晋级人数（覆盖自动计算） */
    @TableField("manual_advance_count")
    private Integer manualAdvanceCount;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
