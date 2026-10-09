package com.trackmeet.modules.athlete.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 报名退回请求。
 * <p>
 * 接口约定长度 ≤ 200；数据库列 {@code registration.reject_reason} 为 VARCHAR(100)，
 * 服务层会额外按列长度做一次保护性校验，避免超长导致落库失败。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationRejectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退回原因（必填，长度 ≤ 200） */
    @NotBlank(message = "退回原因不能为空")
    @Size(max = 200, message = "退回原因长度不能超过 200")
    private String rejectReason;
}
