package com.trackmeet.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * @author TrackMeet
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 4, max = 64, message = "密码长度需在 4-64 之间")
    private String password;

    /** 验证码标识（开启验证码时必填） */
    @Size(max = 64)
    private String captchaKey;

    /** 验证码内容（开启验证码时必填） */
    @Size(max = 8)
    private String captchaCode;
}
