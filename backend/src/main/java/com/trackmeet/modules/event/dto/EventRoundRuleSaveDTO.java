package com.trackmeet.modules.event.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 轮次级规则（晋级规则与录取名次）保存入参。
 * <p>
 * 对应 {@code event_round_rule}（{@code round_id} 唯一，保存时先查后插 / 更新）。
 * {@code advanceJson} 与 {@code tieBreakJson} 必须是合法 JSON 文本，非法将由服务层抛 {@code PARAM_ERROR}。
 *
 * @author TrackMeet
 */
@Data
public class EventRoundRuleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 轮次 ID（路径 {@code /api/rounds/{id}/rule} 已携带；传值时需与路径一致） */
    private Long roundId;

    /** 本轮录取名次（取前 N 名） */
    private Integer admitRank;

    /** 每组直接晋级前 N 名 */
    private Integer groupTopN;

    /** 剩余最好成绩递补人数 */
    private Integer bestLoserCount;

    /** 目标晋级总人数 */
    private Integer targetCount;

    /** 晋级规则 JSON，如 {"groupTop":2,"bestLoser":8,"target":16,"excludeIllegalWind":true} */
    private String advanceJson;

    /** 本轮平局规则 JSON */
    private String tieBreakJson;

    /** 晋级来源轮次 ID（上一轮），关联 event_round.id */
    private Long sourceRoundId;

    /** 未配置项是否继承项目级规则：0 否 / 1 是 */
    private Integer isInherit;

    /** 超风速成绩是否排除出递补：0 否 / 1 是 */
    private Integer excludeIllegalWind;

    /** 人工指定晋级人数（覆盖自动计算） */
    private Integer manualAdvanceCount;

    /** 备注 */
    private String remark;
}
