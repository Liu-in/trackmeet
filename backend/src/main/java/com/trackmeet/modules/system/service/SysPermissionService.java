package com.trackmeet.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.system.dto.PermissionSaveDTO;
import com.trackmeet.modules.system.dto.SysPermissionQuery;
import com.trackmeet.modules.system.entity.SysPermission;
import com.trackmeet.modules.system.vo.PermissionTreeVO;

import java.util.List;

/**
 * 权限管理服务。
 *
 * @author TrackMeet
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 权限分页列表（条件：keyword、permType、module、status）。
     */
    PageResult<SysPermission> pagePermissions(SysPermissionQuery query);

    /**
     * 权限树：启用菜单组装为树，权限点按 menu_id 挂载到对应菜单节点。
     */
    List<PermissionTreeVO> permissionTree();

    /**
     * 新增权限，返回新权限 ID。
     */
    Long createPermission(PermissionSaveDTO dto);

    /**
     * 修改权限。
     */
    void updatePermission(Long id, PermissionSaveDTO dto);

    /**
     * 删除权限，内置权限禁止删除，同时清理角色权限关联。
     */
    void deletePermission(Long id);
}
