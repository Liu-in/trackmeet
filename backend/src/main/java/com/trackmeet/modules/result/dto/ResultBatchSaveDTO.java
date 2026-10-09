package com.trackmeet.modules.result.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成绩批量录入入参：一组（一个日程条目）一次提交多条成绩。
 *
 * @author TrackMeet
 */
@Data
public class ResultBatchSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID，缺省取当前届次上下文 */
    private Long meetId;

    /** 日程条目 ID（本批成绩统一归属的日程条目） */
    @NotNull(message = "日程条目不能为空")
    private Long scheduleId;

    /** 本批成绩明细（至少一条） */
    @NotEmpty(message = "成绩明细不能为空")
    @Valid
    private List<ResultSaveDTO> results;
}
