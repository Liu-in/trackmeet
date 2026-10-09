package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 分组锁定/解锁入参。
 * <p>
 * 列名为 {@code heat.is_locked}，接口字段统一为 {@code isLocked}（0 未锁定 / 1 已锁定）。
 *
 * @author TrackMeet
 */
@Data
public class HeatLockDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否锁定：0 未锁定 / 1 已锁定（锁定后重新生成分组时跳过本组） */
    @NotNull(message = "锁定状态不能为空")
    private Integer isLocked;
}
