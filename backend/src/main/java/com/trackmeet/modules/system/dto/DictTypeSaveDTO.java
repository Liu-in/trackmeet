package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 字典类型新增 / 修改入参。
 *
 * @author TrackMeet
 */
@Data
public class DictTypeSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，新增留空，修改时由路径变量指定 */
    private Long id;

    /** 字典类型编码，唯一，如 event_type */
    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 64, message = "字典类型编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{0,63}$",
            message = "字典类型编码需以字母开头，仅允许字母、数字、下划线、点、短横线，长度不超过 64")
    private String dictType;

    /** 字典类型名称 */
    @NotBlank(message = "字典类型名称不能为空")
    @Size(max = 100, message = "字典类型名称长度不能超过 100")
    private String dictName;

    /** 字典分组，如 TRACK_FIELD */
    @Size(max = 64, message = "字典分组长度不能超过 64")
    private String dictGroup;

    /** 1 系统内置，禁止删除 */
    @Min(value = 0, message = "系统内置标识只能为 0 或 1")
    @Max(value = 1, message = "系统内置标识只能为 0 或 1")
    private Integer isSystem;

    /** 1 全局字典，届次间共用 */
    @Min(value = 0, message = "全局标识只能为 0 或 1")
    @Max(value = 1, message = "全局标识只能为 0 或 1")
    private Integer isGlobal;

    /** 状态：ENABLED 启用 / DISABLED 停用，留空默认 ENABLED */
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    /** 列表显示顺序 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortNo;

    /** 扩展属性（JSON 文本） */
    @Size(max = 2000, message = "扩展属性长度不能超过 2000")
    private String extraJson;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
