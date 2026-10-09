package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门表 sys_dept。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 父部门，0 为根 */
    @TableField("parent_id")
    private Long parentId;

    /** 部门名称 */
    @TableField("dept_name")
    private String deptName;

    /** 部门编码，唯一 */
    @TableField("dept_code")
    private String deptCode;

    /** 类型：ORG 机构 / DEPT 部门 / TEAM 业务小组 */
    @TableField("dept_type")
    private String deptType;

    /** 祖先路径，如 /1/4/9/ */
    @TableField("dept_path")
    private String deptPath;

    /** 负责人姓名 */
    @TableField("leader")
    private String leader;

    /** 联系电话 */
    @TableField("phone")
    private String phone;

    /** 联系邮箱 */
    @TableField("email")
    private String email;

    /** 绑定参赛单位 unit.id */
    @TableField("unit_id")
    private Long unitId;

    /** 同级排序号 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 状态：ENABLED / DISABLED */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
