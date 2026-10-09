package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成绩审核通过入参（批量）。
 * <p>
 * 审核通过后：{@code audit_status = APPROVED}、{@code is_locked = 1}、记录审核人与审核时间，
 * 并自动触发本轮名次生成（{@code RankService.generateRank}）。
 *
 * @author TrackMeet
 */
@Data
public class ResultApproveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID 列表（至少一条） */
    @NotEmpty(message = "成绩 ID 不能为空")
    private List<Long> ids;

    /** 审核意见（写入留痕 reason） */
    private String reason;
}
