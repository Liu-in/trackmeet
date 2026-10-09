package com.trackmeet.modules.system.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.system.dto.ResetPasswordDTO;
import com.trackmeet.modules.system.dto.SysUserQuery;
import com.trackmeet.modules.system.dto.UserRoleAuthDTO;
import com.trackmeet.modules.system.dto.UserSaveDTO;
import com.trackmeet.modules.system.dto.UserStatusDTO;
import com.trackmeet.modules.system.service.SysUserService;
import com.trackmeet.modules.system.vo.UserVO;
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

/**
 * 用户管理接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
@Validated
public class SysUserController {

    private final SysUserService sysUserService;

    /**
     * 用户分页列表。
     */
    @GetMapping
    @PreAuthorize("@perm.has('system:user:list')")
    public R<PageResult<UserVO>> page(SysUserQuery query) {
        return R.ok(sysUserService.pageUsers(query));
    }

    /**
     * 用户详情（含 roleIds）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('system:user:query')")
    public R<UserVO> detail(@PathVariable("id") Long id) {
        return R.ok(sysUserService.getUserDetail(id));
    }

    /**
     * 新增用户（密码 BCrypt 加密，可同时分配角色）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('system:user:add')")
    @OpLog(module = "SYSTEM", action = "ADD", actionName = "新增用户", targetType = "user")
    public R<Long> add(@Valid @RequestBody UserSaveDTO dto) {
        return R.ok(sysUserService.createUser(dto));
    }

    /**
     * 修改用户（不修改密码；传 roleIds 时重建角色关联）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('system:user:edit')")
    @OpLog(module = "SYSTEM", action = "EDIT", actionName = "修改用户", targetType = "user")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody UserSaveDTO dto) {
        sysUserService.updateUser(id, dto);
        return R.ok();
    }

    /**
     * 逻辑删除用户，内置账号禁止删除。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('system:user:delete')")
    @OpLog(module = "SYSTEM", action = "DELETE", actionName = "删除用户", targetType = "user")
    public R<Void> delete(@PathVariable("id") Long id) {
        sysUserService.deleteUser(id);
        return R.ok();
    }

    /**
     * 启用 / 停用用户：{"status":"ENABLED"}。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('system:user:edit')")
    @OpLog(module = "SYSTEM", action = "EDIT", actionName = "修改用户状态", targetType = "user")
    public R<Void> updateStatus(@PathVariable("id") Long id, @Valid @RequestBody UserStatusDTO dto) {
        sysUserService.updateStatus(id, dto.getStatus());
        return R.ok();
    }

    /**
     * 重置密码：{"password":"..."}，重置后强制改密并失效旧令牌。
     */
    @PutMapping("/{id}/password")
    @PreAuthorize("@perm.has('system:user:resetPwd')")
    @OpLog(module = "SYSTEM", action = "RESET_PWD", actionName = "重置用户密码",
            targetType = "user", recordParams = false)
    public R<Void> resetPassword(@PathVariable("id") Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        sysUserService.resetPassword(id, dto.getPassword());
        return R.ok();
    }

    /**
     * 分配角色：{"roleIds":[1,2]}。
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("@perm.has('system:user:assignRole')")
    @OpLog(module = "SYSTEM", action = "AUTH", actionName = "分配用户角色", targetType = "user")
    public R<Void> assignRoles(@PathVariable("id") Long id, @Valid @RequestBody UserRoleAuthDTO dto) {
        sysUserService.assignRoles(id, dto.getRoleIds());
        return R.ok();
    }
}
