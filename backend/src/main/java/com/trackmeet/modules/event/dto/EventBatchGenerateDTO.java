package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量生成标准项目入参。
 * <p>
 * 语义：{@code groupIds}（组别）× {@code gender}（性别）× {@code templates}（项目模板类型）做笛卡尔积，
 * 按模板自动填充 {@code event_type}、{@code sub_type}、{@code is_relay}、{@code relay_min}、{@code relay_max}、
 * {@code duration_min}；已存在「同名 + 同组别 + 同性别」的项目直接跳过。
 *
 * @author TrackMeet
 */
@Data
public class EventBatchGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID，未传取当前届次上下文 */
    private Long meetId;

    /** 目标组别 ID 列表 */
    @NotEmpty(message = "组别列表不能为空")
    private List<Long> groupIds;

    /** 适用性别：MALE 男 / FEMALE 女 / MIXED 混合 */
    @NotBlank(message = "性别不能为空")
    @Pattern(regexp = "^(MALE|FEMALE|MIXED)$", message = "性别码只能是 MALE/FEMALE/MIXED")
    private String gender;

    /** 项目模板类型列表，元素取值：100米 / 200米 / 400米 / 800米 / 1500米 / 跳远 / 跳高 / 铅球 / 4×100米接力 */
    @NotEmpty(message = "项目模板列表不能为空")
    private List<String> templates;
}
