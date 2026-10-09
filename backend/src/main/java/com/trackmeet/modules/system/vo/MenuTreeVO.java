package com.trackmeet.modules.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点。
 *
 * @author TrackMeet
 */
@Data
public class MenuTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 菜单 ID */
    private Long id;

    /** 父节点 ID，0 为根 */
    private Long parentId;

    /** 菜单/按钮名称 */
    private String menuName;

    /** 菜单类型：D 目录 / M 菜单 / B 按钮 */
    private String menuType;

    /** 路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 图标标识 */
    private String icon;

    /** 权限编码 */
    private String permCode;

    /** 1 导航可见，0 隐藏 */
    private Integer visible;

    /** 同级排序号 */
    private Integer sortNo;

    /** 子节点 */
    private List<MenuTreeVO> children = new ArrayList<>();
}
