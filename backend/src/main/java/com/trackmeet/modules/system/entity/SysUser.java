package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户表 sys_user。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录名，全局唯一 */
    @TableField("username")
    private String username;

    /** BCrypt 密文密码 */
    @JsonIgnore
    @TableField("password")
    private String password;

    /** 真实姓名 */
    @TableField("real_name")
    private String realName;

    /** 手机号 */
    @TableField("phone")
    private String phone;

    /** 邮箱 */
    @TableField("email")
    private String email;

    /** 头像文件 sys_file.id */
    @TableField("avatar_file_id")
    private Long avatarFileId;

    /** 所属部门 sys_dept.id */
    @TableField("dept_id")
    private Long deptId;

    /** 关联参赛单位 unit.id */
    @TableField("unit_id")
    private Long unitId;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 1 内置账号，禁止删除 */
    @TableField("is_builtin")
    private Integer isBuiltin;

    /** 密码最后修改时间 */
    @TableField("pwd_update_time")
    private LocalDateTime pwdUpdateTime;

    /** 连续输错次数 */
    @TableField("pwd_error_count")
    private Integer pwdErrorCount;

    /** 锁定到期时间 */
    @TableField("lock_until")
    private LocalDateTime lockUntil;

    /** 1 首次登录强制改密 */
    @TableField("force_pwd_change")
    private Integer forcePwdChange;

    /** 令牌版本，自增即失效旧令牌 */
    @TableField("token_version")
    private Integer tokenVersion;

    /** 最后登录时间 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    @TableField("last_login_ip")
    private String lastLoginIp;

    /** 列表显示顺序 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
