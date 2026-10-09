package com.trackmeet.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图形验证码返回体。
 *
 * @author TrackMeet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 验证码标识，登录时回传 */
    private String captchaKey;

    /** 图片内容（data:image/png;base64,...） */
    private String imageBase64;

    /** 有效期（秒） */
    private long expireSeconds;
}
