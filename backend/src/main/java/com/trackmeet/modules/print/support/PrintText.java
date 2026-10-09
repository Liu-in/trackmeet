package com.trackmeet.modules.print.support;

import java.math.BigDecimal;

/**
 * 打印文本工具：统一「空值输出 -」「数值去尾零」「多段备注拼接」规则。
 * <p>
 * 秩序册四张表的单元格要求全部为字符串、空值输出 {@code -}，便于前端直接渲染 A4 表格，
 * 所有取值转换集中在本类，避免各处规则不一致。
 *
 * @author TrackMeet
 */
public final class PrintText {

    /** 空值占位符 */
    public static final String EMPTY = "-";

    private PrintText() {
    }

    /**
     * 文本单元：去除首尾空白，空值输出 {@link #EMPTY}。
     *
     * @param value 原值
     * @return 非空显示文本
     */
    public static String text(String value) {
        if (value == null) {
            return EMPTY;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? EMPTY : trimmed;
    }

    /**
     * 数值单元：空值输出 {@link #EMPTY}，小数去尾零后输出（如 11.20 → 11.2，600 → 600）。
     *
     * @param value 数值
     * @return 显示文本
     */
    public static String number(Number value) {
        if (value == null) {
            return EMPTY;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /**
     * 拼接多段文本（跳过空值与 {@link #EMPTY}），用空格分隔，全部为空时输出 {@link #EMPTY}。
     *
     * @param parts 文本片段
     * @return 拼接结果
     */
    public static String join(String... parts) {
        return joinWith(" ", parts);
    }

    /**
     * 按指定分隔符拼接多段文本（跳过空值与 {@link #EMPTY}），全部为空时输出 {@link #EMPTY}。
     *
     * @param separator 分隔符
     * @param parts     文本片段
     * @return 拼接结果
     */
    public static String joinWith(String separator, String... parts) {
        if (parts == null || parts.length == 0) {
            return EMPTY;
        }
        String sep = separator == null ? "" : separator;
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty() || EMPTY.equals(trimmed)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(sep);
            }
            builder.append(trimmed);
        }
        return builder.length() == 0 ? EMPTY : builder.toString();
    }

    /**
     * 数值是否有效（非空且大于 0）。
     *
     * @param value 数值
     * @return true 表示有效正数
     */
    public static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    /**
     * 取首个非空文本（去除首尾空白后判断），全部为空时返回 null。
     *
     * @param values 候选文本
     * @return 首个非空文本或 null
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
