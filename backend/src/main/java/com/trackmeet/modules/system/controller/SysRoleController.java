package com.trackmeet.modules.system.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.system.dto.RoleAuthDTO;
import com.trackmeet.modules.system.dto.RoleSaveDTO;
import com.trackmeet.modules.system.dto.SysRoleQuery;
import com.trackmeet.modules.system.service.SysRoleService;
import com.trackmeet.modules.system.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@Validated
public class SysRoleController {

    private final SysRoleService sysRoleService;

    /**
     * 角色分页列表。
     */
    @GetMapping
    @PreAuthorize("@perm.has('system:role:list')")
    public R<PageResult<RoleVO>> page(SysRoleQuery query) {
        return R.ok(sysRoleService.pageRoles(query));
    }

    /**
     * 角色详情（含 permissionIds）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('system:role:query')")
    public R<RoleVO> detail(@PathVariable("id") Long id) {
        return R.ok(sysRoleService.getRoleDetail(id));
    }

    /**
     * 新增角色（role_code 唯一，可同时授予权限）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('system:role:add')")
    @OpLog(module = "SYSTEM", action = "ADD", actionName = "新增角色", targetType = "role")
    public R<Long> add(@Valid @RequestBody RoleSaveDTO dto) {
        return R.ok(sysRoleService.createRole(dto));
    }

    /**
     * 修改角色。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('system:role:edit')")
    @OpLog(module = "SYSTEM", action = "EDIT", actionName = "修改角色", targetType = "role")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody RoleSaveDTO dto) {
        sysRoleService.updateRole(id, dto);
        return R.ok();
    }

    /**
     * 删除角色，同时清理用户角色与角色权限关联。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('system:role:delete')")
    @OpLog(module = "SYSTEM", action = "DELETE", actionName = "删除角色", targetType = "role")
    public R<Void> delete(@PathVariable("id") Long id) {
        sysRoleService.deleteRole(id);
        return R.ok();
    }

    /**
     * 角色授权：{"permissionIds":[...]}。
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("@perm.has('system:role:auth')")
    @OpLog(module = "SYSTEM", action = "AUTH", actionName = "角色授权", targetType = "role")
    public R<Void> grantPermissions(@PathVariable("id") Long id, @Valid @RequestBody RoleAuthDTO dto) {
        sysRoleService.grantPermissions(id, dto.getPermissionIds());
        return R.ok();
    }

    /**
     * 查询角色已有权限 ID 列表。
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("@perm.has('system:role:query')")
    public R<List<Long>> listPermissions(@PathVariable("id") Long id) {
        return R.ok(sysRoleService.listPermissionIds(id));
    }
}
