package com.trackmeet.modules.result.service.impl;

import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 成绩原文解析工具（包内可见，静态无状态）。
 * <p>
 * 成绩双列建模要求 {@code result_raw} 与 {@code result_value} 同时写入：原文供显示，
 * 数值供排序比较。手工录入时前端可能只给原文，此处统一把原文解析为可比较数值：
 * <ul>
 *     <li>纯数字：{@code 11.23} → 11.23；{@code 5.68} → 5.68；</li>
 *     <li>秒数原文：{@code 11"23} / {@code 11'23} / {@code 11″23} → 11.23（田径径赛惯用写法）；</li>
 *     <li>分秒原文：{@code 1:02.45} → 62.45；{@code 2:03} → 123.00；</li>
 *     <li>无效原文（如 {@code DNS}、{@code DNF}）：解析失败返回 {@code null}，由调用方按特殊状态处理。</li>
 * </ul>
 *
 * @author TrackMeet
 */
final class ResultRawParser {

    /** 纯数字（含小数） */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^\\d+(\\.\\d+)?$");

    /** 分:秒（秒可带小数） */
    private static final Pattern MINUTE_SECOND = Pattern.compile("^(\\d+):(\\d{1,2}(\\.\\d+)?)$");

    /** 秒 + 百分秒（田径惯写：11"23 / 11'23 / 11″23） */
    private static final Pattern SECOND_HUNDREDTH = Pattern.compile("^(\\d+)[\"'″’](\\d{1,2})$");

    /** 秒的小数点后最大位数（成绩统一按两位小数入库） */
    private static final int SCALE = 2;

    private ResultRawParser() {
    }

    /**
     * 把成绩原文解析为可比较数值。
     *
     * @param raw 成绩原文，允许为空
     * @return 解析出的数值（保留 2 位小数），无法解析时返回 {@code null}
     */
    static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        // 1) 纯数字
        if (PLAIN_NUMBER.matcher(text).matches()) {
            return scale(text);
        }
        // 2) 分:秒
        Matcher minute = MINUTE_SECOND.matcher(text);
        if (minute.matches()) {
            BigDecimal minutes = new BigDecimal(minute.group(1));
            BigDecimal seconds = new BigDecimal(minute.group(2));
            return minutes.multiply(BigDecimal.valueOf(60)).add(seconds).setScale(SCALE, RoundingMode.HALF_UP);
        }
        // 3) 秒 + 百分秒
        Matcher hundredth = SECOND_HUNDREDTH.matcher(text);
        if (hundredth.matches()) {
            BigDecimal seconds = new BigDecimal(hundredth.group(1));
            BigDecimal fraction = new BigDecimal(hundredth.group(2))
                    .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
            return seconds.add(fraction).setScale(SCALE, RoundingMode.HALF_UP);
        }
        return null;
    }

    /**
     * 数值化并统一精度。
     *
     * @param text 数字文本
     * @return 保留 2 位小数的数值
     */
    static BigDecimal scale(String text) {
        try {
            return new BigDecimal(text).setScale(SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析原文，解析失败即抛参数错误（用于「必须给出可比较成绩」的场景，如田赛单次试跳）。
     *
     * @param raw   成绩原文
     * @param field 字段中文名（用于错误提示）
     * @return 解析出的数值
     */
    static BigDecimal parseRequired(String raw, String field) {
        BigDecimal value = parse(raw);
        if (value == null) {
            throw new BizException(ResultCode.PARAM_ERROR, field + "无法解析为成绩数值：" + raw);
        }
        return value;
    }

    /**
     * 是否为「无成绩」类原文（DNS/DNF/DSQ/NM/PASS/SCRATCH 及中文简写），
     * 用于区分「无效状态标记」与「无法解析的错误输入」。
     *
     * @param raw 成绩原文
     * @return 属于无效状态标记返回 true
     */
    static boolean isInvalidStatusText(String raw) {
        if (raw == null) {
            return false;
        }
        String text = raw.trim().toUpperCase();
        return switch (text) {
            case "DNS", "DNF", "DSQ", "NM", "PASS", "SCRATCH", "弃权", "未出发", "未完成", "犯规" -> true;
            default -> false;
        };
    }
}
