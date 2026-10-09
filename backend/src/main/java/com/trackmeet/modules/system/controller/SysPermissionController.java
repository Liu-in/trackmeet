package com.trackmeet.modules.system.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.system.dto.PermissionSaveDTO;
import com.trackmeet.modules.system.dto.SysPermissionQuery;
import com.trackmeet.modules.system.entity.SysPermission;
import com.trackmeet.modules.system.service.SysPermissionService;
import com.trackmeet.modules.system.vo.PermissionTreeVO;
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
 * 权限管理接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
@Validated
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;

    /**
     * 权限分页列表（条件：keyword、permType、module）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('system:perm:list')")
    public R<PageResult<SysPermission>> page(SysPermissionQuery query) {
        return R.ok(sysPermissionService.pagePermissions(query));
    }

    /**
     * 权限树：启用菜单为骨架，权限点按 menu_id 挂载。
     */
    @GetMapping("/tree")
    @PreAuthorize("@perm.has('system:perm:list')")
    public R<List<PermissionTreeVO>> tree() {
        return R.ok(sysPermissionService.permissionTree());
    }

    /**
     * 新增权限（perm_code 唯一）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('system:perm:add')")
    @OpLog(module = "SYSTEM", action = "ADD", actionName = "新增权限", targetType = "permission")
    public R<Long> add(@Valid @RequestBody PermissionSaveDTO dto) {
        return R.ok(sysPermissionService.createPermission(dto));
    }

    /**
     * 修改权限。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('system:perm:edit')")
    @OpLog(module = "SYSTEM", action = "EDIT", actionName = "修改权限", targetType = "permission")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody PermissionSaveDTO dto) {
        sysPermissionService.updatePermission(id, dto);
        return R.ok();
    }

    /**
     * 删除权限，同时清理角色权限关联。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('system:perm:delete')")
    @OpLog(module = "SYSTEM", action = "DELETE", actionName = "删除权限", targetType = "permission")
    public R<Void> delete(@PathVariable("id") Long id) {
        sysPermissionService.deletePermission(id);
        return R.ok();
    }
}
