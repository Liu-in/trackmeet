package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 角色权限关联表 sys_role_permission。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色 sys_role.id */
    @TableField("role_id")
    private Long roleId;

    /** 权限 sys_permission.id */
    @TableField("permission_id")
    private Long permissionId;

    /** 冗余菜单 sys_menu.id */
    @TableField("menu_id")
    private Long menuId;

    /** 授权类型：MENU / BUTTON / API */
    @TableField("perm_type")
    private String permType;

    /** 授权来源：INIT / MANUAL / COPY */
    @TableField("grant_source")
    private String grantSource;

    /** 授权人 sys_user.id */
    @TableField("grant_by")
    private Long grantBy;

    /** 授权时间 */
    @TableField("grant_time")
    private LocalDateTime grantTime;

    /** 1 生效，0 暂停授权 */
    @TableField("is_effective")
    private Integer isEffective;

    /** 授权生效开始时间 */
    @TableField("effective_start_time")
    private LocalDateTime effectiveStartTime;

    /** 授权生效结束时间 */
    @TableField("effective_end_time")
    private LocalDateTime effectiveEndTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
