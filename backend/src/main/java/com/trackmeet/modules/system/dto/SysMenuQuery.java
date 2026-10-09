package com.trackmeet.modules.system.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单查询条件：用于菜单树的可选过滤（关键字匹配 menu_name）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysMenuQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 菜单类型：D 目录 / M 菜单 / B 按钮 */
    private String menuType;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
