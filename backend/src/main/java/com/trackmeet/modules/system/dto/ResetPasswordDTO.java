package com.trackmeet.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 重置密码入参：{@code {"password":"..."}}。
 * <p>密码为明文传输，序列化时忽略，禁止记录到操作日志。
 *
 * @author TrackMeet
 */
@Data
public class ResetPasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新密码明文，服务端以 BCrypt 加密后落库 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
}
