package com.trackmeet.security;

import com.trackmeet.common.util.SecurityUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 按钮/接口级权限校验器，供 {@code @PreAuthorize("@perm.has('schedule:auto')")} 使用。
 * 超级管理员（ROLE_SUPER_ADMIN）拥有全部权限。
 *
 * @author TrackMeet
 */
@Component("perm")
public class PermissionChecker {

    public boolean has(String permission) {
        if (!StringUtils.hasText(permission)) {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            return false;
        }
        if (loginUser.isSuperAdmin()) {
            return true;
        }
        return loginUser.getPermissions() != null && loginUser.getPermissions().contains(permission);
    }

    public boolean hasAny(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }
        for (String permission : permissions) {
            if (has(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAll(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }
        for (String permission : permissions) {
            if (!has(permission)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasRole(String roleCode) {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        return loginUser != null && loginUser.getRoles() != null && loginUser.getRoles().contains(roleCode);
    }
}
