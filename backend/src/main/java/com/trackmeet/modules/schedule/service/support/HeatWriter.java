package com.trackmeet.modules.schedule.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.trackmeet.engine.fieldorder.FieldOrderGenerator;
import com.trackmeet.engine.lane.LaneAllocator;
import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.LaneAssignment;
import com.trackmeet.engine.model.LaneOptions;
import com.trackmeet.modules.schedule.entity.FieldOrder;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.entity.Lane;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.mapper.FieldOrderMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.mapper.LaneMapper;
import com.trackmeet.modules.schedule.vo.EventScheduleRuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分组分道落库器：把 {@link GroupDraft} 写入 {@code heat / heat_member / lane / field_order}。
 * <p>
 * 落库策略：
 * <ol>
 *   <li><b>锁定保护</b>：草案中 {@code locked=true} 的组只更新派生统计（人数/种子数），
 *       不删除、不重建其成员与道次；</li>
 *   <li><b>清理口径</b>：仅清理未锁定组的成员/道次，且用<b>物理删除</b>（{@code delete} 走逻辑删除，
 *       会残留 {@code uk_hm_heat_order} / {@code uk_lane_heat_no} 唯一键占位行）；</li>
 *   <li><b>顺序</b>：先插 heat（拿到主键）→ 插 heat_member（顺序即 {@code order_no}）→ 插 lane
 *       （径赛，按 {@link LaneAllocator}）→ 插 field_order（田赛，按 {@link FieldOrderGenerator}）。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Component
@RequiredArgsConstructor
public class HeatWriter {

    /** 每组最短时长（分钟），用于把日程时长均摊到各组 */
    private static final int MIN_HEAT_MINUTES = 1;

    /** 单组最长时长（分钟），避免组数极少时单组长时间占用 */
    private static final int MAX_HEAT_MINUTES = 180;

    private final HeatMapper heatMapper;
    private final HeatMemberMapper heatMemberMapper;
    private final LaneMapper laneMapper;
    private final FieldOrderMapper fieldOrderMapper;
    private final ScheduleRuleMatcher ruleMatcher;

