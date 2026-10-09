package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成绩审核驳回入参（批量）。
 * <p>
 * 驳回后：{@code audit_status = REJECTED}、{@code is_locked = 0}，
 * 驳回原因同时写入 {@code result.reject_reason} 与 {@code result_audit.reason}。
 *
 * @author TrackMeet
 */
@Data
public class ResultRejectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID 列表（至少一条） */
    @NotEmpty(message = "成绩 ID 不能为空")
    private List<Long> ids;

    /** 驳回原因（必填，≤200） */
    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 200, message = "驳回原因长度不能超过 200")
    private String rejectReason;
}
