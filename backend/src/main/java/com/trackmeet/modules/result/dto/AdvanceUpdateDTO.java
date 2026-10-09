package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 晋级名单手动调整入参。
 * <p>
 * 人工调整后该条记录的 {@code advance_type} 置为 {@code MANUAL}，
 * 并以 {@link #advanceOrder} 重排新轮次中的顺序号；
 * 可通过 {@link #advanceType} 回填为 {@code GROUP_TOP} / {@code BEST_LOSER} 以还原来源语义。
 *
 * @author TrackMeet
 */
@Data
public class AdvanceUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID（路径参数已给定时可留空） */
    private Long id;

    /** 晋级方式：GROUP_TOP/BEST_LOSER/MANUAL，留空按 MANUAL 处理 */
    @Pattern(regexp = "^(GROUP_TOP|BEST_LOSER|MANUAL)?$",
            message = "晋级方式只能是 GROUP_TOP/BEST_LOSER/MANUAL")
    private String advanceType;

    /** 晋级后在新轮次中的顺序号（从 1 开始） */
    @NotNull(message = "晋级顺序号不能为空")
    private Integer advanceOrder;

    /** 调整原因（写入 remark） */
    private String remark;
}
