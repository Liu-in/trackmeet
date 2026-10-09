package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 田赛出场顺序生成入参。
 *
 * @author TrackMeet
 */
@Data
public class FieldOrderGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排序方式：随机 */
    public static final String MODE_RANDOM = "RANDOM";
    /** 排序方式：按报名成绩 */
    public static final String MODE_SEED = "SEED";
    /** 排序方式：按单位 */
    public static final String MODE_UNIT = "UNIT";
    /** 排序方式：按号码 */
    public static final String MODE_BIB = "BIB";

    /** 日程条目 ID */
    @NotNull(message = "日程条目不能为空")
    private Long scheduleId;

    /** 排序方式：RANDOM / SEED / UNIT / BIB，缺省 SEED */
    @Size(max = 32, message = "排序方式长度不能超过 32")
    private String mode;

    /** 是否落库（缺省 true；false 仅返回预览） */
    private Boolean apply;

    /** 抽签随机种子（保证可复现） */
    private Long randomSeed;
}
