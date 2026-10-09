package com.trackmeet.modules.schedule.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.engine.grouping.SerpentineGrouper;
import com.trackmeet.engine.lane.LaneAllocator;
import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.GroupingOptions;
import com.trackmeet.engine.model.HeatResult;
import com.trackmeet.modules.schedule.dto.HeatsGenerateDTO;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.vo.AthleteEntryRowVO;
import com.trackmeet.modules.schedule.vo.EventBriefRowVO;
import com.trackmeet.modules.schedule.vo.HeatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分组草案构建器：把「报名装配行 + 既有分组」转换为可落库的 {@link GroupDraft}。
 * <p>
 * 算法全部委托引擎（{@link SerpentineGrouper} / {@link LaneAllocator}），本类只负责：
 * <ol>
 *   <li>参数解析：{@code heatCount} / {@code heatSize} / {@code avoidSameUnit} / {@code seedAscending}
 *       的业务默认值（项目级规则 → 场地道数 → 引擎默认）；</li>
 *   <li>锁定保护：{@code heat.is_locked=1} 的既有分组整组保留（成员与道次原样），
 *       这些组的成员不进入重排池，仅对池内选手重新分组；</li>
 *   <li>组号分配：锁定组保留原组号，新组从「既有最大组号 + 1」起顺延，避免违反
 *       {@code uk_heat_schedule_no(schedule_id, heat_no)}。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Component
@RequiredArgsConstructor
public class HeatDraftBuilder {

    /** 分组方式：蛇形（缺省） */
    public static final String DRAW_SERPENT = "SERPENT";
    /** 分组方式：按种子成绩段 */
    public static final String DRAW_SEED = "SEED";
    /** 分组方式：随机抽签 */
    public static final String DRAW_RANDOM = "RANDOM";
    /** 分组方式：按单位 */
    public static final String DRAW_UNIT = "UNIT";

    /** 道次分配方式：按种子 */
    public static final String LANE_SEED = "SEED";
    /** 道次分配方式：抽签 */
    public static final String LANE_DRAW = "DRAW";
    /** 道次分配方式：按顺序 */
    public static final String LANE_ORDER = "ORDER";

    /** 缺省随机种子（保证抽签结果可复现） */
    private static final long DEFAULT_RANDOM_SEED = 20250101L;

    private final ScheduleDataProvider dataProvider;
    private final AthleteEntryFactory athleteEntryFactory;
    private final ObjectMapper objectMapper;

