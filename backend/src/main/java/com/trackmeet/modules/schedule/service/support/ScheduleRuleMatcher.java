package com.trackmeet.modules.schedule.service.support;

import com.trackmeet.engine.schedule.DurationEstimator;
import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import com.trackmeet.modules.schedule.vo.EventScheduleRuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 项目时长与顺序默认规则匹配器（{@code event_schedule_rule}，全局表无 meet_id）。
 * <p>
 * 匹配优先级：项目类型 + 子类型 + 性别 &gt; 项目类型 + 子类型 &gt; 项目类型 + 性别 &gt; 仅项目类型。
 * 规则缺失时全部回退到引擎兜底值（{@link DurationEstimator}）或业务默认值，不硬编码业务阈值。
 *
 * @author TrackMeet
 */
@Component
@RequiredArgsConstructor
public class ScheduleRuleMatcher {

    /** 兜底提前检录分钟数 */
    public static final int DEFAULT_CALL_AHEAD_MIN = 20;
    /** 兜底兼项最小间隔（分钟），与 {@code ConflictOptions} 默认值保持一致 */
    public static final int DEFAULT_CONFLICT_INTERVAL_MIN = 30;
    /** 兜底裁判连场最小间隔（分钟） */
    public static final int DEFAULT_REFEREE_INTERVAL_MIN = 15;
    /** 兜底道数 */
    public static final int DEFAULT_LANE_COUNT = 8;
    /** 兜底优先级（越小越先排） */
    public static final int DEFAULT_PRIORITY = 100;

    private final ScheduleMapper scheduleMapper;

    /**
     * 载入全部启用规则（每次编排/分组调用一次，避免逐条查询）。
     *
     * @return 规则列表（永不为 null）
     */
    public List<EventScheduleRuleVO> loadRules() {
        List<EventScheduleRuleVO> rules = scheduleMapper.selectEventScheduleRules();
        return rules == null ? new ArrayList<>() : rules;
    }

    /**
     * 按「项目类型 × 子类型 × 性别」匹配规则。
     *
     * @param rules     规则列表
     * @param eventType 项目类型
     * @param subType   子类型
     * @param gender    性别
     * @return 命中的规则，全部缺失返回 null
     */
    public EventScheduleRuleVO match(List<EventScheduleRuleVO> rules, String eventType, String subType, String gender) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        String type = normalize(eventType);
        String sub = normalize(subType);
        String sex = normalize(gender);
        EventScheduleRuleVO typeAndSub = null;
        EventScheduleRuleVO typeOnly = null;
        EventScheduleRuleVO typeAndGender = null;
        for (EventScheduleRuleVO rule : rules) {
            if (rule == null) {
                continue;
            }
            if (!Objects.equals(type, normalize(rule.getEventType()))) {
                continue;
            }
            boolean sameSub = sub != null && sub.equals(normalize(rule.getSubType()));
            boolean sameGender = sex != null && sex.equals(normalize(rule.getGender()));
            if (sameSub && sameGender) {
                return rule;
            }
            if (sameSub && typeAndSub == null) {
                typeAndSub = rule;
            }
            if (sameGender && typeAndGender == null) {
                typeAndGender = rule;
            }
            if (typeOnly == null) {
                typeOnly = rule;
            }
        }
        if (typeAndSub != null) {
            return typeAndSub;
        }
        if (typeAndGender != null) {
            return typeAndGender;
        }
        return typeOnly;
    }

    /**
     * 解析项目预计时长（分钟）：规则默认时长 → 项目自身时长 → 引擎估算。
     *
     * @param rule             命中的规则（可空）
     * @param eventDurationMin 项目自身预计时长（可空）
     * @param eventType        项目类型
     * @param subType          子类型
     * @param participantCount 参赛人数
     * @param heatCount        组数
     * @param attemptCount     田赛试跳/试掷次数（可空）
     * @return 预计时长（分钟，&gt;= 1）
     */
    public int resolveDuration(EventScheduleRuleVO rule, Integer eventDurationMin, String eventType, String subType,
                               int participantCount, int heatCount, Integer attemptCount) {
        if (rule != null && rule.getDefaultDurationMin() != null && rule.getDefaultDurationMin() > 0) {
            return rule.getDefaultDurationMin();
        }
        if (eventDurationMin != null && eventDurationMin > 0) {
            return eventDurationMin;
        }
        int attempts = attemptCount == null || attemptCount <= 0 ? 0 : attemptCount;
        return Math.max(1, DurationEstimator.estimate(eventType, subType, participantCount, heatCount, attempts));
    }

    /**
     * 提前检录分钟数（规则缺失按 20 分钟）。
     */
    public int resolveCallAheadMin(EventScheduleRuleVO rule) {
        if (rule != null && rule.getCallAheadMin() != null && rule.getCallAheadMin() >= 0) {
            return rule.getCallAheadMin();
        }
        return DEFAULT_CALL_AHEAD_MIN;
    }

    /**
     * 偏好时段（MORNING / AFTERNOON / EVENING / ANY）。
     */
    public String resolvePeriodPreference(EventScheduleRuleVO rule) {
        if (rule != null && StringUtils.hasText(rule.getPeriodPreference())) {
            return rule.getPeriodPreference().trim().toUpperCase();
        }
        return "ANY";
    }

    /**
     * 同项目轮次最小间隔（分钟）；未配置返回 0（由编排参数兜底）。
     */
    public int resolveEventIntervalMin(EventScheduleRuleVO rule) {
        if (rule != null && rule.getEventIntervalMin() != null && rule.getEventIntervalMin() > 0) {
            return rule.getEventIntervalMin();
        }
        return 0;
    }

    /**
     * 默认道数（规则缺失按 8）。
     */
    public int resolveLaneCount(EventScheduleRuleVO rule) {
        if (rule != null && rule.getDefaultLaneCount() != null && rule.getDefaultLaneCount() > 0) {
            return rule.getDefaultLaneCount();
        }
        return DEFAULT_LANE_COUNT;
    }

    /**
     * 优先级：默认排序权重，缺失按 100。
     */
    public int resolvePriority(EventScheduleRuleVO rule) {
        if (rule != null && rule.getDefaultOrder() != null && rule.getDefaultOrder() > 0) {
            return rule.getDefaultOrder();
        }
        return DEFAULT_PRIORITY;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
