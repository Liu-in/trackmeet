package com.trackmeet.common.context;

/**
 * 当前届次上下文（ThreadLocal）。
 * 由 {@code MeetContextFilter} 从请求头 X-Meet-Id 或参数 meetId 写入，
 * 供 MyBatis-Plus 多租户拦截器拼接 meet_id 条件、供服务层做数据范围校验。
 *
 * @author TrackMeet
 */
public final class MeetContext {

    private static final ThreadLocal<Long> CURRENT_MEET = new ThreadLocal<>();

    private MeetContext() {
    }

    public static void set(Long meetId) {
        CURRENT_MEET.set(meetId);
    }

    public static Long get() {
        return CURRENT_MEET.get();
    }

    public static boolean hasMeet() {
        return CURRENT_MEET.get() != null;
    }

    public static void clear() {
        CURRENT_MEET.remove();
    }
}
