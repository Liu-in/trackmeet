package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户启用 / 停用入参：{@code {"status":"ENABLED"}}。
 *
 * @author TrackMeet
 */
@Data
public class UserStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：ENABLED 启用 / DISABLED 停用 */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;
}
