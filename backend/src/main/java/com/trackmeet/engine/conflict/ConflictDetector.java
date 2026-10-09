package com.trackmeet.engine.conflict;

import com.trackmeet.engine.model.ConflictItem;
import com.trackmeet.engine.model.ConflictOptions;
import com.trackmeet.engine.model.ScheduledItem;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 冲突检测：运动员兼项、场地冲突、裁判冲突、时间间隔不足。
 *
 * <p>判定规则：</p>
 * <ol>
 *   <li><b>时间重叠</b>（{@code overlapMinutes > 0}）时：
 *     <ul>
 *       <li>同一场地 → {@code VENUE}（ERROR）</li>
 *       <li>同一运动员 → {@code ATHLETE}（ERROR，即兼项冲突）</li>
 *       <li>同一裁判 → {@code REFEREE}（ERROR）</li>
 *     </ul>
 *   </li>
 *   <li><b>时间不重叠</b>时，检查间隔：同一运动员两项目间隔 &lt; {@code athleteIntervalMin}、
 *       同一裁判两场间隔 &lt; {@code refereeIntervalMin} → {@code INTERVAL}（WARN，供编排员判断）。</li>
 * </ol>
 *
 * <p>复杂度 O(n²)，适用于单届运动会日程规模（数百条）；时间已按开始时间排序，便于提前终止。</p>
 *
 * @author TrackMeet
 */
public final class ConflictDetector {

    public static final String TYPE_ATHLETE = "ATHLETE";
    public static final String TYPE_VENUE = "VENUE";
    public static final String TYPE_REFEREE = "REFEREE";
    public static final String TYPE_INTERVAL = "INTERVAL";

    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_WARN = "WARN";

    private ConflictDetector() {
    }

    /**
     * 检测冲突。
     *
     * @param items   已排日程条目（时间与参与人）
     * @param options 检测开关与阈值
     * @return 冲突清单（ERROR 在前，其次按日程 ID 排序）
     */
    public static List<ConflictItem> detect(List<ScheduledItem> items, ConflictOptions options) {
        ConflictOptions opts = options == null ? ConflictOptions.builder().build() : options;
        List<ConflictItem> conflicts = new ArrayList<>();
        List<ScheduledItem> valid = new ArrayList<>();
        if (items != null) {
            for (ScheduledItem item : items) {
                if (item != null && item.getStartTime() != null && item.getEndTime() != null) {
                    valid.add(item);
                }
            }
        }
        if (valid.size() < 2) {
            return conflicts;
        }
        valid.sort(Comparator.comparing(ScheduledItem::getStartTime));

        for (int i = 0; i < valid.size(); i++) {
            ScheduledItem first = valid.get(i);
            for (int j = i + 1; j < valid.size(); j++) {
                ScheduledItem second = valid.get(j);
                // 已按开始时间排序：若 second 开始不早于 first 结束，则后续均不重叠（但仍可能存在间隔不足）
                boolean overlap = second.getStartTime().isBefore(first.getEndTime());
                if (overlap) {
                    long overlapMinutes = Duration.between(second.getStartTime(), min(first.getEndTime(), second.getEndTime())).toMinutes();
                    if (overlapMinutes <= 0) {
                        overlapMinutes = 1;
                    }
                    if (opts.isCheckVenue()) {
                        addVenueConflict(conflicts, first, second, overlapMinutes);
                    }
                    if (opts.isCheckAthlete()) {
                        addAthleteConflict(conflicts, first, second, overlapMinutes);
                    }
                    if (opts.isCheckReferee()) {
                        addRefereeConflict(conflicts, first, second, overlapMinutes);
                    }
                } else {
                    long gapMinutes = Duration.between(first.getEndTime(), second.getStartTime()).toMinutes();
                    if (opts.isCheckAthlete() && opts.getAthleteIntervalMin() > 0 && gapMinutes < opts.getAthleteIntervalMin()) {
                        addIntervalConflict(conflicts, first, second, gapMinutes, opts.getAthleteIntervalMin(), true);
                    }
                    if (opts.isCheckReferee() && opts.getRefereeIntervalMin() > 0 && gapMinutes < opts.getRefereeIntervalMin()) {
                        addIntervalConflict(conflicts, first, second, gapMinutes, opts.getRefereeIntervalMin(), false);
                    }
                }
            }
        }

        conflicts.sort(Comparator
                .comparing((ConflictItem item) -> LEVEL_ERROR.equals(item.getLevel()) ? 0 : 1)
                .thenComparing(item -> item.getScheduleId() == null ? 0L : item.getScheduleId())
                .thenComparing(item -> item.getConflictType() == null ? "" : item.getConflictType()));
        return conflicts;
    }

