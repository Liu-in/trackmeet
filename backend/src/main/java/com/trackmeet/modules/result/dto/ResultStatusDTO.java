package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成绩特殊状态标记入参。
 * <p>
 * 用于批量标记 {@code DNS/DNF/DSQ/NM/PASS/SCRATCH}：服务层会把这些成绩的
 * {@code result_value}、{@code rank_no}、{@code rank_display} 置空，使其不参与名次；
 * 标记 {@code DSQ} 时 {@link #foulType} 必填，否则返回 {@code PARAM_ERROR}。
 *
 * @author TrackMeet
 */
@Data
public class ResultStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID 列表（至少一条） */
    @NotEmpty(message = "成绩 ID 不能为空")
    private List<Long> ids;

    /** 成绩状态：VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH */
    @Pattern(regexp = "^(VALID|DNS|DNF|DSQ|NM|PASS|SCRATCH)$",
            message = "成绩状态只能是 VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH")
    private String status;

    /** 犯规类型码，DSQ 必填 */
    @Size(max = 32, message = "犯规类型长度不能超过 32")
    private String foulType;

    /** 判罚依据说明，DSQ 建议填写 */
    @Size(max = 200, message = "判罚依据说明长度不能超过 200")
    private String foulReason;

    /** 变更原因（写入留痕 reason） */
    @Size(max = 500, message = "变更原因长度不能超过 500")
    private String reason;
}
