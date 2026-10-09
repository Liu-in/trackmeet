package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目级规则视图对象（对应 {@code event_rule_config}）。
 * <p>
 * <b>规则来源标注</b>：读取采用「项目级 &gt; 届次级 &gt; 系统兜底默认」的三层继承，
 * 每个配置值都配一个同名的 {@code xxxSource} 字段，取值：
 * <ul>
 *     <li>{@code PROJECT} —— 命中 {@code event_rule_config}（项目级）</li>
 *     <li>{@code MEET} —— 项目级为空，命中 {@code meet_rule_config}（届次级）</li>
 *     <li>{@code DEFAULT} —— 两处均为空，命中系统兜底默认常量</li>
 * </ul>
 * 前端可据此标注「本项目自定义 / 继承届次 / 系统默认」。
 *
 * @author TrackMeet
 */
@Data
public class EventRuleConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源：项目级 */
    public static final String SOURCE_PROJECT = "PROJECT";
    /** 来源：届次级 */
    public static final String SOURCE_MEET = "MEET";
    /** 来源：系统兜底默认 */
    public static final String SOURCE_DEFAULT = "DEFAULT";

    /** 项目级规则行主键（项目级规则不存在时为 null） */
    private Long id;

    /** 项目 ID */
    private Long eventId;

    /** 录取名次（取前 N 名） */
    private Integer admitRank;

    /** admitRank 的来源标记 */
    private String admitRankSource;

    /** 分组方式：SERPENT/RANDOM/SEED/UNIT */
    private String drawMode;

    /** drawMode 的来源标记 */
    private String drawModeSource;

    /** 道次顺序 JSON 原文 */
    private String laneOrderJson;

    /** laneOrderJson 的来源标记 */
    private String laneOrderJsonSource;

    /** 道次分配方式：SEED/DRAW/SERPENT/ORDER */
    private String laneAssignMode;

    /** laneAssignMode 的来源标记 */
    private String laneAssignModeSource;

    /** 兼项最小间隔（分钟） */
    private Integer conflictIntervalMin;

    /** conflictIntervalMin 的来源标记 */
    private String conflictIntervalMinSource;

    /** 风速规则 JSON 原文 */
    private String windJson;

    /** windJson 的来源标记 */
    private String windJsonSource;

    /** 田赛试跳 / 试掷次数 */
    private Integer attemptCount;

    /** attemptCount 的来源标记 */
    private String attemptCountSource;

    /** 及格赛标准原文（仅项目级可配，届次级不提供时来源为 DEFAULT 且值为空） */
    private String qualifyStandard;

    /** qualifyStandard 的来源标记 */
    private String qualifyStandardSource;

    /** 及格标准可比较数值 */
    private BigDecimal qualifyStandardValue;

    /** qualifyStandardValue 的来源标记 */
    private String qualifyStandardValueSource;

    /** 升杆方案 JSON 原文 */
    private String heightPlanJson;

    /** heightPlanJson 的来源标记 */
    private String heightPlanJsonSource;

    /** 平局规则 JSON 原文 */
    private String tieBreakJson;

    /** tieBreakJson 的来源标记 */
    private String tieBreakJsonSource;

    /** 项目预计时长（分钟） */
    private Integer durationMin;

    /** durationMin 的来源标记 */
    private String durationMinSource;

    /** 其他扩展规则 JSON 原文 */
    private String extraJson;

    /** extraJson 的来源标记 */
    private String extraJsonSource;
}
