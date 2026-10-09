package com.trackmeet.modules.auth.service;

import com.trackmeet.modules.auth.dto.CaptchaVO;
import com.trackmeet.modules.auth.dto.ChangePasswordDTO;
import com.trackmeet.modules.auth.dto.LoginDTO;
import com.trackmeet.modules.auth.dto.LoginVO;
import com.trackmeet.modules.auth.dto.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 认证服务。
 *
 * @author TrackMeet
 */
public interface AuthService {

    /**
     * 登录：校验账号状态、锁定、验证码、口令，签发令牌并写登录日志。
     */
    LoginVO login(LoginDTO loginDTO, HttpServletRequest request);

    /**
     * 刷新令牌。
     */
    LoginVO refresh(String refreshToken);

    /**
     * 登出：令牌加入黑名单并写日志。
     *
     * @param authorizationHeader 请求头中的原始令牌
     */
    void logout(String authorizationHeader, HttpServletRequest request);

    /**
     * 当前登录用户信息（含角色、权限、菜单树）。
     */
    UserInfoVO currentUserInfo();

    /**
     * 修改本人密码（旧令牌立即失效）。
     */
    void changePassword(ChangePasswordDTO changePasswordDTO);

    /**
     * 生成图形验证码。
     */
    CaptchaVO captcha();
}
