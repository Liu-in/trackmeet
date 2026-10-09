package com.trackmeet.modules.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单节点（用于前端动态路由）。
 *
 * @author TrackMeet
 */
@Data
public class MenuNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String icon;
    private String permCode;
    private Integer visible;
    private Integer keepAlive;
    private Integer sortNo;
    private List<MenuNodeVO> children = new ArrayList<>();
}
