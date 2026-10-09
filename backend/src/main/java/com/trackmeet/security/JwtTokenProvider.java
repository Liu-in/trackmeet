package com.trackmeet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JWT 令牌工具：签发、解析、校验与登出黑名单。
 * 说明：黑名单为进程内实现（单机局域网部署足够）；如需多实例可替换为 Redis。
 *
 * @author TrackMeet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_USERNAME = "uname";
    public static final String CLAIM_TYPE = "typ";
    /** 令牌版本：与 sys_user.token_version 比对，改密/停用/改角色后旧令牌立即失效 */
    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;

    private SecretKey secretKey;

    /** jti -> 失效时间戳（毫秒） */
    private final ConcurrentMap<String, Long> blacklist = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        String secret = jwtProperties.getSecret();
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("trackmeet.jwt.secret 必须配置且长度不少于 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发访问令牌。
     */
    public String createAccessToken(Long userId, String username, Integer tokenVersion) {
        return createToken(userId, username, tokenVersion, TYPE_ACCESS, jwtProperties.getAccessTokenExpireMinutes());
    }

    /**
     * 签发刷新令牌。
     */
    public String createRefreshToken(Long userId, String username, Integer tokenVersion) {
        return createToken(userId, username, tokenVersion, TYPE_REFRESH, jwtProperties.getRefreshTokenExpireMinutes());
    }

    private String createToken(Long userId, String username, Integer tokenVersion, String type, long expireMinutes) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subject(String.valueOf(userId))
                .issuer(jwtProperties.getIssuer())
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion == null ? 0 : tokenVersion)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析令牌，非法或过期抛异常。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验令牌是否有效（签名正确、未过期、未登出）。
     */
    public boolean validate(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            Claims claims = parse(token);
            boolean notExpired = claims.getExpiration() != null && claims.getExpiration().after(new Date());
            return notExpired && !isBlacklisted(claims.getId());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 校验失败: {}", ex.getMessage());
            return false;
        }
    }

    public Long getUserId(String token) {
        Object value = parse(token).get(CLAIM_USER_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    public String getUsername(String token) {
        Object value = parse(token).get(CLAIM_USERNAME);
        return value == null ? null : String.valueOf(value);
    }

    public Integer getTokenVersion(String token) {
        Object value = parse(token).get(CLAIM_TOKEN_VERSION);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.valueOf(String.valueOf(value));
    }

    public String getTokenType(String token) {
        Object value = parse(token).get(CLAIM_TYPE);
        return value == null ? null : String.valueOf(value);
    }

    public String getTokenId(String token) {
        return parse(token).getId();
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(getTokenType(token));
    }

    public long getAccessTokenExpireSeconds() {
        return jwtProperties.getAccessTokenExpireMinutes() * 60L;
    }

    /**
     * 将令牌加入黑名单（登出、改密、停用时调用）。
     */
    public void blacklist(String token) {
        try {
            Claims claims = parse(token);
            if (claims.getExpiration() == null) {
                return;
            }
            long expireAt = claims.getExpiration().getTime();
            if (expireAt > System.currentTimeMillis()) {
                blacklist.put(claims.getId(), expireAt);
            }
            cleanExpired();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("加入黑名单失败（令牌非法）: {}", ex.getMessage());
        }
    }

    public boolean isBlacklisted(String tokenId) {
        if (!StringUtils.hasText(tokenId)) {
            return false;
        }
        Long expireAt = blacklist.get(tokenId);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            blacklist.remove(tokenId);
            return false;
        }
        return true;
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    /**
     * 从请求头值中剥离前缀，得到裸令牌。
     */
    public String resolveToken(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        String prefix = jwtProperties.getPrefix();
        if (StringUtils.hasText(prefix) && headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length()).trim();
        }
        return headerValue.trim();
    }

    public String getHeaderName() {
        return jwtProperties.getHeader();
    }
}
