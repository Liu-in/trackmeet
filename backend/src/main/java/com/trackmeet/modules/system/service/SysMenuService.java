package com.trackmeet.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.system.dto.MenuSaveDTO;
import com.trackmeet.modules.system.dto.SysMenuQuery;
import com.trackmeet.modules.system.entity.SysMenu;
import com.trackmeet.modules.system.vo.MenuTreeVO;

import java.util.List;

/**
 * 菜单管理服务。
 *
 * @author TrackMeet
 */
public interface SysMenuService extends IService<SysMenu> {

    /**
     * 菜单树：默认取全部启用菜单，可按关键字 / 菜单类型 / 状态过滤。
     */
    List<MenuTreeVO> menuTree(SysMenuQuery query);

    /**
     * 菜单详情。
     */
    SysMenu getMenuDetail(Long id);

    /**
     * 新增菜单，返回新菜单 ID。
     */
    Long createMenu(MenuSaveDTO dto);

    /**
     * 修改菜单（含父子环校验）。
     */
    void updateMenu(Long id, MenuSaveDTO dto);

    /**
     * 删除菜单，存在子节点时禁止删除。
     */
    void deleteMenu(Long id);
}
