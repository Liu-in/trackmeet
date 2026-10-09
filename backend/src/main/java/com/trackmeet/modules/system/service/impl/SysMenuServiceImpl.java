package com.trackmeet.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.system.dto.MenuSaveDTO;
import com.trackmeet.modules.system.dto.SysMenuQuery;
import com.trackmeet.modules.system.entity.SysMenu;
import com.trackmeet.modules.system.mapper.SysMenuMapper;
import com.trackmeet.modules.system.service.SysMenuService;
import com.trackmeet.modules.system.vo.MenuTreeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单管理服务实现。
 *
 * @author TrackMeet
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final Long ROOT_PARENT_ID = 0L;

    /** 父子环校验的最大回溯层级 */
    private static final int MAX_TREE_DEPTH = 50;

    @Override
    public List<MenuTreeVO> menuTree(SysMenuQuery query) {
        boolean hasFilter = query != null
                && (StringUtils.hasText(query.getKeyword())
                || StringUtils.hasText(query.getMenuType())
                || StringUtils.hasText(query.getStatus()));
        List<SysMenu> menus;
        if (!hasFilter) {
            menus = baseMapper.selectAllEnabled();
        } else {
            menus = list(new LambdaQueryWrapper<SysMenu>()
                    .eq(StringUtils.hasText(query.getStatus()), SysMenu::getStatus, query.getStatus())
                    .eq(StringUtils.hasText(query.getMenuType()), SysMenu::getMenuType, query.getMenuType())
                    .like(StringUtils.hasText(query.getKeyword()), SysMenu::getMenuName, query.getKeyword())
                    .orderByAsc(SysMenu::getSortNo)
                    .orderByAsc(SysMenu::getId));
        }
        return assembleMenuTree(menus);
    }

    @Override
    public SysMenu getMenuDetail(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMenu(MenuSaveDTO dto) {
        Long parentId = dto.getParentId() == null ? ROOT_PARENT_ID : dto.getParentId();
        validateParent(null, parentId);
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setMenuName(dto.getMenuName());
        menu.setMenuType(dto.getMenuType());
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setIcon(dto.getIcon());
        menu.setPermCode(dto.getPermCode());
        menu.setVisible(dto.getVisible() == null ? 1 : dto.getVisible());
        menu.setKeepAlive(dto.getKeepAlive() == null ? 0 : dto.getKeepAlive());
        menu.setIsExternal(dto.getIsExternal() == null ? 0 : dto.getIsExternal());
        menu.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_ENABLED);
        menu.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        menu.setRemark(dto.getRemark());
        save(menu);
        return menu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMenu(Long id, MenuSaveDTO dto) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        Long parentId = dto.getParentId() == null ? ROOT_PARENT_ID : dto.getParentId();
        validateParent(id, parentId);
        menu.setParentId(parentId);
        menu.setMenuName(dto.getMenuName());
        menu.setMenuType(dto.getMenuType());
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setIcon(dto.getIcon());
        menu.setPermCode(dto.getPermCode());
        if (dto.getVisible() != null) {
            menu.setVisible(dto.getVisible());
        }
        if (dto.getKeepAlive() != null) {
            menu.setKeepAlive(dto.getKeepAlive());
        }
        if (dto.getIsExternal() != null) {
            menu.setIsExternal(dto.getIsExternal());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            menu.setStatus(dto.getStatus());
        }
        if (dto.getSortNo() != null) {
            menu.setSortNo(dto.getSortNo());
        }
        menu.setRemark(dto.getRemark());
        updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BizException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        long childCount = count(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new BizException(ResultCode.CONFLICT, "存在子菜单，无法删除");
        }
        removeById(id);
    }

    /**
     * 组装菜单树：先建 Map 再一次性挂载父子关系，parent_id 为 0 或不在集合内时作为根节点。
     */
    private List<MenuTreeVO> assembleMenuTree(List<SysMenu> menus) {
        List<MenuTreeVO> roots = new ArrayList<>();
        Map<Long, MenuTreeVO> nodeMap = new HashMap<>();
        for (SysMenu menu : menus) {
            MenuTreeVO node = new MenuTreeVO();
            node.setId(menu.getId());
            node.setParentId(menu.getParentId() == null ? ROOT_PARENT_ID : menu.getParentId());
            node.setMenuName(menu.getMenuName());
            node.setMenuType(menu.getMenuType());
            node.setPath(menu.getPath());
            node.setComponent(menu.getComponent());
            node.setIcon(menu.getIcon());
            node.setPermCode(menu.getPermCode());
            node.setVisible(menu.getVisible());
            node.setSortNo(menu.getSortNo());
            node.setChildren(new ArrayList<>());
            nodeMap.put(menu.getId(), node);
        }
        for (SysMenu menu : menus) {
            MenuTreeVO node = nodeMap.get(menu.getId());
            MenuTreeVO parent = menu.getParentId() == null ? null : nodeMap.get(menu.getParentId());
            if (parent == null || parent.getId().equals(node.getId())) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 校验父节点：不允许为自身或自身子孙，避免形成环。
     */
    private void validateParent(Long id, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(id)) {
            throw new BizException(ResultCode.PARAM_ERROR, "父菜单不能为自身");
        }
        Long cursor = parentId;
        int depth = 0;
        while (cursor != null && cursor != 0L && depth++ < MAX_TREE_DEPTH) {
            if (cursor.equals(id)) {
                throw new BizException(ResultCode.PARAM_ERROR, "父菜单不能为自身的子菜单");
            }
            SysMenu parent = getById(cursor);
            if (parent == null) {
                if (depth == 1) {
                    throw new BizException(ResultCode.PARAM_ERROR, "父菜单不存在");
                }
                return;
            }
            cursor = parent.getParentId();
        }
    }
}
