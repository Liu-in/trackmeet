package com.trackmeet.engine.schedule;

/**
 * 项目时长估算：按项目类型与子类型给出单元内占用时长（分钟）。
 *
 * <p>取值均为「兜底默认」，业务上以 {@code event_schedule_rule.default_duration_min} 与
 * {@code event.duration_min} 为准（调用方可传入已配置的时长，本类仅用于缺失时的估算）。</p>
 *
 * <ul>
 *   <li>径赛：按「每组分钟数 × 组数 + 准备时间」计算；</li>
 *   <li>田赛：按「人数 × 试跳次数 × 每人次分钟数」计算，并限制在 [15, 150] 分钟；</li>
 *   <li>全能：按整段时长估算（默认 180 分钟）。</li>
 * </ul>
 *
 * @author TrackMeet
 */
public final class DurationEstimator {

    /** 短跑每组分钟数 */
    private static final int SPRINT_PER_HEAT = 6;
    /** 跨栏每组分钟数 */
    private static final int HURDLE_PER_HEAT = 8;
    /** 中跑每组分钟数 */
    private static final int MIDDLE_PER_HEAT = 10;
    /** 长跑每组分钟数 */
    private static final int LONG_PER_HEAT = 18;
    /** 障碍赛每组分钟数 */
    private static final int STEEPLE_PER_HEAT = 18;
    /** 竞走每组分钟数 */
    private static final int WALK_PER_HEAT = 20;
    /** 接力每组分钟数 */
    private static final int RELAY_PER_HEAT = 8;
    /** 径赛固定准备时间（发令、器材、集合） */
    private static final int TRACK_SETUP_MINUTES = 5;
    /** 田赛每人每次试跳/试掷分钟数 */
    private static final double FIELD_MINUTES_PER_ATTEMPT = 1.2;
    /** 田赛最少时长 */
    private static final int FIELD_MIN_MINUTES = 15;
    /** 田赛最长时长 */
    private static final int FIELD_MAX_MINUTES = 150;
    /** 田赛默认试跳/试掷次数 */
    private static final int DEFAULT_ATTEMPT_COUNT = 3;
    /** 全能整段时长 */
    private static final int ALLROUND_MINUTES = 180;
    /** 任意项目最少时长 */
    private static final int MIN_TOTAL_MINUTES = 10;

    private DurationEstimator() {
    }

    /**
     * 估算项目时长。
     *
     * @param eventType      项目类型：TRACK/FIELD/ALLROUND/RELAY
     * @param subType        子类型：SPRINT/MIDDLE/LONG/HURDLE/STEEPLE/WALK/JUMP/THROW/RELAY
     * @param participantCount 参赛人数
     * @param heatCount      组数
     * @param attemptCount   田赛试跳/试掷次数
     * @return 估算时长（分钟，>= 10）
     */
    public static int estimate(String eventType, String subType, int participantCount, int heatCount, int attemptCount) {
        String type = eventType == null ? "TRACK" : eventType.trim().toUpperCase();
        if ("ALLROUND".equals(type)) {
            return ALLROUND_MINUTES;
        }
        if ("FIELD".equals(type)) {
            int attempts = attemptCount > 0 ? attemptCount : DEFAULT_ATTEMPT_COUNT;
            int count = Math.max(1, participantCount);
            int minutes = (int) Math.ceil(count * attempts * FIELD_MINUTES_PER_ATTEMPT);
            return clamp(minutes, FIELD_MIN_MINUTES, FIELD_MAX_MINUTES);
        }
        int perHeat = trackMinutesPerHeat(type, subType);
        int heats = Math.max(1, heatCount);
        return Math.max(MIN_TOTAL_MINUTES, perHeat * heats + TRACK_SETUP_MINUTES);
    }

    private static int trackMinutesPerHeat(String eventType, String subType) {
        if ("RELAY".equals(eventType)) {
            return RELAY_PER_HEAT;
        }
        String sub = subType == null ? "SPRINT" : subType.trim().toUpperCase();
        switch (sub) {
            case "HURDLE":
                return HURDLE_PER_HEAT;
            case "MIDDLE":
                return MIDDLE_PER_HEAT;
            case "LONG":
                return LONG_PER_HEAT;
            case "STEEPLE":
                return STEEPLE_PER_HEAT;
            case "WALK":
                return WALK_PER_HEAT;
            case "RELAY":
                return RELAY_PER_HEAT;
            case "SPRINT":
            default:
                return SPRINT_PER_HEAT;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
