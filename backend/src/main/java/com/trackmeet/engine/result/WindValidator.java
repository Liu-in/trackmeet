package com.trackmeet.engine.result;

import java.math.BigDecimal;

/**
 * 风速合法性与适用性判定。
 *
 * <p>规则：目前通行上限为顺风 2.0 m/s（短跑、跨栏、跳远、三级跳）；投掷、中长跑、竞走不计风速。</p>
 *
 * @author TrackMeet
 */
public final class WindValidator {

    /** 默认风速上限（m/s） */
    public static final BigDecimal DEFAULT_MAX_LEGAL = new BigDecimal("2.0");

    private WindValidator() {
    }

    /**
     * 风速是否合法（未测风速视为合法）。
     *
     * @param windSpeed 风速（顺风为正）
     * @param maxLegal  上限（为空取 2.0）
     */
    public static boolean isLegal(BigDecimal windSpeed, BigDecimal maxLegal) {
        if (windSpeed == null) {
            return true;
        }
        BigDecimal limit = maxLegal == null ? DEFAULT_MAX_LEGAL : maxLegal;
        return windSpeed.abs().compareTo(limit) <= 0;
    }

    /**
     * 该项目是否需要计风速。
     *
     * @param eventType TRACK/FIELD/RELAY/ALLROUND
     * @param subType   SPRINT/HURDLE/JUMP/LONG/MIDDLE/THROW/WALK...
     */
    public static boolean isApplicable(String eventType, String subType) {
        String type = eventType == null ? "" : eventType.trim().toUpperCase();
        String sub = subType == null ? "" : subType.trim().toUpperCase();
        if ("TRACK".equals(type)) {
            return "SPRINT".equals(sub) || "HURDLE".equals(sub);
        }
        if ("FIELD".equals(type)) {
            return "JUMP".equals(sub);
        }
        return false;
    }

    /**
     * 超风速提示文案。
     */
    public static String illegalMessage(BigDecimal windSpeed, BigDecimal maxLegal) {
        BigDecimal limit = maxLegal == null ? DEFAULT_MAX_LEGAL : maxLegal;
        return String.format("风速 %s m/s 超过上限 %s m/s，该成绩不作为纪录认定依据", windSpeed, limit);
    }
}
