package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限表 sys_permission：菜单权限 / 按钮权限 / 接口权限。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 权限编码，唯一，如 schedule:auto */
    @TableField("perm_code")
    private String permCode;

    /** 权限名称 */
    @TableField("perm_name")
    private String permName;

    /** 权限类型：MENU 菜单 / BUTTON 按钮 / API 接口 */
    @TableField("perm_type")
    private String permType;

    /** 所属菜单 sys_menu.id，API 可空 */
    @TableField("menu_id")
    private Long menuId;

    /** 所属模块，用于日志归类 */
    @TableField("module")
    private String module;

    /** 接口路径（Ant 风格），API 用 */
    @TableField("api_path")
    private String apiPath;

    /** 请求方法：GET/POST/PUT/DELETE，API 用 */
    @TableField("http_method")
    private String httpMethod;

    /** 接口分组，如 REGISTRATION */
    @TableField("api_group")
    private String apiGroup;

    /** 1 内置权限，禁止删除 */
    @TableField("is_builtin")
    private Integer isBuiltin;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 列表显示顺序 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