    /**
     * 落库分组草案。
     *
     * @param schedule     日程条目
     * @param draft        分组草案
     * @param batchNo      编排批次号
     * @param callAheadMin 提前检录分钟数（规则缺失按 20）
     * @param rule         项目时长与顺序默认规则（可空）
     * @return 已落库的分组列表（含成员数/种子数/检录与开始时间）
     */
    public List<Heat> write(Schedule schedule, GroupDraft draft, String batchNo, int callAheadMin,
                            EventScheduleRuleVO rule) {
        List<Heat> persisted = new ArrayList<>();
        if (schedule == null || draft == null || draft.getHeats().isEmpty()) {
            return persisted;
        }
        Long meetId = schedule.getMeetId();
        Long scheduleId = schedule.getId();
        LocalDateTime start = schedule.getPlanStartTime();
        int perHeatMinutes = perHeatMinutes(schedule.getDurationMin(), draft.getHeats().size());
        int index = 0;
        for (GroupDraft.HeatDraft heatDraft : draft.getHeats()) {
            LocalDateTime heatStart = start == null ? null : start.plusMinutes((long) perHeatMinutes * index);
            LocalDateTime heatEnd = heatStart == null ? null : heatStart.plusMinutes(perHeatMinutes);
            index++;

            Heat heat = new Heat();
            heat.setMeetId(meetId);
            heat.setScheduleId(scheduleId);
            heat.setHeatNo(heatDraft.getHeatNo());
            heat.setHeatName(buildHeatName(rule, heatDraft.getHeatNo()));
            heat.setLaneCount(heatDraft.getLaneCount() == null ? draft.getLaneCount() : heatDraft.getLaneCount());
            heat.setMemberCount(0);
            heat.setSeedCount(0);
            heat.setStartTime(heatStart);
            heat.setEndTime(heatEnd);
            heat.setCallTime(heatStart == null ? null : heatStart.minusMinutes(Math.max(0, callAheadMin)));
            heat.setStatus(Heat.STATUS_PLANNED);
            heat.setSourceType(Heat.SOURCE_AUTO);
            heat.setBatchNo(batchNo);
            heat.setDrawMode(draft.getDrawMode());

            if (heatDraft.isLocked() && heatDraft.getExistingHeatId() != null) {
                // 锁定组：保留成员与道次，仅同步派生统计与时间窗
                heat.setId(heatDraft.getExistingHeatId());
                Heat patch = new Heat();
                patch.setId(heatDraft.getExistingHeatId());
                patch.setMemberCount(heatDraft.getMembers().size());
                patch.setSeedCount(heatDraft.getSeedCount());
                patch.setLaneCount(heat.getLaneCount());
                patch.setStartTime(heatStart);
                patch.setEndTime(heatEnd);
                if (heat.getCallTime() != null) {
                    patch.setCallTime(heat.getCallTime());
                }
                heatMapper.update(patch, new LambdaUpdateWrapper<Heat>().eq(Heat::getId, patch.getId()));
                Heat current = heatMapper.selectById(heatDraft.getExistingHeatId());
                persisted.add(current == null ? heat : current);
                continue;
            }

            heat.setIsLocked(Heat.UNLOCKED);
            heatMapper.insert(heat);
            Long heatId = heat.getId();

            List<HeatMember> members = insertMembers(schedule, heat, heatDraft.getMembers(), batchNo);
            heat.setMemberCount(members.size());
            heat.setSeedCount((int) members.stream().filter(member -> Objects.equals(member.getIsSeed(), 1)).count());

            if (draft.isTrack() && draft.getLaneMode() != null) {
                insertLanes(schedule, heatId, members, draft, batchNo);
            } else {
                insertFieldOrders(schedule, heatId, members, draft, batchNo);
            }

            Heat patch = new Heat();
            patch.setId(heatId);
            patch.setMemberCount(heat.getMemberCount());
            patch.setSeedCount(heat.getSeedCount());
            heatMapper.update(patch, new LambdaUpdateWrapper<Heat>().eq(Heat::getId, patch.getId()));
            persisted.add(heat);
        }
        return persisted;
    }

    /**
     * 仅落库指定分组的成员（用于「重新生成分组且存在锁定组」的增量场景）。
     *
     * @param schedule 日程条目
     * @param draft    分组草案
     * @param heatIds  目标分组 ID（与草案中未锁定组一一对应，按组号升序）
     * @param batchNo  编排批次号
     * @return 已更新的分组列表
     */
    public List<Heat> writeInto(Schedule schedule, GroupDraft draft, List<Long> heatIds, String batchNo) {
        List<Heat> persisted = new ArrayList<>();
        if (schedule == null || draft == null || heatIds == null || heatIds.isEmpty()) {
            return persisted;
        }
        int cursor = 0;
        for (GroupDraft.HeatDraft heatDraft : draft.getHeats()) {
            if (heatDraft.isLocked()) {
                continue;
            }
            if (cursor >= heatIds.size()) {
                break;
            }
            Long heatId = heatIds.get(cursor++);
            Heat heat = heatMapper.selectById(heatId);
            if (heat == null) {
                continue;
            }
            clearHeatContent(heatId);
            List<HeatMember> members = insertMembers(schedule, heat, heatDraft.getMembers(), batchNo);
            Heat patch = new Heat();
            patch.setId(heatId);
            patch.setMemberCount(members.size());
            patch.setSeedCount((int) members.stream().filter(member -> Objects.equals(member.getIsSeed(), 1)).count());
            patch.setDrawMode(draft.getDrawMode());
            patch.setBatchNo(batchNo);
            heatMapper.update(patch, new LambdaUpdateWrapper<Heat>().eq(Heat::getId, patch.getId()));
            if (draft.isTrack() && draft.getLaneMode() != null) {
                insertLanes(schedule, heatId, members, draft, batchNo);
            } else {
                insertFieldOrders(schedule, heatId, members, draft, batchNo);
            }
            persisted.add(heatMapper.selectById(heatId));
        }
        return persisted;
    }