    /**
     * 构建分组草案（不落库）。
     *
     * @param schedule      日程条目
     * @param event         项目简要投影
     * @param request       入参（可为空，全部取默认值）
     * @param existingHeats 既有分组（含锁定组）
     * @param data          届次只读数据快照
     * @return 分组草案
     */
    public GroupDraft build(Schedule schedule, EventBriefRowVO event, HeatsGenerateDTO request,
                            List<HeatVO> existingHeats, CachedScheduleData data) {
        HeatsGenerateDTO params = request == null ? new HeatsGenerateDTO() : request;
        Long meetId = schedule.getMeetId();
        Long eventId = schedule.getEventId();
        Long venueId = schedule.getVenueId();

        int laneCount = params.getLaneCount() != null && params.getLaneCount() > 0
                ? params.getLaneCount()
                : data.laneCount(eventId, venueId);
        List<Integer> laneOrder = JsonTextUtils.parseIntArray(objectMapper, data.laneOrderJson(eventId, venueId));
        if (laneOrder.isEmpty()) {
            laneOrder = LaneAllocator.defaultLaneOrder(laneCount);
        }
        String drawMode = resolveDrawMode(params.getDrawMode(), data.drawMode(eventId));
        String laneMode = resolveLaneMode(params.getLaneMode(), data, eventId, params);
        boolean track = isTrack(event);
        long randomSeed = params.getRandomSeed() == null ? DEFAULT_RANDOM_SEED : params.getRandomSeed();

        GroupDraft draft = new GroupDraft();
        draft.setScheduleId(schedule.getId());
        draft.setEventId(eventId);
        draft.setTrack(track);
        draft.setDrawMode(drawMode);
        draft.setLaneMode(track ? laneMode : null);
        draft.setLaneCount(laneCount);
        draft.setLaneOrder(laneOrder);

        // 锁定组整组保留
        Set<Long> lockedRegistrationIds = new LinkedHashSet<>();
        int maxHeatNo = 0;
        List<HeatVO> lockedHeats = new ArrayList<>();
        for (HeatVO heat : existingHeats == null ? Collections.<HeatVO>emptyList() : existingHeats) {
            maxHeatNo = Math.max(maxHeatNo, heat.getHeatNo() == null ? 0 : heat.getHeatNo());
            if (heat.getIsLocked() != null && heat.getIsLocked() == Heat.LOCKED) {
                lockedHeats.add(heat);
            }
        }
        for (HeatVO heat : lockedHeats) {
            GroupDraft.HeatDraft lockedDraft = new GroupDraft.HeatDraft();
            lockedDraft.setExistingHeatId(heat.getId());
            lockedDraft.setHeatNo(heat.getHeatNo() == null ? 0 : heat.getHeatNo());
            lockedDraft.setLocked(true);
            lockedDraft.setLaneCount(heat.getLaneCount() == null ? laneCount : heat.getLaneCount());
            lockedDraft.setSeedCount(heat.getSeedCount() == null ? 0 : heat.getSeedCount());
            draft.getHeats().add(lockedDraft);
            for (var member : heat.getMembers() == null
                    ? Collections.<com.trackmeet.modules.schedule.vo.HeatMemberVO>emptyList() : heat.getMembers()) {
                if (member.getRegistrationId() != null) {
                    lockedRegistrationIds.add(member.getRegistrationId());
                }
            }
        }
        draft.setLockedHeatCount(lockedHeats.size());

        // 重排池：排除锁定组成员
        List<AthleteEntryRowVO> rows = dataProvider.seedEntries(meetId, eventId);
        List<AthleteEntryRowVO> pool = rows.stream()
                .filter(row -> row.getRegistrationId() != null && !lockedRegistrationIds.contains(row.getRegistrationId()))
                .collect(Collectors.toList());
        draft.setParticipantCount(pool.size());

        if (!pool.isEmpty()) {
            List<AthleteEntryRowVO> ordered = sortPool(pool, drawMode, randomSeed);
            int heatSize = resolveHeatSize(params.getHeatSize(), data.drawGroupSize(eventId), laneCount);
            int poolHeatCount = resolvePoolHeatCount(params.getHeatCount(), ordered.size(), heatSize,
                    draft.getLockedHeatCount());
            boolean avoidSameUnit = params.getAvoidSameUnit() != null
                    ? params.getAvoidSameUnit()
                    : data.avoidSameUnit(eventId);
            boolean seedAscending = track;
            List<HeatResult> results = SerpentineGrouper.group(toEntries(ordered), GroupingOptions.builder()
                    .heatCount(poolHeatCount)
                    .heatSize(heatSize)
                    .avoidSameUnit(avoidSameUnit)
                    .seedAscending(seedAscending)
                    .randomizeUnseeded(DRAW_RANDOM.equals(drawMode))
                    .randomSeed(randomSeed)
                    .build());
            // 锁定组占位后可能不足以容纳全部新组，按需追加（保持组数口径与组号连续）
            int targetHeatCount = Math.max(poolHeatCount, results.size());
            Set<Integer> usedHeatNos = draft.getHeats().stream()
                    .map(GroupDraft.HeatDraft::getHeatNo)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            int heatNo = maxHeatNo;
            for (int index = 0; index < targetHeatCount; index++) {
                while (usedHeatNos.contains(heatNo + 1)) {
                    heatNo++;
                }
                heatNo++;
                usedHeatNos.add(heatNo);
                GroupDraft.HeatDraft heatDraft = new GroupDraft.HeatDraft();
                heatDraft.setHeatNo(heatNo);
                heatDraft.setLocked(false);
                heatDraft.setLaneCount(laneCount);
                if (index < results.size() && results.get(index) != null) {
                    heatDraft.setMembers(toMemberDrafts(results.get(index).getMembers(), ordered));
                }
                heatDraft.setSeedCount((int) heatDraft.getMembers().stream()
                        .filter(member -> member.getIsSeed() == 1).count());
                draft.getHeats().add(heatDraft);
            }
        }
        draft.getHeats().sort(Comparator.comparingInt(GroupDraft.HeatDraft::getHeatNo));
        draft.setHeatCount(draft.getHeats().size());
        return draft;
    }

