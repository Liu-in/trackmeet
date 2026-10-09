package com.trackmeet.modules.athlete.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量限制校验请求（不落库）：items 元素结构与 {@link RegistrationSaveDTO} 一致。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationCheckDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（可空，空时取请求头 X-Meet-Id 或 meetId 参数） */
    private Long meetId;

    /** 待校验的报名明细 */
    @NotEmpty(message = "待校验的报名明细不能为空")
    @Valid
    private List<RegistrationSaveDTO> items;
}
