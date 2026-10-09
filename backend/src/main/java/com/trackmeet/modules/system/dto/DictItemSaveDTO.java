package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 字典项新增 / 修改入参。
 *
 * @author TrackMeet
 */
@Data
public class DictItemSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，新增留空，修改时由路径变量指定 */
    private Long id;

    /** 所属字典类型编码，如 event_type */
    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 64, message = "字典类型编码长度不能超过 64")
    private String dictType;

    /** 显示名，如 径赛 */
    @NotBlank(message = "字典项显示名不能为空")
    @Size(max = 100, message = "字典项显示名长度不能超过 100")
    private String itemLabel;

    /** 存储码，如 TRACK */
    @NotBlank(message = "字典项存储码不能为空")
    @Size(max = 64, message = "字典项存储码长度不能超过 64")
    private String itemValue;

    /** 上级字典项码，支持级联 */
    @Size(max = 64, message = "上级字典项码长度不能超过 64")
    private String parentValue;

    /** 状态标签样式类 */
    @Size(max = 64, message = "样式类长度不能超过 64")
    private String cssClass;

    /** 1 为默认选中项 */
    @Min(value = 0, message = "默认项标识只能为 0 或 1")
    @Max(value = 1, message = "默认项标识只能为 0 或 1")
    private Integer isDefault;

    /** 同类型内排序号 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortNo;

    /** 扩展属性（JSON 文本） */
    @Size(max = 2000, message = "扩展属性长度不能超过 2000")
    private String extraJson;

    /** 状态：ENABLED 启用 / DISABLED 停用，留空默认 ENABLED */
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
