package com.trackmeet.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.system.dto.RoleSaveDTO;
import com.trackmeet.modules.system.dto.SysRoleQuery;
import com.trackmeet.modules.system.entity.SysRole;
import com.trackmeet.modules.system.vo.RoleVO;

import java.util.List;

/**
 * 角色管理服务。
 *
 * @author TrackMeet
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 角色分页列表。
     */
    PageResult<RoleVO> pageRoles(SysRoleQuery query);

    /**
     * 角色详情（含 permissionIds）。
     */
    RoleVO getRoleDetail(Long id);

    /**
     * 新增角色，可同时授予权限，返回新角色 ID。
     */
    Long createRole(RoleSaveDTO dto);

    /**
     * 修改角色，可同时重建权限授权。
     */
    void updateRole(Long id, RoleSaveDTO dto);

    /**
     * 删除角色，内置角色禁止删除，同时清理用户角色与角色权限关联。
     */
    void deleteRole(Long id);

    /**
     * 角色授权：先按 role_id 物理删除再批量插入，冗余写入 perm_type / menu_id。
     */
    void grantPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 查询角色已授权的权限 ID 列表。
     */
    List<Long> listPermissionIds(Long roleId);
}
