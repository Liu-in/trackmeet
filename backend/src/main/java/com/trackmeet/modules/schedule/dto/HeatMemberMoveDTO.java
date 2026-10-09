package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 组间拖拽移动入参：把 memberId 移动到目标分组的目标顺序号。
 *
 * @author TrackMeet
 */
@Data
public class HeatMemberMoveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组内人员 ID heat_member.id */
    @NotNull(message = "组内人员不能为空")
    private Long memberId;

    /** 目标分组 ID heat.id */
    @NotNull(message = "目标分组不能为空")
    private Long targetHeatId;

    /** 目标顺序号（从 1 开始；为空则追加到组末） */
    private Integer targetOrderNo;
}
