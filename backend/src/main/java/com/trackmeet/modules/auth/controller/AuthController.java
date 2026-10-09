package com.trackmeet.modules.auth.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.auth.dto.CaptchaVO;
import com.trackmeet.modules.auth.dto.ChangePasswordDTO;
import com.trackmeet.modules.auth.dto.LoginDTO;
import com.trackmeet.modules.auth.dto.LoginVO;
import com.trackmeet.modules.auth.dto.UserInfoVO;
import com.trackmeet.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    /**
     * 登录。
     */
    @PostMapping("/login")
    @OpLog(module = "AUTH", action = "LOGIN", actionName = "用户登录", targetType = "user", recordParams = false)
    public R<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        return R.ok(authService.login(loginDTO, request));
    }

    /**
     * 图形验证码（开关由 trackmeet.captcha.enabled 控制）。
     */
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(authService.captcha());
    }

    /**
     * 刷新令牌。
     */
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@RequestParam("refreshToken") String refreshToken) {
        return R.ok(authService.refresh(refreshToken));
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    @OpLog(module = "AUTH", action = "LOGOUT", actionName = "用户登出", targetType = "user", recordParams = false)
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                          HttpServletRequest request) {
        authService.logout(authorization, request);
        return R.ok();
    }

    /**
     * 当前登录用户信息（含角色、权限、菜单树）。
     */
    @GetMapping("/profile")
    public R<UserInfoVO> profile() {
        return R.ok(authService.currentUserInfo());
    }

    /**
     * 修改本人密码。
     */
    @PostMapping("/change-password")
    @OpLog(module = "AUTH", action = "CHANGE_PWD", actionName = "修改密码", targetType = "user", recordParams = false)
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        authService.changePassword(changePasswordDTO);
        return R.ok();
    }
}
