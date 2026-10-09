package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单表 sys_menu：目录(D) / 菜单(M) / 按钮(B) 三级树。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 父节点，0 为根 */
    @TableField("parent_id")
    private Long parentId;

    /** 菜单/按钮名称 */
    @TableField("menu_name")
    private String menuName;

    /** 菜单类型：D 目录 / M 菜单 / B 按钮 */
    @TableField("menu_type")
    private String menuType;

    /** 路由路径 */
    @TableField("path")
    private String path;

    /** 前端组件路径 */
    @TableField("component")
    private String component;

    /** 图标标识 */
    @TableField("icon")
    private String icon;

    /** 权限编码，与 sys_permission.perm_code 一致 */
    @TableField("perm_code")
    private String permCode;

    /** 1 导航可见，0 隐藏 */
    @TableField("visible")
    private Integer visible;

    /** 1 页面缓存 */
    @TableField("keep_alive")
    private Integer keepAlive;

    /** 1 外链，0 内部路由 */
    @TableField("is_external")
    private Integer isExternal;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 同级排序号 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
