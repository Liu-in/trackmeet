package com.trackmeet.common.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 富文本白名单净化器（防存储型 XSS）。
 *
 * <p>公告正文等由管理员录入、又会在管理端与大屏以 {@code v-html} 渲染，因此必须在服务端净化。
 * 本类为自包含实现（不引入额外依赖），策略：</p>
 * <ol>
 *   <li>删除注释与危险元素整体（{@code script/style/iframe/object/embed/svg/math/form/input/link/meta/noscript} 等）；</li>
 *   <li>删除所有 {@code on*} 事件属性与 {@code javascript:/vbscript:/data:} 协议 URL；</li>
 *   <li>仅保留白名单标签，其余标签被丢弃但保留其文本内容；</li>
 *   <li>标签属性只保留 {@code a} 的 {@code href/title}，并强制 {@code rel="noopener noreferrer"}；</li>
 *   <li>整条流水线重复执行直至结果稳定（最多 5 轮），防御 {@code <scr<script>ipt>} 这类拼接绕过。</li>
 * </ol>
 *
 * @author TrackMeet
 */
public final class HtmlSanitizer {

    /** 允许保留的标签 */
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "strong", "b", "em", "i", "u", "s", "del", "ul", "ol", "li",
            "a", "span", "div", "h1", "h2", "h3", "h4", "blockquote", "pre", "code",
            "table", "thead", "tbody", "tr", "th", "td", "hr", "sub", "sup");

    /** 危险元素（连内容一起删除） */
    private static final String DANGEROUS =
            "script|style|iframe|frame|frameset|object|embed|applet|svg|math|form|input|button"
                    + "|select|textarea|link|meta|base|noscript|template";

    private static final Pattern DANGEROUS_BLOCK =
            Pattern.compile("(?is)<\\s*(" + DANGEROUS + ")\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>");
    private static final Pattern DANGEROUS_TAG =
            Pattern.compile("(?is)<\\s*/?\\s*(" + DANGEROUS + ")\\b[^>]*>");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern EVENT_ATTR =
            Pattern.compile("(?i)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern SCRIPT_URL = Pattern.compile(
            "(?i)\\b(href|src|xlink:href)\\s*=\\s*(\"\\s*(?:javascript|vbscript|data)\\s*:[^\"]*\""
                    + "|'\\s*(?:javascript|vbscript|data)\\s*:[^']*'"
                    + "|\\s*(?:javascript|vbscript|data)\\s*:[^\\s>]+)");
    private static final Pattern TAG =
            Pattern.compile("(?is)<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern HREF_ATTR =
            Pattern.compile("(?is)\\bhref\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern TITLE_ATTR =
            Pattern.compile("(?is)\\btitle\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    /** 最大净化轮数 */
    private static final int MAX_PASSES = 5;

    private HtmlSanitizer() {
    }

    /**
     * 净化 HTML。
     *
     * @param html 原始富文本（可为 null）
     * @return 净化后的 HTML；入参为 null 或空白时原样返回
     */
    public static String sanitize(String html) {
        if (html == null || html.trim().isEmpty()) {
            return html;
        }
        String current = html;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            String cleaned = cleanOnce(current);
            if (cleaned.equals(current)) {
                return cleaned;
            }
            current = cleaned;
        }
        return current;
    }

    /**
     * 是否为「净化后无实际内容」（仅空白与空标签），用于校验必填正文。
     */
    public static boolean isBlankAfterSanitize(String html) {
        String cleaned = sanitize(html);
        if (cleaned == null) {
            return true;
        }
        return TAG.matcher(cleaned).replaceAll("").replace("&nbsp;", " ").trim().isEmpty();
    }

    private static String cleanOnce(String html) {
        String result = DANGEROUS_BLOCK.matcher(html).replaceAll("");
        result = COMMENT.matcher(result).replaceAll("");
        result = DANGEROUS_TAG.matcher(result).replaceAll("");
        result = SCRIPT_URL.matcher(result).replaceAll("");
        result = EVENT_ATTR.matcher(result).replaceAll("");
        return normalizeTags(result);
    }

    /**
     * 白名单标签归一化：允许的标签保留（仅 {@code a} 留 href/title），其余标签丢弃、保留文本。
     */
    private static String normalizeTags(String html) {
        Matcher matcher = TAG.matcher(html);
        StringBuilder builder = new StringBuilder(html.length());
        int lastEnd = 0;
        while (matcher.find()) {
            builder.append(html, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String closing = matcher.group(1);
            String name = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase();
            String attributes = matcher.group(3) == null ? "" : matcher.group(3);
            if (!ALLOWED_TAGS.contains(name)) {
                continue;
            }
            if ("br".equals(name) || "hr".equals(name)) {
                builder.append('<').append(name).append("/>");
                continue;
            }
            if (!closing.isEmpty()) {
                builder.append("</").append(name).append('>');
                continue;
            }
            builder.append('<').append(name);
            if ("a".equals(name)) {
                appendAttribute(builder, "href", HREF_ATTR, attributes);
                appendAttribute(builder, "title", TITLE_ATTR, attributes);
                builder.append(" rel=\"noopener noreferrer\"");
            }
            builder.append('>');
        }
        builder.append(html, lastEnd, html.length());
        return builder.toString();
    }

    private static void appendAttribute(StringBuilder builder, String name, Pattern pattern, String attributes) {
        Matcher matcher = pattern.matcher(attributes);
        if (!matcher.find()) {
            return;
        }
        String value = matcher.group(1);
        if (value == null) {
            return;
        }
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("data:")) {
            return;
        }
        builder.append(' ').append(name).append("=\"").append(trimmed.replace("\"", "&quot;")).append('"');
    }
}
