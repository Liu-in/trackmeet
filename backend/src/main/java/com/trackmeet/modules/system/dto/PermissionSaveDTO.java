package com.trackmeet.modules.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 权限新增 / 修改入参。
 *
 * @author TrackMeet
 */
@Data
public class PermissionSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，新增留空，修改时由路径变量指定 */
    private Long id;

    /** 权限编码，唯一，如 schedule:auto */
    @NotBlank(message = "权限编码不能为空")
    @Size(max = 64, message = "权限编码长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_:.-]{0,63}$",
            message = "权限编码需以字母开头，仅允许字母、数字、下划线、冒号、点、短横线，长度不超过 64")
    private String permCode;

    /** 权限名称 */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过 100")
    private String permName;

    /** 权限类型：MENU 菜单 / BUTTON 按钮 / API 接口 */
    @NotBlank(message = "权限类型不能为空")
    @Pattern(regexp = "^(MENU|BUTTON|API)$", message = "权限类型只能为 MENU、BUTTON 或 API")
    private String permType;

    /** 所属菜单 sys_menu.id，API 可空 */
    private Long menuId;

    /** 所属模块，用于日志归类 */
    @Size(max = 64, message = "所属模块长度不能超过 64")
    private String module;

    /** 接口路径（Ant 风格），API 用 */
    @Size(max = 200, message = "接口路径长度不能超过 200")
    private String apiPath;

    /** 请求方法：GET/POST/PUT/DELETE，API 用 */
    @Pattern(regexp = "^$|^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)$", message = "请求方法不合法")
    private String httpMethod;

    /** 接口分组，如 REGISTRATION */
    @Size(max = 64, message = "接口分组长度不能超过 64")
    private String apiGroup;

    /** 状态：ENABLED 启用 / DISABLED 停用，留空默认 ENABLED */
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    /** 列表显示顺序 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
