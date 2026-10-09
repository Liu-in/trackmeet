package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 破纪录认定入参。
 * <p>
 * {@code confirmed = true} 时：回写 {@code result.is_record_confirmed = 1}、{@code record_id}
 * 与 {@code record_type}；若该成绩为超风速（{@code is_wind_legal = 0}）则抛
 * {@code WIND_ILLEGAL}（风速超限，纪录不予认定），但保留 {@code is_record_candidate} 候选位。
 * {@code confirmed = false} 时：取消认定，{@code is_record_confirmed = 0} 并清空 {@code record_id}。
 *
 * @author TrackMeet
 */
@Data
public class RecordConfirmDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID */
    @NotNull(message = "成绩 ID 不能为空")
    private Long resultId;

    /** 认定纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP；留空时按届次内现行纪录自动匹配 */
    @Pattern(regexp = "^(WORLD|ASIA|NATION|PROVINCE|CITY|MEET|GROUP)?$",
            message = "纪录层级只能是 WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP")
    private String recordType;

    /** 对标纪录 ID（record.id），与 recordType 同时给出时优先使用 */
    private Long recordId;

    /** 是否认定：true 认定 / false 取消认定 */
    @NotNull(message = "是否认定不能为空")
    private Boolean confirmed;

    /** 认定说明或取消原因（写入留痕与备注） */
    private String reason;
}
