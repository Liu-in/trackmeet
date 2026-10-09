package com.trackmeet.config;

import com.trackmeet.common.context.MeetContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 届次上下文过滤器：从请求头 X-Meet-Id 或查询参数 meetId 解析当前届次。
 *
 * @author TrackMeet
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MeetContextFilter extends OncePerRequestFilter {

    public static final String HEADER_MEET_ID = "X-Meet-Id";
    public static final String PARAM_MEET_ID = "meetId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String value = request.getHeader(HEADER_MEET_ID);
            if (!StringUtils.hasText(value)) {
                value = request.getParameter(PARAM_MEET_ID);
            }
            if (StringUtils.hasText(value)) {
                try {
                    MeetContext.set(Long.valueOf(value.trim()));
                } catch (NumberFormatException ex) {
                    log.debug("忽略非法的 meetId: {}", value);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            MeetContext.clear();
        }
    }
}