    private static void addVenueConflict(List<ConflictItem> conflicts, ScheduledItem first, ScheduledItem second, long overlapMinutes) {
        if (first.getVenueId() == null || !first.getVenueId().equals(second.getVenueId())) {
            return;
        }
        ConflictItem item = new ConflictItem();
        item.setConflictType(TYPE_VENUE);
        item.setLevel(LEVEL_ERROR);
        item.setScheduleId(first.getScheduleId());
        item.setRelatedScheduleId(second.getScheduleId());
        item.setVenueId(first.getVenueId());
        item.setOverlapMinutes(overlapMinutes);
        item.setMessage(String.format("场地冲突：%s 与 %s 在同一场地重叠 %d 分钟",
                name(first), name(second), overlapMinutes));
        conflicts.add(item);
    }

    private static void addAthleteConflict(List<ConflictItem> conflicts, ScheduledItem first, ScheduledItem second, long overlapMinutes) {
        for (Long athleteId : intersect(first.getAthleteIds(), second.getAthleteIds())) {
            ConflictItem item = new ConflictItem();
            item.setConflictType(TYPE_ATHLETE);
            item.setLevel(LEVEL_ERROR);
            item.setScheduleId(first.getScheduleId());
            item.setRelatedScheduleId(second.getScheduleId());
            item.setAthleteId(athleteId);
            item.setOverlapMinutes(overlapMinutes);
            item.setMessage(String.format("兼项冲突：运动员(%d) 同时参加 %s 与 %s，重叠 %d 分钟",
                    athleteId, name(first), name(second), overlapMinutes));
            conflicts.add(item);
        }
    }

    private static void addRefereeConflict(List<ConflictItem> conflicts, ScheduledItem first, ScheduledItem second, long overlapMinutes) {
        for (Long refereeId : intersect(first.getRefereeIds(), second.getRefereeIds())) {
            ConflictItem item = new ConflictItem();
            item.setConflictType(TYPE_REFEREE);
            item.setLevel(LEVEL_ERROR);
            item.setScheduleId(first.getScheduleId());
            item.setRelatedScheduleId(second.getScheduleId());
            item.setRefereeId(refereeId);
            item.setOverlapMinutes(overlapMinutes);
            item.setMessage(String.format("裁判冲突：裁判(%d) 同时执裁 %s 与 %s，重叠 %d 分钟",
                    refereeId, name(first), name(second), overlapMinutes));
            conflicts.add(item);
        }
    }

    private static void addIntervalConflict(List<ConflictItem> conflicts, ScheduledItem first, ScheduledItem second,
                                            long gapMinutes, int requiredMinutes, boolean athleteRelated) {
        List<Long> related = athleteRelated
                ? intersect(first.getAthleteIds(), second.getAthleteIds())
                : intersect(first.getRefereeIds(), second.getRefereeIds());
        for (Long id : related) {
            ConflictItem item = new ConflictItem();
            item.setConflictType(TYPE_INTERVAL);
            item.setLevel(LEVEL_WARN);
            item.setScheduleId(first.getScheduleId());
            item.setRelatedScheduleId(second.getScheduleId());
            if (athleteRelated) {
                item.setAthleteId(id);
            } else {
                item.setRefereeId(id);
            }
            item.setIntervalMinutes(gapMinutes);
            item.setRequiredIntervalMin((long) requiredMinutes);
            item.setMessage(String.format("%s间隔不足：%s 与 %s 之间仅 %d 分钟（要求 ≥ %d 分钟）",
                    athleteRelated ? "兼项" : "裁判连场", name(first), name(second), gapMinutes, requiredMinutes));
            conflicts.add(item);
        }
    }

    private static String name(ScheduledItem item) {
        if (item.getItemName() != null && !item.getItemName().isEmpty()) {
            return item.getItemName();
        }
        return item.getEventId() == null ? "未命名项目" : ("项目" + item.getEventId());
    }

    private static LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    /**
     * 求两个 ID 列表的交集（保持稳定顺序，去重）。
     */
    private static List<Long> intersect(List<Long> first, List<Long> second) {
        List<Long> result = new ArrayList<>();
        if (first == null || first.isEmpty() || second == null || second.isEmpty()) {
            return result;
        }
        Set<Long> secondSet = new HashSet<>(second);
        Set<Long> added = new HashSet<>();
        for (Long id : first) {
            if (id != null && secondSet.contains(id) && added.add(id)) {
                result.add(id);
            }
        }
        return result;
    }
}
