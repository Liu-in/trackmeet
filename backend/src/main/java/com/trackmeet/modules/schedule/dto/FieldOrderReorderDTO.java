package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 田赛出场顺序调整入参：按 memberIds 数组顺序重排分组内 order_no（从 1 开始）。
 *
 * @author TrackMeet
 */
@Data
public class FieldOrderReorderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分组 ID heat.id */
    @NotNull(message = "分组不能为空")
    private Long heatId;

    /** 组内人员 ID 列表（按目标出场顺序排列） */
    @NotEmpty(message = "顺序列表不能为空")
    private List<Long> memberIds;
}
