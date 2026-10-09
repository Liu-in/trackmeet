package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色新增 / 修改入参。
 *
 * @author TrackMeet
 */
@Data
public class RoleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，新增留空，修改时由路径变量指定 */
    private Long id;

    /** 角色编码，唯一，如 SCHEDULER */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{0,63}$",
            message = "角色编码需以字母开头，仅允许字母、数字、下划线、点、短横线，长度不超过 64")
    private String roleCode;

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过 100")
    private String roleName;

    /** 数据范围：ALL / MEET / UNIT / SELF / REF，留空默认 ALL */
    @Pattern(regexp = "^(ALL|MEET|UNIT|SELF|REF)$", message = "数据范围只能为 ALL、MEET、UNIT、SELF 或 REF")
    private String dataScope;

    /** REF 时的部门/单位/届次 ID 集合（JSON 文本） */
    @Size(max = 2000, message = "数据范围扩展内容长度不能超过 2000")
    private String dataScopeJson;

    /** 状态：ENABLED 启用 / DISABLED 停用，留空默认 ENABLED */
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    /** 列表显示顺序 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 同时授予的权限 ID 集合，null 表示不调整授权 */
    private List<Long> permissionIds;
}
