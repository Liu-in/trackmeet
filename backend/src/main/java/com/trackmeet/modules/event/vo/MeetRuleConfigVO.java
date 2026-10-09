package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 届次级默认规则视图对象（对应 {@code meet_rule_config}）。
 * <p>
 * 字段名与前端契约严格一致；三个 JSON 列以原始 JSON 文本返回。
 * 读取接口既返回库中原始值（未配置字段为 {@code null}），
 * 也通过 {@code GET /api/meets/{id}/rule-config} 返回「已套用系统兜底默认」的生效值。
 *
 * @author TrackMeet
 */
@Data
public class MeetRuleConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（届次级规则未配置时为 null） */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 默认录取名次 */
    private Integer admitRank;

    /** 默认名次分方案 score_rule.id */
    private Long scoreRuleId;

    /** 每人限报项数（0 表示不限） */
    private Integer athleteMaxEvents;

    /** 每人限报接力项数 */
    private Integer relayMaxPerAthlete;

    /** 每单位每项限报人数 */
    private Integer maxPerUnit;

    /** 每单位报名总人数上限 */
    private Integer maxAthletePerUnit;

    /** 兼项最小间隔分钟数 */
    private Integer conflictIntervalMin;

    /** 裁判连场最小间隔分钟数 */
    private Integer refereeIntervalMin;

    /** 接力得分双倍系数 */
    private BigDecimal relayDoubleFactor;

    /** 破纪录加分是否计入团体分：1 计入 / 0 不计入 */
    private Integer bonusInTeamScore;

    /** 奖牌榜排序：GOLD_FIRST/TOTAL_FIRST/POINT_FIRST */
    private String medalSort;

    /** 计时精度：HUNDREDTH/TENTH/SECOND */
    private String timePrecision;

    /** 田赛默认试跳 / 试掷次数 */
    private Integer fieldAttemptCount;

    /** 默认道数（6 或 8） */
    private Integer defaultLaneCount;

    /** 默认道次顺序 JSON 原文 */
    private String defaultLaneOrderJson;

    /** 默认道次分配方式：SEED/DRAW/SERPENT */
    private String laneAssignMode;

    /** 风速上限规则 JSON 原文 */
    private String windJson;

    /** 默认平局规则 JSON 原文 */
    private String tieBreakJson;

    /** 届次规则扩展项 JSON 原文 */
    private String extraJson;
}
