package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 两道次交换入参（同一分组内的两条道次）。
 *
 * @author TrackMeet
 */
@Data
public class LaneSwapDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 道次记录 A */
    @NotNull(message = "道次记录 A 不能为空")
    private Long laneIdA;

    /** 道次记录 B */
    @NotNull(message = "道次记录 B 不能为空")
    private Long laneIdB;

    /** 交换原因，如申诉调整 */
    @Size(max = 100, message = "交换原因长度不能超过 100")
    private String swapReason;
}
