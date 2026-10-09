package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 自动晋级入参。
 * <p>
 * 缺省值取 {@code event_round_rule}：{@code group_top_n}（每组前 N）、
 * {@code best_loser_count}（最好成绩递补数）、{@code target_count}（目标总人数）、
 * {@code exclude_illegal_wind}（超风速是否排除递补）。
 * 生成后 {@code advance_result} 按「先删后插」幂等重建，{@code is_confirmed = 0}。
 *
 * @author TrackMeet
 */
@Data
public class AdvanceGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    @NotNull(message = "项目不能为空")
    private Long eventId;

    /** 来源轮次 ID（本轮成绩所在轮次） */
    @NotNull(message = "来源轮次不能为空")
    private Long fromRoundId;

    /** 目标轮次 ID（晋级去向的下一轮） */
    @NotNull(message = "目标轮次不能为空")
    private Long toRoundId;

    /** 每组直接晋级前 N 名；留空取轮次规则 */
    private Integer groupTopN;

    /** 剩余最好成绩递补人数；留空取轮次规则 */
    private Integer bestLoserCount;

    /** 目标晋级总人数；留空取轮次规则 */
    private Integer targetCount;

    /** 超风速成绩是否排除出递补；留空取轮次规则，规则也未配置时按 true 处理 */
    private Boolean excludeIllegalWind;

    /** 是否排除无效状态成绩（DNS/DNF/DSQ/NM…），缺省 true */
    private Boolean excludeInvalidStatus;

    /** 平局规则：TIE_PARALLEL 并列 / TIE_BY_SECOND_BEST 次优成绩 / TIE_BY_DRAW 抽签 */
    private String tiePolicy;

    /** 备注（写入晋级名单 remark） */
    private String remark;
}
