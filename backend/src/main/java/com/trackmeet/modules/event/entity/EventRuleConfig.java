package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * event_rule_config 项目级规则表：项目级规则中枢 —— 录取、分组、道次、风速、试跳、升杆、平局、时长。
 * <p>
 * 字段与 {@code sql/03-schema-event-schedule.sql} 的 {@code event_rule_config} 表逐列一致；
 * {@code event_id} 上有唯一键 {@code uk_evrule_event}，与项目一对一。
 * <p>
 * 空值语义为「继承上一层」：读取优先级
 * {@code event_round_rule} &gt; {@code event_rule_config} &gt; {@code meet_rule_config} &gt; 系统兜底默认。
 * JSON 列以原始 JSON 文本（{@link String}）接收与返回，不做结构化映射。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_rule_config")
public class EventRuleConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 项目 ID，关联 event.id，一对一 */
    @TableField("event_id")
    private Long eventId;

    /** 录取名次（取前 N 名，默认 8） */
    @TableField("admit_rank")
    private Integer admitRank;

    /** 分组方式：SERPENT 蛇形 / RANDOM 随机 / SEED 成绩段 / UNIT 按单位 */
    @TableField("draw_mode")
    private String drawMode;

    /** 分组时同单位是否避开：0 否 / 1 是 */
    @TableField("draw_avoid_same_unit")
    private Integer drawAvoidSameUnit;

    /** 分组计划每组人数 */
    @TableField("draw_group_size")
    private Integer drawGroupSize;

    /** 本项目使用道数（8 道 / 6 道） */
    @TableField("lane_count")
    private Integer laneCount;

    /** 道次顺序 JSON：8 道 [4,5,3,6,2,7,1,8]；6 道 [3,4,2,5,1,6] */
    @TableField("lane_order_json")
    private String laneOrderJson;

    /** 道次分配方式：SEED 按种子 / DRAW 抽签 / SERPENT 蛇形 / ORDER 按顺序 */
    @TableField("lane_assign_mode")
    private String laneAssignMode;

    /** 兼项最小间隔（分钟，默认 30） */
    @TableField("conflict_interval_min")
    private Integer conflictIntervalMin;

    /** 风速规则 JSON，如 {"applicable":true,"maxLegal":2.0} */
    @TableField("wind_json")
    private String windJson;

    /** 田赛预赛试跳 / 试掷次数（默认 3） */
    @TableField("attempt_count")
    private Integer attemptCount;

    /** 田赛决赛试跳 / 试掷次数（默认 3） */
    @TableField("final_attempt_count")
    private Integer finalAttemptCount;

    /** 及格赛标准原文 */
    @TableField("qualify_standard")
    private String qualifyStandard;

    /** 及格标准可比较数值 */
    @TableField("qualify_standard_value")
    private BigDecimal qualifyStandardValue;

    /** 及格标准单位：s 秒 / m 米 / cm 厘米 */
    @TableField("qualify_standard_unit")
    private String qualifyStandardUnit;

    /** 升杆方案 JSON（起跳高度、每轮升幅、免跳） */
    @TableField("height_plan_json")
    private String heightPlanJson;

    /** 平局规则 JSON（次优成绩 → 最远成功轮次 → 并列） */
    @TableField("tie_break_json")
    private String tieBreakJson;

    /** 项目预计时长（分钟） */
    @TableField("duration_min")
    private Integer durationMin;

    /** 未配置项是否继承届次默认值：0 否 / 1 是 */
    @TableField("is_inherit_meet")
    private Integer isInheritMeet;

    /** 其他扩展规则 JSON（抽签种子数、重赛规则等） */
    @TableField("extra_json")
    private String extraJson;
}
