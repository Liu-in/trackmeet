package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 名次生成入参。
 * <p>
 * 排序列由 {@link #trackAscending} 决定：径赛成绩越小越好传 {@code true}（默认），
 * 田赛/全能成绩越大越好传 {@code false}；缺省时服务层按项目类型（TRACK/RELAY 为 true）推断。
 *
 * @author TrackMeet
 */
@Data
public class RankGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    @NotNull(message = "项目不能为空")
    private Long eventId;

    /** 轮次 ID */
    @NotNull(message = "轮次不能为空")
    private Long roundId;

    /** 成绩排序方向：true 升序（径赛用时）、false 降序（田赛远度/高度）；留空按项目类型推断 */
    private Boolean trackAscending;

    /** 平局规则顺序：SECOND_BEST/BEST_ATTEMPT_ROUND/DRAW；留空取轮次级/项目级配置 */
    private String tieBreakOrder;
}
