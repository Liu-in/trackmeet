package com.trackmeet.modules.standing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 积分与榜单重算入参（{@code POST /api/standings/recalculate}）。
 * <p>
 * 范围语义：{@code meetId} 必填；{@code groupId} / {@code eventId} 为空表示该届次全量重算。
 * 重算为幂等操作——先在范围内物理删除 {@code points} / {@code team_score} / {@code medal_standing}
 * 再整批重建。
 *
 * @author TrackMeet
 */
@Data
public class StandingRecalculateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填，须与 MeetContext 一致） */
    @NotNull(message = "届次不能为空")
    private Long meetId;

    /** 组别 ID（可空，空为全部组别） */
    private Long groupId;

    /** 项目 ID（可空，空为全部项目） */
    private Long eventId;
}