    /**
     * 清理单个分组的成员、道次与出场顺序（物理删除，避免唯一键占位行）。
     *
     * @param heatId 分组 ID
     */
    public void clearHeatContent(Long heatId) {
        if (heatId == null) {
            return;
        }
        // 物理删除：逻辑删除会残留 uk_hm_heat_order / uk_lane_heat_no / uk_forder_heat_order 占位行
        heatMemberMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heatId));
        laneMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heatId));
        fieldOrderMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heatId));
    }

    /**
     * 清理日程下全部未锁定分组的内容（保留分组行本身，便于增量重排）。
     *
     * @param meetId     届次 ID
     * @param scheduleId 日程 ID
     * @return 已清理内容的分组 ID 列表（按组号升序，含被锁定的组则跳过）
     */
    public List<Long> clearUnlockedHeats(Long meetId, Long scheduleId) {
        List<Heat> heats = heatMapper.selectList(new LambdaQueryWrapper<Heat>()
                .eq(Heat::getMeetId, meetId)
                .eq(Heat::getScheduleId, scheduleId));
        List<Long> cleared = new ArrayList<>();
        for (Heat heat : heats) {
            if (heat.getIsLocked() != null && heat.getIsLocked() == Heat.LOCKED) {
                continue;
            }
            clearHeatContent(heat.getId());
            cleared.add(heat.getId());
        }
        return cleared;
    }

    /**
     * 物理删除分组行。
     * <p>
     * {@code heat} 上有唯一键 {@code uk_heat_schedule_no(schedule_id, heat_no)}，
     * 逻辑删除会残留占位行导致同组号无法再次插入，故按主键逐条物理删除。
     * 调用方需先通过 {@link #clearHeatContent(Long)} 清理成员、道次与出场顺序。
     *
     * @param heatId 分组 ID
     */
    public void deleteHeatPhysically(Long heatId) {
        if (heatId == null) {
            return;
        }
        heatMapper.deleteByIdPhysically(heatId);
    }

    /**
     * 单个分组的道次分配（重新分配道次接口使用）。
     *
     * @param schedule 日程条目
     * @param heat     分组
     * @param members  组内人员（已按 order_no 升序）
     * @param mode     分配方式
     * @param laneCount 道数
     * @param laneOrder 道次优先顺序
     * @param batchNo  编排批次号
     * @return 落库的道次列表
     */
    public List<Lane> assignLanesForHeat(Schedule schedule, Heat heat, List<HeatMember> members, String mode,
                                         int laneCount, List<Integer> laneOrder, String batchNo) {
        return assignLanesForHeat(schedule, heat, members, mode, laneCount, laneOrder, batchNo, 0);
    }

    /**
     * 单个分组的道次分配（可限制本次分配人数）。
     *
     * @param schedule  日程条目
     * @param heat      分组
     * @param members   组内人员（已按 order_no 升序）
     * @param mode      分配方式
     * @param laneCount 道数
     * @param laneOrder 道次优先顺序
     * @param batchNo   编排批次号
     * @param maxAssign 本次分配人数上限（&lt;=0 表示全部）
     * @return 落库的道次列表
     */
    public List<Lane> assignLanesForHeat(Schedule schedule, Heat heat, List<HeatMember> members, String mode,
                                         int laneCount, List<Integer> laneOrder, String batchNo, int maxAssign) {
        laneMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heat.getId()));
        GroupDraft draft = new GroupDraft();
        draft.setTrack(true);
        draft.setLaneMode(mode);
        draft.setLaneCount(laneCount);
        draft.setLaneOrder(laneOrder);
        insertLanes(schedule, heat.getId(), members, draft, batchNo, maxAssign);
        return laneMapper.selectList(new LambdaQueryWrapper<Lane>()
                .eq(Lane::getHeatId, heat.getId())
                .orderByAsc(Lane::getLaneNo));
    }

    // ------------------------------------------------------------------
    // 内部写入
    // ------------------------------------------------------------------

    private List<HeatMember> insertMembers(Schedule schedule, Heat heat, List<GroupDraft.MemberDraft> drafts,
                                           String batchNo) {
        List<HeatMember> members = new ArrayList<>();
        int orderNo = 1;
        for (GroupDraft.MemberDraft draft : drafts) {
            HeatMember member = new HeatMember();
            member.setMeetId(schedule.getMeetId());
            member.setHeatId(heat.getId());
            member.setScheduleId(schedule.getId());
            member.setRegistrationId(draft.getRegistrationId());
            member.setAthleteId(draft.getAthleteId());
            member.setUnitId(draft.getUnitId());
            member.setBibNo(draft.getBibNo());
            member.setGroupId(draft.getGroupId());
            member.setOrderNo(orderNo++);
            member.setIsSeed(draft.getIsSeed());
            member.setIsScratch(0);
            member.setStatus(HeatMember.STATUS_NOT_CALLED);
            member.setSeedResult(draft.getSeedResult());
            member.setSeedResultRaw(draft.getSeedResultRaw());
            member.setSeedResultUnit(draft.getSeedResultUnit());
            member.setIsLocked(HeatMember.UNLOCKED);
            member.setBatchNo(batchNo);
            heatMemberMapper.insert(member);
            members.add(member);
        }
        return members;
    }

    /**
     * 道次分配：组内顺序即成绩顺序（{@code order_no}），按 {@link LaneAllocator} 取道次并回写
     * {@code heat_member.lane_no} 便于检录表输出。
     */
    private void insertLanes(Schedule schedule, Long heatId, List<HeatMember> members, GroupDraft draft,
                             String batchNo) {
        insertLanes(schedule, heatId, members, draft, batchNo, 0);
    }

    private void insertLanes(Schedule schedule, Long heatId, List<HeatMember> members, GroupDraft draft,
                             String batchNo, int maxAssign) {
        if (members.isEmpty()) {
            return;
        }
        List<HeatMember> assignable = maxAssign > 0 && members.size() > maxAssign
                ? new ArrayList<>(members.subList(0, maxAssign))
                : members;
        List<AthleteEntry> entries = new ArrayList<>();
        for (HeatMember member : assignable) {
            entries.add(AthleteEntry.builder()
                    .registrationId(member.getRegistrationId())
                    .athleteId(member.getAthleteId())
                    .bibNo(member.getBibNo())
                    .unitId(member.getUnitId())
                    .seedValue(member.getSeedResult())
                    .seedResult(member.getSeedResultRaw())
                    .build());
        }
        int laneCount = draft.getLaneCount() > 0 ? draft.getLaneCount() : ScheduleRuleMatcher.DEFAULT_LANE_COUNT;
        List<Integer> laneOrder = draft.getLaneOrder() == null || draft.getLaneOrder().isEmpty()
                ? LaneAllocator.defaultLaneOrder(laneCount)
                : draft.getLaneOrder();
        LaneOptions options = LaneOptions.builder()
                .laneCount(laneCount)
                .laneOrder(new ArrayList<>(laneOrder))
                .mode(draft.getLaneMode() == null ? "SEED" : draft.getLaneMode())
                .build();        List<LaneAssignment> assignments = LaneAllocator.assign(entries, options);
        String orderJson = laneOrder.toString();
        int index = 0;
        for (LaneAssignment assignment : assignments) {
            HeatMember member = assignable.stream()
                    .filter(item -> Objects.equals(item.getRegistrationId(), assignment.getRegistrationId()))
                    .findFirst()
                    .orElse(null);
            if (member == null) {
                continue;
            }
            Lane lane = new Lane();
            lane.setMeetId(schedule.getMeetId());
            lane.setHeatId(heatId);
            lane.setHeatMemberId(member.getId());
            lane.setScheduleId(schedule.getId());
            lane.setLaneNo(assignment.getLaneNo());
            lane.setAssignMode(assignment.getAssignMode());
            lane.setLaneOrderSource(Lane.ORDER_SOURCE_EVENT);
            lane.setLaneOrderJson(orderJson);
            lane.setSeedRank(++index);
            lane.setIsBestLane(assignment.getLaneNo() == laneOrder.get(0) ? 1 : 0);
            lane.setIsLocked(Lane.UNLOCKED);
            lane.setBatchNo(batchNo);
            laneMapper.insert(lane);

            HeatMember patch = new HeatMember();
            patch.setId(member.getId());
            patch.setLaneNo(assignment.getLaneNo());
            heatMemberMapper.update(patch, new LambdaUpdateWrapper<HeatMember>().eq(HeatMember::getId, patch.getId()));
        }
    }

    /**
     * 田赛出场顺序：按草案的分组方式映射为 {@code FieldOrderMode}，顺序号从 1 开始。
     */
    private void insertFieldOrders(Schedule schedule, Long heatId, List<HeatMember> members, GroupDraft draft,
                                   String batchNo) {
        if (members.isEmpty()) {
            return;
        }
        List<AthleteEntry> entries = new ArrayList<>();
        Map<Long, HeatMember> memberByRegistration = new HashMap<>();
        for (HeatMember member : members) {
            memberByRegistration.put(member.getRegistrationId(), member);
            entries.add(AthleteEntry.builder()
                    .registrationId(member.getRegistrationId())
                    .athleteId(member.getAthleteId())
                    .bibNo(member.getBibNo())
                    .unitId(member.getUnitId())
                    .seedValue(member.getSeedResult())
                    .seedResult(member.getSeedResultRaw())
                    .build());
        }
        // 田赛：远度/高度越大越好 → seedAscending=false
        List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, draft.fieldOrderMode(), false,
                resolveRandomSeed());
        int orderNo = 1;
        for (AthleteEntry entry : ordered) {
            HeatMember member = memberByRegistration.get(entry.getRegistrationId());
            if (member == null) {
                continue;
            }
            FieldOrder order = new FieldOrder();
            order.setMeetId(schedule.getMeetId());
            order.setHeatId(heatId);
            order.setHeatMemberId(member.getId());
            order.setScheduleId(schedule.getId());
            order.setEventId(schedule.getEventId());
            order.setOrderNo(orderNo++);
            order.setOrderMode(draft.fieldOrderMode().name());
            order.setBestSeed(entry.getSeedValue());
            order.setBestSeedRaw(entry.getSeedResult());
            order.setBestSeedUnit(member.getSeedResultUnit());
            order.setPassCount(0);
            order.setIsScratch(0);
            order.setIsPass(0);
            order.setIsLocked(FieldOrder.UNLOCKED);
            order.setBatchNo(batchNo);
            fieldOrderMapper.insert(order);
        }
    }

    private long resolveRandomSeed() {
        return 20250101L;
    }

    private int perHeatMinutes(Integer durationMin, int heatCount) {
        if (durationMin == null || durationMin <= 0) {
            return MAX_HEAT_MINUTES;
        }
        int per = heatCount <= 0 ? durationMin : Math.max(MIN_HEAT_MINUTES, durationMin / heatCount);
        return Math.min(per, MAX_HEAT_MINUTES);
    }

    private String buildHeatName(EventScheduleRuleVO rule, int heatNo) {
        return "第 " + heatNo + " 组";
    }
}
