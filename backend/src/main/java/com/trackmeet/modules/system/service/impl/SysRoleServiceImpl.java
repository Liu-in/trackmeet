package com.trackmeet.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.system.dto.RoleSaveDTO;
import com.trackmeet.modules.system.dto.SysRoleQuery;
import com.trackmeet.modules.system.entity.SysPermission;
import com.trackmeet.modules.system.entity.SysRole;
import com.trackmeet.modules.system.entity.SysRolePermission;
import com.trackmeet.modules.system.entity.SysUser;
import com.trackmeet.modules.system.mapper.SysPermissionMapper;
import com.trackmeet.modules.system.mapper.SysRoleMapper;
import com.trackmeet.modules.system.mapper.SysRolePermissionMapper;
import com.trackmeet.modules.system.mapper.SysUserMapper;
import com.trackmeet.modules.system.mapper.SysUserRoleMapper;
import com.trackmeet.modules.system.service.SysRoleService;
import com.trackmeet.modules.system.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现。
 * <p>角色授权采用「物理删除 + 批量插入」重建；授权变化后自增受影响用户的 token_version 失效旧令牌。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String DATA_SCOPE_ALL = "ALL";
    private static final String GRANT_SOURCE_MANUAL = "MANUAL";

    /** role_code 列长度，删除时改写唯一键需截断 */
    private static final int ROLE_CODE_MAX_LENGTH = 64;

    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<RoleVO> pageRoles(SysRoleQuery query) {
        SysRoleQuery condition = query == null ? new SysRoleQuery() : query;
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        String keyword = condition.getKeyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword)
                    .or().like(SysRole::getRoleName, keyword));
        }
        wrapper.eq(StringUtils.hasText(condition.getStatus()), SysRole::getStatus, condition.getStatus())
                .orderByAsc(SysRole::getSortNo)
                .orderByAsc(SysRole::getId);

        Page<SysRole> pageParam = condition.toPage();
        Page<SysRole> entityPage = page(pageParam, wrapper);

        List<RoleVO> records = entityPage.getRecords().stream()
                .map(this::toRoleVO)
                .collect(Collectors.toList());
        Page<RoleVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    @Override
    public RoleVO getRoleDetail(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        RoleVO vo = toRoleVO(role);
        vo.setPermissionIds(listPermissionIds(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleSaveDTO dto) {
        String roleCode = dto.getRoleCode().trim();
        if (existsRoleCode(roleCode, null)) {
            throw new BizException(ResultCode.CONFLICT, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(dto.getRoleName());
        role.setDataScope(StringUtils.hasText(dto.getDataScope()) ? dto.getDataScope() : DATA_SCOPE_ALL);
        role.setDataScopeJson(dto.getDataScopeJson());
        role.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_ENABLED);
        role.setIsBuiltin(0);
        role.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        role.setRemark(dto.getRemark());
        save(role);
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            rebuildRolePermissions(role.getId(), dto.getPermissionIds());
        }
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleSaveDTO dto) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        String roleCode = dto.getRoleCode().trim();
        if (!roleCode.equals(role.getRoleCode()) && existsRoleCode(roleCode, id)) {
            throw new BizException(ResultCode.CONFLICT, "角色编码已存在");
        }
        role.setRoleCode(roleCode);
        role.setRoleName(dto.getRoleName());
        if (StringUtils.hasText(dto.getDataScope())) {
            role.setDataScope(dto.getDataScope());
        }
        role.setDataScopeJson(dto.getDataScopeJson());
        if (StringUtils.hasText(dto.getStatus())) {
            role.setStatus(dto.getStatus());
        }
        if (dto.getSortNo() != null) {
            role.setSortNo(dto.getSortNo());
        }
        role.setRemark(dto.getRemark());
        updateById(role);
        if (dto.getPermissionIds() != null) {
            rebuildRolePermissions(id, dto.getPermissionIds());
            invalidateRoleUsers(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        if (role.getIsBuiltin() != null && role.getIsBuiltin() == 1) {
            throw new BizException(ResultCode.FORBIDDEN, "内置角色禁止删除");
        }
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(id);
        userRoleMapper.deleteByRoleId(id);
        rolePermissionMapper.deleteByRoleId(id);
        baseMapper.update(null, new LambdaUpdateWrapper<SysRole>()
                .eq(SysRole::getId, id)
                .set(SysRole::getRoleCode, releaseUniqueKey(role.getRoleCode(), id, ROLE_CODE_MAX_LENGTH)));
        removeById(id);
        invalidateUsers(userIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantPermissions(Long roleId, List<Long> permissionIds) {
        SysRole role = getById(roleId);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        rebuildRolePermissions(roleId, permissionIds == null ? new ArrayList<>() : permissionIds);
        invalidateRoleUsers(roleId);
    }

    @Override
    public List<Long> listPermissionIds(Long roleId) {
        List<Long> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
        if (permissionIds == null) {
            return new ArrayList<>();
        }
        return permissionIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    /**
     * 重建角色授权：先按 role_id 物理删除，再按权限行冗余写入 perm_type / menu_id 批量插入。
     */
    private void rebuildRolePermissions(Long roleId, List<Long> permissionIds) {
        List<Long> distinctIds = permissionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysPermission> permissionMap = new HashMap<>();
        if (!distinctIds.isEmpty()) {
            List<SysPermission> permissions = permissionMapper.selectList(
                    new LambdaQueryWrapper<SysPermission>().in(SysPermission::getId, distinctIds));
            for (SysPermission permission : permissions) {
                permissionMap.put(permission.getId(), permission);
            }
            if (permissionMap.size() != distinctIds.size()) {
                throw new BizException(ResultCode.PARAM_ERROR, "存在无效的权限 ID");
            }
        }
        rolePermissionMapper.deleteByRoleId(roleId);
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = SecurityUtils.getUserId();
        for (Long permissionId : distinctIds) {
            SysPermission permission = permissionMap.get(permissionId);
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setMenuId(permission.getMenuId());
            relation.setPermType(permission.getPermType());
            relation.setGrantSource(GRANT_SOURCE_MANUAL);
            relation.setGrantBy(operatorId);
            relation.setGrantTime(now);
            relation.setIsEffective(1);
            rolePermissionMapper.insert(relation);
        }
    }

    /**
     * 失效该角色下全部用户的旧令牌（授权变化后权限缓存必须重建）。
     */
    private void invalidateRoleUsers(Long roleId) {
        invalidateUsers(userRoleMapper.selectUserIdsByRoleId(roleId));
    }

    private void invalidateUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> distinctUserIds = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctUserIds.isEmpty()) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .in(SysUser::getId, distinctUserIds)
                .setSql("token_version = token_version + 1")
                .set(SysUser::getUpdateTime, LocalDateTime.now()));
    }

    private boolean existsRoleCode(String roleCode, Long excludeId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeId != null, SysRole::getId, excludeId);
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

    private RoleVO toRoleVO(SysRole role) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }
}
