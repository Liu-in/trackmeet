package com.trackmeet.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项，对应 application.yml 中 trackmeet.jwt.*。
 *
 * @author TrackMeet
 */
@Data
@Component
@ConfigurationProperties(prefix = "trackmeet.jwt")
public class JwtProperties {

    /** 签名密钥（长度必须 >= 32 字节） */
    private String secret;

    /** 令牌所在请求头 */
    private String header = "Authorization";

    /** 令牌前缀 */
    private String prefix = "Bearer ";

    /** 访问令牌有效期（分钟） */
    private long accessTokenExpireMinutes = 120L;

    /** 刷新令牌有效期（分钟） */
    private long refreshTokenExpireMinutes = 10080L;

    /** 签发者 */
    private String issuer = "trackmeet";
}
