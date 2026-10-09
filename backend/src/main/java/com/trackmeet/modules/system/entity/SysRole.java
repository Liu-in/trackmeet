package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色表 sys_role。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 角色编码，唯一，如 SCHEDULER */
    @TableField("role_code")
    private String roleCode;

    /** 角色名称 */
    @TableField("role_name")
    private String roleName;

    /** 数据范围：ALL / MEET / UNIT / SELF / REF */
    @TableField("data_scope")
    private String dataScope;

    /** REF 时的部门/单位/届次 ID 集合（JSON 文本） */
    @TableField("data_scope_json")
    private String dataScopeJson;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 1 内置角色，禁止删除 */
    @TableField("is_builtin")
    private Integer isBuiltin;

    /** 列表显示顺序 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
