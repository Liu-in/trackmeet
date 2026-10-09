package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户角色关联表 sys_user_role。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户 sys_user.id */
    @TableField("user_id")
    private Long userId;

    /** 角色 sys_role.id */
    @TableField("role_id")
    private Long roleId;

    /** 授权来源：INIT / MANUAL / IMPORT */
    @TableField("grant_source")
    private String grantSource;

    /** 授权人 sys_user.id */
    @TableField("grant_by")
    private Long grantBy;

    /** 授权时间 */
    @TableField("grant_time")
    private LocalDateTime grantTime;

    /** 1 主角色，用于默认数据范围 */
    @TableField("is_primary")
    private Integer isPrimary;

    /** 1 生效，0 停用 */
    @TableField("is_effective")
    private Integer isEffective;

    /** 生效开始时间 */
    @TableField("effective_start_time")
    private LocalDateTime effectiveStartTime;

    /** 生效结束时间 */
    @TableField("effective_end_time")
    private LocalDateTime effectiveEndTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