    /**
     * 参数补全：由草案生成「道次分配请求」等价参数。
     *
     * @param draft 草案
     * @return 道次分配方式（空表示不分配）
     */
    public String laneModeOf(GroupDraft draft) {
        return draft == null ? null : draft.getLaneMode();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private String resolveDrawMode(String requested, String ruleDrawMode) {
        String value = StringUtils.hasText(requested) ? requested : ruleDrawMode;
        if (!StringUtils.hasText(value)) {
            return DRAW_SERPENT;
        }
        String mode = value.trim().toUpperCase();
        Set<String> supported = Set.of(DRAW_SERPENT, DRAW_SEED, DRAW_RANDOM, DRAW_UNIT);
        return supported.contains(mode) ? mode : DRAW_SERPENT;
    }

    private String resolveLaneMode(String requested, CachedScheduleData data, Long eventId, HeatsGenerateDTO params) {
        if (StringUtils.hasText(requested)) {
            String mode = requested.trim().toUpperCase();
            return LANE_SEED.equals(mode) || LANE_DRAW.equals(mode) || LANE_ORDER.equals(mode) ? mode : LANE_SEED;
        }
        // 未显式指定：首轮按项目级规则，其次按调度参数（自动编排场景传 SEED）
        var rule = data.eventRule(eventId);
        if (rule != null && StringUtils.hasText(rule.getLaneAssignMode())) {
            String mode = rule.getLaneAssignMode().trim().toUpperCase();
            if (LANE_SEED.equals(mode) || LANE_DRAW.equals(mode) || LANE_ORDER.equals(mode)) {
                return mode;
            }
        }
        if (params.getLaneCount() != null) {
            return LANE_SEED;
        }
        return null;
    }

    private boolean isTrack(EventBriefRowVO event) {
        if (event == null || !StringUtils.hasText(event.getEventType())) {
            return false;
        }
        String type = event.getEventType().trim().toUpperCase();
        return "TRACK".equals(type) || "RELAY".equals(type);
    }

    private int resolveHeatSize(Integer requested, Integer ruleGroupSize, int laneCount) {
        if (requested != null && requested > 0) {
            return requested;
        }
        if (ruleGroupSize != null && ruleGroupSize > 0) {
            return ruleGroupSize;
        }
        return laneCount > 0 ? laneCount : ScheduleRuleMatcher.DEFAULT_LANE_COUNT;
    }

    private int resolvePoolHeatCount(Integer requested, int poolSize, int heatSize, int lockedHeatCount) {
        if (requested != null && requested > 0) {
            return Math.max(1, Math.min(requested, Math.max(1, poolSize)));
        }
        int size = heatSize > 0 ? heatSize : ScheduleRuleMatcher.DEFAULT_LANE_COUNT;
        int total = (int) Math.ceil((double) poolSize / size);
        // 用户给出的是「日程总组数」时，扣除已锁定组，避免组数翻倍
        if (lockedHeatCount > 0) {
            total = Math.max(1, total - lockedHeatCount);
        }
        return Math.max(1, Math.min(total, Math.max(1, poolSize)));
    }

    /**
     * 池内顺序：SEED/蛇形按成绩（径赛升序），RANDOM 先洗牌，UNIT 按单位聚合后再交给蛇形分配。
     */
    private List<AthleteEntryRowVO> sortPool(List<AthleteEntryRowVO> pool, String drawMode, long randomSeed) {
        List<AthleteEntryRowVO> ordered = new ArrayList<>(pool);
        if (DRAW_RANDOM.equals(drawMode)) {
            Collections.shuffle(ordered, new Random(randomSeed));
            return ordered;
        }
        if (DRAW_UNIT.equals(drawMode)) {
            ordered.sort(Comparator
                    .comparing((AthleteEntryRowVO row) -> row.getUnitId() == null ? Long.MAX_VALUE : row.getUnitId())
                    .thenComparing(row -> row.getBibNo() == null ? "" : row.getBibNo())
                    .thenComparing(row -> row.getRegistrationId() == null ? Long.MAX_VALUE : row.getRegistrationId()));
            return ordered;
        }
        // SEED 与 SERPENT：有成绩者按成绩升序（径赛越小越好），无成绩者按号码兜底排在最后
        ordered.sort(Comparator
                .comparing((AthleteEntryRowVO row) -> row.getSeedValue() == null ? 1 : 0)
                .thenComparing(row -> row.getSeedValue() == null ? java.math.BigDecimal.ZERO : row.getSeedValue())
                .thenComparing(row -> row.getBibNo() == null ? "" : row.getBibNo())
                .thenComparing(row -> row.getRegistrationId() == null ? Long.MAX_VALUE : row.getRegistrationId()));
        return ordered;
    }

    private List<AthleteEntry> toEntries(List<AthleteEntryRowVO> rows) {
        return athleteEntryFactory.fromRows(rows);
    }

    private List<GroupDraft.MemberDraft> toMemberDrafts(List<AthleteEntry> entries, List<AthleteEntryRowVO> rows) {
        List<GroupDraft.MemberDraft> members = new ArrayList<>();
        if (entries == null) {
            return members;
        }
        for (AthleteEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            AthleteEntryRowVO row = rows.stream()
                    .filter(item -> Objects.equals(item.getRegistrationId(), entry.getRegistrationId()))
                    .findFirst()
                    .orElse(null);
            GroupDraft.MemberDraft member = new GroupDraft.MemberDraft();
            member.setRegistrationId(entry.getRegistrationId());
            member.setAthleteId(entry.getAthleteId());
            member.setUnitId(entry.getUnitId());
            member.setBibNo(entry.getBibNo());
            member.setSeedResult(entry.getSeedValue());
            member.setSeedResultRaw(entry.getSeedResult());
            member.setIsSeed(entry.getSeedValue() == null ? 0 : 1);
            if (row != null) {
                member.setGroupId(row.getGroupId());
                member.setSeedResultUnit(row.getSeedResultUnit());
            }
            members.add(member);
        }
        return members;
    }
}
