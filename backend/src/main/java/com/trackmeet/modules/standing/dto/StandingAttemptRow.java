package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 田赛试跳/试掷投影行：装配 {@code ResultRecord.attempts} 的只读数据源。
 * <p>
 * 引擎的平局规则（{@code SECOND_BEST} / {@code BEST_ATTEMPT_ROUND}）需要按轮次顺序的
 * 试跳成绩值列表，失败与免跳以 {@code null} 占位。
 *
 * @author TrackMeet
 */
@Data
public class StandingAttemptRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 试跳/试掷 ID */
    private Long id;

    /** 所属成绩 ID（result.id） */
    private Long resultId;

    /** 届次 ID */
    private Long meetId;

    /** 试跳/试掷总序号，从 1 递增 */
    private Integer attemptNo;

    /** 单次成绩数值（米/厘米） */
    private BigDecimal attemptValue;

    /** 是否成功：1 成功 / 0 失败 */
    private Integer success;

    /** 是否免跳：1 免跳 / 0 否 */
    private Integer pass;

    /** 是否犯规：1 犯规 / 0 未犯规 */
    private Integer foul;
}
