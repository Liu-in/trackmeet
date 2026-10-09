package com.trackmeet.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求参数。
 *
 * @author TrackMeet
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    @Size(max = 64)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在 6-64 之间")
    private String newPassword;
}
