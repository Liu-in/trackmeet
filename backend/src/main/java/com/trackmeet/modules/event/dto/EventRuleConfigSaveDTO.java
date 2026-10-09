package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目级规则保存入参（对应 {@code event_rule_config}，与项目一对一）。
 * <p>
 * 空值语义为「继承」：未传（{@code null}）的字段不写入，
 * 读取时按 {@code event_rule_config} &gt; {@code meet_rule_config} &gt; 系统兜底默认的顺序回退。
 * JSON 列（laneOrderJson、windJson、heightPlanJson、tieBreakJson、extraJson）以原始 JSON 文本提交。
 *
 * @author TrackMeet
 */
@Data
public class EventRuleConfigSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID（路径 {@code /api/events/{id}/rule-config} 已携带；传值时需与路径一致） */
    private Long eventId;

    /** 录取名次（取前 N 名） */
    private Integer admitRank;

    /** 分组方式：SERPENT 蛇形 / RANDOM 随机 / SEED 成绩段 / UNIT 按单位 */
    @Pattern(regexp = "^(SERPENT|RANDOM|SEED|UNIT)?$", message = "分组方式只能是 SERPENT/RANDOM/SEED/UNIT")
    private String drawMode;

    /** 分组时同单位是否避开：0 否 / 1 是 */
    private Integer drawAvoidSameUnit;

    /** 分组计划每组人数 */
    private Integer drawGroupSize;

    /** 本项目使用道数（8 道 / 6 道） */
    private Integer laneCount;

    /** 道次顺序 JSON，默认 8 道 [4,5,3,6,2,7,1,8]、6 道 [3,4,2,5,1,6] */
    private String laneOrderJson;

    /** 道次分配方式：SEED 按种子 / DRAW 抽签 / SERPENT 蛇形 / ORDER 按顺序 */
    @Pattern(regexp = "^(SEED|DRAW|SERPENT|ORDER)?$", message = "道次分配方式只能是 SEED/DRAW/SERPENT/ORDER")
    private String laneAssignMode;

    /** 兼项最小间隔（分钟） */
    private Integer conflictIntervalMin;

    /** 风速规则 JSON，如 {"applicable":true,"maxLegal":2.0} */
    private String windJson;

    /** 田赛预赛试跳 / 试掷次数 */
    private Integer attemptCount;

    /** 田赛决赛试跳 / 试掷次数 */
    private Integer finalAttemptCount;

    /** 及格赛标准原文 */
    @Size(max = 100, message = "及格赛标准原文长度不能超过 100")
    private String qualifyStandard;

    /** 及格标准可比较数值 */
    private BigDecimal qualifyStandardValue;

    /** 及格标准单位：s 秒 / m 米 / cm 厘米 */
    @Pattern(regexp = "^(s|m|cm)?$", message = "及格标准单位只能是 s/m/cm")
    private String qualifyStandardUnit;

    /** 升杆方案 JSON */
    private String heightPlanJson;

    /** 平局规则 JSON */
    private String tieBreakJson;

    /** 项目预计时长（分钟） */
    private Integer durationMin;

    /** 未配置项是否继承届次默认值：0 否 / 1 是 */
    private Integer isInheritMeet;

    /** 其他扩展规则 JSON */
    private String extraJson;
}
