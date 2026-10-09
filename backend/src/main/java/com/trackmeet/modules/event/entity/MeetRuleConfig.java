package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * meet_rule_config 届次级规则表：三层继承的最外层（届次级默认规则）。
 * <p>
 * 字段与 {@code sql/02-schema-meet-athlete.sql} 的 {@code meet_rule_config} 表逐列一致；
 * {@code meet_id} 上有唯一键 {@code uk_meet_id}，与届次一对一（保存时「不存在则插入、存在则更新」）。
 * <p>
 * JSON 列（{@code default_lane_order_json}、{@code wind_json}、{@code tie_break_json}、{@code extra_json}）
 * 以原始 JSON 文本（{@link String}）接收与返回。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meet_rule_config")
public class MeetRuleConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（业务表届次隔离列，必填，唯一） */
    @TableField("meet_id")
    private Long meetId;

    /** 默认录取名次（如 8 取前 8 名） */
    @TableField("admit_rank")
    private Integer admitRank;

    /** 默认名次分方案 score_rule.id */
    @TableField("score_rule_id")
    private Long scoreRuleId;

    /** 每人限报项数（0 表示不限） */
    @TableField("athlete_max_events")
    private Integer athleteMaxEvents;

    /** 每人限报接力项数 */
    @TableField("relay_max_per_athlete")
    private Integer relayMaxPerAthlete;

    /** 每单位每项限报人数 */
    @TableField("max_per_unit")
    private Integer maxPerUnit;

    /** 每单位报名总人数上限 */
    @TableField("max_athlete_per_unit")
    private Integer maxAthletePerUnit;

    /** 兼项最小间隔分钟数（冲突检测阈值） */
    @TableField("conflict_interval_min")
    private Integer conflictIntervalMin;

    /** 裁判连场最小间隔分钟数 */
    @TableField("referee_interval_min")
    private Integer refereeIntervalMin;

    /** 接力得分双倍系数（默认 2.00） */
    @TableField("relay_double_factor")
    private BigDecimal relayDoubleFactor;

    /** 破纪录加分是否计入团体分：1 计入 / 0 不计入 */
    @TableField("bonus_in_team_score")
    private Integer bonusInTeamScore;

    /** 奖牌榜排序：GOLD_FIRST 金牌优先 / TOTAL_FIRST 奖牌总数优先 / POINT_FIRST 积分优先 */
    @TableField("medal_sort")
    private String medalSort;

    /** 计时精度：HUNDREDTH 百分秒 / TENTH 十分秒 / SECOND 秒 */
    @TableField("time_precision")
    private String timePrecision;

    /** 田赛默认试跳 / 试掷次数（如 3 次） */
    @TableField("field_attempt_count")
    private Integer fieldAttemptCount;

    /** 默认道数（6 或 8） */
    @TableField("default_lane_count")
    private Integer defaultLaneCount;

    /** 默认道次顺序 JSON，8 道为 [4,5,3,6,2,7,1,8] */
    @TableField("default_lane_order_json")
    private String defaultLaneOrderJson;

    /** 默认道次分配方式：SEED 按种子 / DRAW 抽签 / SERPENT 蛇形 */
    @TableField("lane_assign_mode")
    private String laneAssignMode;

    /** 风速上限规则 JSON（各项目风速上限与超风速处理） */
    @TableField("wind_json")
    private String windJson;

    /** 默认平局规则 JSON（比较次优成绩、名次并列、抽签等顺序） */
    @TableField("tie_break_json")
    private String tieBreakJson;

    /** 届次规则扩展项 JSON（未结构化的默认值） */
    @TableField("extra_json")
    private String extraJson;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
