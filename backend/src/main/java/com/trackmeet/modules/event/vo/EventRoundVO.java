package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 轮次视图对象。
 * <p>
 * 字段名与前端契约严格一致；{@code rule} 为轮次级规则（{@code event_round_rule}），未配置时为 {@code null}。
 *
 * @author TrackMeet
 */
@Data
public class EventRoundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属项目 ID */
    private Long eventId;

    /** 轮次码：QUALIFY/PRE/R1/R2/SEMI/FINAL */
    private String roundCode;

    /** 轮次名称 */
    private String roundName;

    /** 轮次顺序号，从 1 起 */
    private Integer roundNo;

    /** 是否决赛轮：0 否 / 1 是 */
    private Integer isFinal;

    /** 计划分组数 */
    private Integer planHeatCount;

    /** 计划晋级总人数 */
    private Integer planAdvanceCount;

    /** 状态：PLANNED/READY/RUNNING/FINISHED */
    private String status;

    /** 轮次级规则（晋级规则与录取名次），未配置为 null */
    private EventRoundRuleVO rule;
}
