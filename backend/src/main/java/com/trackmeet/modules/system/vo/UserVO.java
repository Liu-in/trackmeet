package com.trackmeet.modules.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户视图对象：不含密码等敏感字段，附带角色 ID 与角色名称。
 *
 * @author TrackMeet
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 登录名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像文件 sys_file.id */
    private Long avatarFileId;

    /** 所属部门 sys_dept.id */
    private Long deptId;

    /** 关联参赛单位 unit.id */
    private Long unitId;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 1 内置账号，禁止删除 */
    private Integer isBuiltin;

    /** 密码最后修改时间 */
    private LocalDateTime pwdUpdateTime;

    /** 1 首次登录强制改密 */
    private Integer forcePwdChange;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP */
    private String lastLoginIp;

    /** 列表显示顺序 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;

    /** 已分配角色 ID 集合 */
    private List<Long> roleIds = new ArrayList<>();

    /** 已分配角色名称集合 */
    private List<String> roleNames = new ArrayList<>();
}
