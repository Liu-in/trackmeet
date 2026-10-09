package com.trackmeet.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 令牌工具单元测试（不依赖 Spring 容器与数据库）。
 *
 * @author TrackMeet
 */
class JwtTokenProviderTest {

    private static final String SECRET = "trackmeet-unit-test-secret-key-0123456789";
    private static final Long USER_ID = 1001L;
    private static final String USERNAME = "tester";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenExpireMinutes(120L);
        properties.setRefreshTokenExpireMinutes(10080L);
        properties.setIssuer("trackmeet-test");
        properties.setHeader("Authorization");
        properties.setPrefix("Bearer ");
        jwtTokenProvider = new JwtTokenProvider(properties);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("访问令牌：可解析用户 ID、登录名与令牌版本，且校验通过")
    void shouldCreateAndParseAccessToken() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, USERNAME, 3);

        assertThat(jwtTokenProvider.validate(token)).isTrue();
        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(USER_ID);
        assertThat(jwtTokenProvider.getUsername(token)).isEqualTo(USERNAME);
        assertThat(jwtTokenProvider.getTokenVersion(token)).isEqualTo(3);
        assertThat(jwtTokenProvider.isRefreshToken(token)).isFalse();

        Claims claims = jwtTokenProvider.parse(token);
        assertThat(claims.getIssuer()).isEqualTo("trackmeet-test");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("刷新令牌：类型可正确识别")
    void shouldIdentifyRefreshToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken(USER_ID, USERNAME, 0);

        assertThat(jwtTokenProvider.isRefreshToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.getUserId(refreshToken)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("登出：令牌进入黑名单后校验失败")
    void shouldRejectBlacklistedToken() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, USERNAME, 0);
        assertThat(jwtTokenProvider.validate(token)).isTrue();

        jwtTokenProvider.blacklist(token);

        assertThat(jwtTokenProvider.validate(token)).isFalse();
        assertThat(jwtTokenProvider.isBlacklisted(jwtTokenProvider.getTokenId(token))).isTrue();
    }

    @Test
    @DisplayName("伪造/篡改令牌：校验失败且不抛异常")
    void shouldRejectTamperedToken() {
        String token = jwtTokenProvider.createAccessToken(USER_ID, USERNAME, 0);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(jwtTokenProvider.validate(tampered)).isFalse();
        assertThat(jwtTokenProvider.validate("not-a-jwt")).isFalse();
        assertThat(jwtTokenProvider.validate("")).isFalse();
        assertThat(jwtTokenProvider.validate(null)).isFalse();
    }

    @Test
    @DisplayName("请求头解析：正确剥离 Bearer 前缀，无前缀时按原文处理")
    void shouldResolveTokenFromHeader() {
        assertThat(jwtTokenProvider.resolveToken("Bearer abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(jwtTokenProvider.resolveToken("abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(jwtTokenProvider.resolveToken(null)).isNull();
    }
}
