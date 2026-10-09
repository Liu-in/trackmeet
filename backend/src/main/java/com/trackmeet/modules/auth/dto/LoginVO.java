package com.trackmeet.modules.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录/刷新令牌返回体。
 *
 * @author TrackMeet
 */
@Data
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** 令牌类型 */
    private String tokenType = "Bearer";

    /** 访问令牌有效期（秒） */
    private long expiresIn;

    /** 用户信息 */
    private UserInfoVO userInfo;
}
