package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 届次级默认规则保存入参（对应 {@code meet_rule_config}）。
 * <p>
 * 保存语义：按 {@code meet_id} 不存在则插入、存在则更新（唯一键 {@code uk_meet_id}）；
 * 仅写入非空字段（MyBatis-Plus 默认非空更新策略），未传字段保持原值 / 回退系统兜底默认。
 *
 * @author TrackMeet
 */
@Data
public class MeetRuleConfigSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认录取名次（如 8 取前 8 名） */
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

    /** 兼项最小间隔分钟数（冲突检测阈值） */
    private Integer conflictIntervalMin;

    /** 裁判连场最小间隔分钟数 */
    private Integer refereeIntervalMin;

    /** 接力得分双倍系数（默认 2.00） */
    private BigDecimal relayDoubleFactor;

    /** 破纪录加分是否计入团体分：1 计入 / 0 不计入 */
    private Integer bonusInTeamScore;

    /** 奖牌榜排序：GOLD_FIRST 金牌优先 / TOTAL_FIRST 奖牌总数优先 / POINT_FIRST 积分优先 */
    @Pattern(regexp = "^(GOLD_FIRST|TOTAL_FIRST|POINT_FIRST)?$", message = "奖牌榜排序取值不正确")
    private String medalSort;

    /** 计时精度：HUNDREDTH 百分秒 / TENTH 十分秒 / SECOND 秒 */
    @Pattern(regexp = "^(HUNDREDTH|TENTH|SECOND)?$", message = "计时精度取值不正确")
    private String timePrecision;

    /** 田赛默认试跳 / 试掷次数（如 3 次） */
    private Integer fieldAttemptCount;

    /** 默认道数（6 或 8） */
    private Integer defaultLaneCount;

    /** 默认道次顺序 JSON，8 道为 [4,5,3,6,2,7,1,8] */
    private String defaultLaneOrderJson;

    /** 默认道次分配方式：SEED 按种子 / DRAW 抽签 / SERPENT 蛇形 */
    @Pattern(regexp = "^(SEED|DRAW|SERPENT|ORDER)?$", message = "默认道次分配方式取值不正确")
    private String laneAssignMode;

    /** 风速上限规则 JSON（各项目风速上限与超风速处理） */
    private String windJson;

    /** 默认平局规则 JSON（比较次优成绩、名次并列、抽签等顺序） */
    private String tieBreakJson;

    /** 届次规则扩展项 JSON（未结构化的默认值） */
    private String extraJson;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
