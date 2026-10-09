package com.trackmeet.modules.system.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询条件：关键字匹配 role_code / role_name。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysRoleQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;
}
