package com.trackmeet.common.util;

import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具。
 *
 * @author TrackMeet
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户，未登录返回 null。
     */
    public static LoginUser getLoginUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }

    /**
     * 获取当前登录用户，未登录抛 401。
     */
    public static LoginUser getLoginUser() {
        LoginUser loginUser = getLoginUserOrNull();
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long getUserId() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? null : loginUser.getId();
    }

    public static String getUsername() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser == null ? null : loginUser.getUsername();
    }

    /**
     * 是否超级管理员（拥有 ROLE_SUPER_ADMIN）。
     */
    public static boolean isSuperAdmin() {
        LoginUser loginUser = getLoginUserOrNull();
        return loginUser != null && loginUser.getRoles() != null && loginUser.getRoles().contains("SUPER_ADMIN");
    }
}
