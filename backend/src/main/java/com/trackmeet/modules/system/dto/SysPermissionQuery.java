package com.trackmeet.modules.system.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限分页查询条件：关键字匹配 perm_code / perm_name。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysPermissionQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 权限类型：MENU / BUTTON / API */
    private String permType;

    /** 所属模块，如 SYSTEM */
    private String module;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
