package com.trackmeet.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 请求信息工具：IP、UA、操作系统、浏览器、链路 ID。
 *
 * @author TrackMeet
 */
public final class RequestUtils {

    private static final String UNKNOWN = "unknown";
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };
    private static final Pattern EDGE_PATTERN = Pattern.compile("(Edg|Edge)/([\\d.]+)");
    private static final Pattern CHROME_PATTERN = Pattern.compile("(Chrome)/([\\d.]+)");
    private static final Pattern FIREFOX_PATTERN = Pattern.compile("(Firefox)/([\\d.]+)");
    private static final Pattern SAFARI_PATTERN = Pattern.compile("(Version)/([\\d.]+).*Safari");
    private static final Pattern WINDOWS_PATTERN = Pattern.compile("(Windows NT [\\d.]+)");
    private static final Pattern MAC_PATTERN = Pattern.compile("(Mac OS X [\\d_.]+)");
    private static final Pattern ANDROID_PATTERN = Pattern.compile("(Android [\\d.]+)");
    private static final Pattern IOS_PATTERN = Pattern.compile("(iPhone OS [\\d_]+)");
    private static final Pattern LINUX_PATTERN = Pattern.compile("(Linux)");

    private RequestUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !UNKNOWN.equalsIgnoreCase(value)) {
                int index = value.indexOf(',');
                return index > 0 ? value.substring(0, index).trim() : value.trim();
            }
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : UNKNOWN;
    }

    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String ua = request.getHeader("User-Agent");
        return StringUtils.hasText(ua) ? truncate(ua, 500) : UNKNOWN;
    }

    public static String getOs(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN;
        }
        Matcher matcher = WINDOWS_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MAC_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return matcher.group(1).replace('_', '.');
        }
        matcher = ANDROID_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = IOS_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return matcher.group(1).replace('_', '.');
        }
        matcher = LINUX_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Linux";
        }
        return UNKNOWN;
    }

    public static String getBrowser(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN;
        }
        Matcher matcher = EDGE_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Edge " + matcher.group(2);
        }
        matcher = CHROME_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Chrome " + matcher.group(2);
        }
        matcher = FIREFOX_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Firefox " + matcher.group(2);
        }
        matcher = SAFARI_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Safari " + matcher.group(2);
        }
        return UNKNOWN;
    }

    public static String getDeviceType(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "PAD";
        }
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return "MOBILE";
        }
        return "PC";
    }

    public static String getRequestId(HttpServletRequest request) {
        if (request != null) {
            String header = request.getHeader("X-Request-Id");
            if (StringUtils.hasText(header)) {
                return truncate(header, 64);
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
