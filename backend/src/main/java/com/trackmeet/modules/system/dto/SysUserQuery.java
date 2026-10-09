package com.trackmeet.modules.system.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询条件：关键字匹配 username / real_name / phone。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 所属部门 sys_dept.id */
    private Long deptId;

    /** 关联参赛单位 unit.id */
    private Long unitId;
}
