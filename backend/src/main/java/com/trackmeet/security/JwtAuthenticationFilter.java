package com.trackmeet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权过滤器：
 * 1. 解析请求头令牌；
 * 2. 校验签名、有效期、黑名单；
 * 3. 比对令牌版本（改密/停用/改角色后旧令牌立即失效）；
 * 4. 装载 LoginUser 到 SecurityContext。
 *
 * @author TrackMeet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = jwtTokenProvider.resolveToken(request.getHeader(jwtTokenProvider.getHeaderName()));
        if (StringUtils.hasText(token)
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtTokenProvider.validate(token)
                && !jwtTokenProvider.isRefreshToken(token)) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                Integer tokenVersion = jwtTokenProvider.getTokenVersion(token);
                LoginUser loginUser = userDetailsService.loadUserById(userId);
                boolean versionMatched = loginUser.getTokenVersion() == null
                        || loginUser.getTokenVersion().equals(tokenVersion);
                if (loginUser.isEnabled() && versionMatched) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("令牌已失效 userId={} enabled={} versionMatched={}", userId, loginUser.isEnabled(), versionMatched);
                }
            } catch (Exception ex) {
                log.debug("装载登录用户失败: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
