package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 轮次级规则视图对象（对应 {@code event_round_rule}）。
 * <p>
 * 字段名与前端契约严格一致；两个 JSON 列以原始 JSON 文本返回，由前端解析渲染。
 *
 * @author TrackMeet
 */
@Data
public class EventRoundRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 轮次 ID，关联 event_round.id，一对一 */
    private Long roundId;

    /** 本轮录取名次（取前 N 名） */
    private Integer admitRank;

    /** 每组直接晋级前 N 名 */
    private Integer groupTopN;

    /** 剩余最好成绩递补人数 */
    private Integer bestLoserCount;

    /** 目标晋级总人数 */
    private Integer targetCount;

    /** 晋级规则扩展 JSON 原文 */
    private String advanceJson;

    /** 本轮平局判定规则 JSON 原文 */
    private String tieBreakJson;

    /** 晋级来源轮次 ID（上一轮） */
    private Long sourceRoundId;
}
