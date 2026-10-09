package com.trackmeet.modules.standing.dto;

import com.trackmeet.common.base.BaseQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分明细下钻查询条件。
 * <p>
 * 在 {@link StandingQuery} 基础上直接复用分页能力（{@code page}/{@code size}），
 * 支持按届次、组别、项目、单位过滤。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填，须与 MeetContext 一致） */
    @NotNull(message = "届次不能为空")
    private Long meetId;

    /** 组别 ID（空为全部组别） */
    private Long groupId;

    /** 项目 ID（空为全部项目） */
    private Long eventId;

    /** 参赛单位 ID（空为全部单位） */
    private Long unitId;
}
