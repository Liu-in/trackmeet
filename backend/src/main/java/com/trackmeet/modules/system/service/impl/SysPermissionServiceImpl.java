package com.trackmeet.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.system.dto.PermissionSaveDTO;
import com.trackmeet.modules.system.dto.SysPermissionQuery;
import com.trackmeet.modules.system.entity.SysMenu;
import com.trackmeet.modules.system.entity.SysPermission;
import com.trackmeet.modules.system.mapper.SysMenuMapper;
import com.trackmeet.modules.system.mapper.SysPermissionMapper;
import com.trackmeet.modules.system.mapper.SysRolePermissionMapper;
import com.trackmeet.modules.system.service.SysPermissionService;
import com.trackmeet.modules.system.vo.PermissionTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限管理服务实现。
 * <p>权限树以启用菜单为骨架，权限点按冗余列 menu_id 挂载到对应菜单节点。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission>
        implements SysPermissionService {

    private static final String TYPE_MENU = "MENU";
    private static final String STATUS_ENABLED = "ENABLED";

    /** perm_code 列长度，删除时改写唯一键需截断 */
    private static final int PERM_CODE_MAX_LENGTH = 64;

    private final SysMenuMapper menuMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    @Override
    public PageResult<SysPermission> pagePermissions(SysPermissionQuery query) {
        SysPermissionQuery condition = query == null ? new SysPermissionQuery() : query;
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<>();
        String keyword = condition.getKeyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysPermission::getPermCode, keyword)
                    .or().like(SysPermission::getPermName, keyword));
        }
        wrapper.eq(StringUtils.hasText(condition.getPermType()), SysPermission::getPermType, condition.getPermType())
                .eq(StringUtils.hasText(condition.getModule()), SysPermission::getModule, condition.getModule())
                .eq(StringUtils.hasText(condition.getStatus()), SysPermission::getStatus, condition.getStatus())
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId);

        Page<SysPermission> pageParam = condition.toPage();
        return PageResult.of(page(pageParam, wrapper));
    }

    @Override
    public List<PermissionTreeVO> permissionTree() {
        List<SysMenu> menus = menuMapper.selectAllEnabled();
        List<SysPermission> permissions = list(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getStatus, STATUS_ENABLED)
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));
        return assemblePermissionTree(menus, permissions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPermission(PermissionSaveDTO dto) {
        String permCode = dto.getPermCode().trim();
        if (existsPermCode(permCode, null)) {
            throw new BizException(ResultCode.CONFLICT, "权限编码已存在");
        }
        validateMenu(dto.getMenuId());
        SysPermission permission = new SysPermission();
        permission.setPermCode(permCode);
        permission.setPermName(dto.getPermName());
        permission.setPermType(dto.getPermType());
        permission.setMenuId(dto.getMenuId());
        permission.setModule(dto.getModule());
        permission.setApiPath(dto.getApiPath());
        permission.setHttpMethod(dto.getHttpMethod());
        permission.setApiGroup(dto.getApiGroup());
        permission.setIsBuiltin(0);
        permission.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_ENABLED);
        permission.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        permission.setRemark(dto.getRemark());
        save(permission);
        return permission.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(Long id, PermissionSaveDTO dto) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new BizException(ResultCode.NOT_FOUND, "权限不存在");
        }
        String permCode = dto.getPermCode().trim();
        if (!permCode.equals(permission.getPermCode()) && existsPermCode(permCode, id)) {
            throw new BizException(ResultCode.CONFLICT, "权限编码已存在");
        }
        validateMenu(dto.getMenuId());
        permission.setPermCode(permCode);
        permission.setPermName(dto.getPermName());
        permission.setPermType(dto.getPermType());
        permission.setMenuId(dto.getMenuId());
        permission.setModule(dto.getModule());
        permission.setApiPath(dto.getApiPath());
        permission.setHttpMethod(dto.getHttpMethod());
        permission.setApiGroup(dto.getApiGroup());
        if (StringUtils.hasText(dto.getStatus())) {
            permission.setStatus(dto.getStatus());
        }
        if (dto.getSortNo() != null) {
            permission.setSortNo(dto.getSortNo());
        }
        permission.setRemark(dto.getRemark());
        updateById(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long id) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new BizException(ResultCode.NOT_FOUND, "权限不存在");
        }
        if (permission.getIsBuiltin() != null && permission.getIsBuiltin() == 1) {
            throw new BizException(ResultCode.FORBIDDEN, "内置权限禁止删除");
        }
        rolePermissionMapper.deleteByPermissionId(id);
        baseMapper.update(null, new LambdaUpdateWrapper<SysPermission>()
                .eq(SysPermission::getId, id)
                .set(SysPermission::getPermCode, releaseUniqueKey(permission.getPermCode(), id, PERM_CODE_MAX_LENGTH)));
        removeById(id);
    }

    /**
     * 组装权限树：菜单节点先入 Map，再一次遍历挂载父子关系，最后把权限点挂到所属菜单。
     */
    private List<PermissionTreeVO> assemblePermissionTree(List<SysMenu> menus, List<SysPermission> permissions) {
        List<PermissionTreeVO> roots = new ArrayList<>();
        Map<Long, PermissionTreeVO> nodeMap = new HashMap<>();
        for (SysMenu menu : menus) {
            PermissionTreeVO node = new PermissionTreeVO();
            node.setId(menu.getId());
            node.setParentId(menu.getParentId() == null ? 0L : menu.getParentId());
            node.setLabel(menu.getMenuName());
            node.setType(TYPE_MENU);
            node.setPermCode(menu.getPermCode());
            node.setChildren(new ArrayList<>());
            nodeMap.put(menu.getId(), node);
        }
        for (SysMenu menu : menus) {
            PermissionTreeVO node = nodeMap.get(menu.getId());
            PermissionTreeVO parent = menu.getParentId() == null ? null : nodeMap.get(menu.getParentId());
            if (parent == null || parent.getId().equals(node.getId())) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        for (SysPermission permission : permissions) {
            PermissionTreeVO node = new PermissionTreeVO();
            node.setId(permission.getId());
            Long menuId = permission.getMenuId();
            node.setParentId(menuId == null ? 0L : menuId);
            node.setLabel(permission.getPermName());
            node.setType(permission.getPermType());
            node.setPermCode(permission.getPermCode());
            node.setChildren(new ArrayList<>());
            PermissionTreeVO parent = menuId == null ? null : nodeMap.get(menuId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private void validateMenu(Long menuId) {
        if (menuId == null || menuId == 0L) {
            return;
        }
        if (menuMapper.selectById(menuId) == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属菜单不存在");
        }
    }

    private boolean existsPermCode(String permCode, Long excludeId) {
        LambdaQueryWrapper<SysPermission> wrapper = new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, permCode)
                .ne(excludeId != null, SysPermission::getId, excludeId);
        return count(wrapper) > 0;
    }

    /**
     * 逻辑删除时释放业务唯一键（docs/12-01 第 4 节）：追加 {@code _d{id}} 后缀。
     */
    private static String releaseUniqueKey(String original, Long id, int maxLength) {
        String suffix = "_d" + id;
        String base = original == null ? "" : original;
        int keep = Math.max(0, maxLength - suffix.length());
        if (base.length() > keep) {
            base = base.substring(0, keep);
        }
        return base + suffix;
    }
}
