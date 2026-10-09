package com.trackmeet.modules.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点：菜单节点（type=MENU）下挂按钮（BUTTON）与接口（API）权限。
 *
 * @author TrackMeet
 */
@Data
public class PermissionTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点 ID：菜单节点为 sys_menu.id，权限节点为 sys_permission.id */
    private Long id;

    /** 父节点 ID，0 为根 */
    private Long parentId;

    /** 节点显示名：菜单为 menu_name，权限为 perm_name */
    private String label;

    /** 节点类型：MENU 菜单 / BUTTON 按钮 / API 接口 */
    private String type;

    /** 权限编码，菜单节点取 sys_menu.perm_code */
    private String permCode;

    /** 子节点 */
    private List<PermissionTreeVO> children = new ArrayList<>();
}
