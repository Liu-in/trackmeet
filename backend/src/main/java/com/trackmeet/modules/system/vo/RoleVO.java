package com.trackmeet.modules.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色视图对象：详情附带已授权权限 ID 集合。
 *
 * @author TrackMeet
 */
@Data
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色 ID */
    private Long id;

    /** 角色编码，如 SCHEDULER */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 数据范围：ALL / MEET / UNIT / SELF / REF */
    private String dataScope;

    /** REF 时的部门/单位/届次 ID 集合（JSON 文本） */
    private String dataScopeJson;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 1 内置角色，禁止删除 */
    private Integer isBuiltin;

    /** 列表显示顺序 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;

    /** 已授权权限 ID 集合，列表查询时为空集合 */
    private List<Long> permissionIds = new ArrayList<>();
}
