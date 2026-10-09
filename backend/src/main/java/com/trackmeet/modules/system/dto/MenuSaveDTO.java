package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 菜单新增 / 修改入参。
 *
 * @author TrackMeet
 */
@Data
public class MenuSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，新增留空，修改时由路径变量指定 */
    private Long id;

    /** 父节点，0 为根，留空默认 0 */
    private Long parentId;

    /** 菜单/按钮名称 */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 100, message = "菜单名称长度不能超过 100")
    private String menuName;

    /** 菜单类型：D 目录 / M 菜单 / B 按钮 */
    @NotBlank(message = "菜单类型不能为空")
    @Pattern(regexp = "^[DMB]$", message = "菜单类型只能为 D、M 或 B")
    private String menuType;

    /** 路由路径 */
    @Size(max = 200, message = "路由路径长度不能超过 200")
    private String path;

    /** 前端组件路径 */
    @Size(max = 200, message = "组件路径长度不能超过 200")
    private String component;

    /** 图标标识 */
    @Size(max = 64, message = "图标长度不能超过 64")
    private String icon;

    /** 权限编码，与 sys_permission.perm_code 一致 */
    @Size(max = 64, message = "权限编码长度不能超过 64")
    private String permCode;

    /** 1 导航可见，0 隐藏，留空默认 1 */
    @Min(value = 0, message = "可见标识只能为 0 或 1")
    @Max(value = 1, message = "可见标识只能为 0 或 1")
    private Integer visible;

    /** 1 页面缓存，0 不缓存，留空默认 0 */
    @Min(value = 0, message = "缓存标识只能为 0 或 1")
    @Max(value = 1, message = "缓存标识只能为 0 或 1")
    private Integer keepAlive;

    /** 1 外链，0 内部路由，留空默认 0 */
    @Min(value = 0, message = "外链标识只能为 0 或 1")
    @Max(value = 1, message = "外链标识只能为 0 或 1")
    private Integer isExternal;

    /** 状态：ENABLED 启用 / DISABLED 停用，留空默认 ENABLED */
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    /** 同级排序号 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
