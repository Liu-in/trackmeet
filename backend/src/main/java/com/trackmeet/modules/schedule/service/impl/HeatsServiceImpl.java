package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.schedule.dto.HeatMemberMoveDTO;
import com.trackmeet.modules.schedule.dto.HeatMemberSaveDTO;
import com.trackmeet.modules.schedule.dto.HeatsGenerateDTO;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.entity.Lane;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.entity.FieldOrder;
import com.trackmeet.modules.schedule.mapper.FieldOrderMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.mapper.LaneMapper;
import com.trackmeet.modules.schedule.service.HeatsService;
import com.trackmeet.modules.schedule.service.ScheduleService;
import com.trackmeet.modules.schedule.service.support.CachedScheduleData;
import com.trackmeet.modules.schedule.service.support.GroupDraft;
import com.trackmeet.modules.schedule.service.support.HeatDraftBuilder;
import com.trackmeet.modules.schedule.service.support.HeatWriter;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.service.support.OrderResequencer;
import com.trackmeet.modules.schedule.service.support.ScheduleDataProvider;
import com.trackmeet.modules.schedule.service.support.ScheduleRuleMatcher;
import com.trackmeet.modules.schedule.vo.AthleteEntryRowVO;
import com.trackmeet.modules.schedule.vo.EventScheduleRuleVO;
import com.trackmeet.modules.schedule.vo.FieldOrderVO;
import com.trackmeet.modules.schedule.vo.HeatGenerateResultVO;
import com.trackmeet.modules.schedule.vo.HeatMemberVO;
import com.trackmeet.modules.schedule.vo.HeatVO;
import com.trackmeet.modules.schedule.vo.LaneVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分组服务实现。
 * <p>
 * 算法全部委托引擎（蛇形分组 / 同单位避开 / 人数均衡 / 道次分配 / 田赛出场顺序），本类负责：
 * <ol>
 *   <li><b>装配</b>：报名 → 运动员 → 单位 → 种子成绩（{@code registration.status='APPROVED'}）；</li>
 *   <li><b>参数默认值</b>：{@code heatSize} 取场地 {@code venue.lane_count}，{@code heatCount} 缺省按
 *       {@code ceil(人数 / heatSize)}；分组方向：径赛 {@code seedAscending=true}、田赛 {@code false}；</li>
 *   <li><b>锁定保护</b>：{@code heat.is_locked=1} 的既有分组整组保留（成员与道次不重建）；</li>
 *   <li><b>派生回填</b>：分组人数/种子数、{@code heat_member.lane_no}、{@code schedule.heat_count}。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class HeatsServiceImpl extends ServiceImpl<HeatMapper, Heat> implements HeatsService {

    private final ScheduleService scheduleService;
    private final ScheduleDataProvider dataProvider;
    private final CachedScheduleData cachedData;
    private final ScheduleRuleMatcher ruleMatcher;
    private final HeatDraftBuilder heatDraftBuilder;
    private final HeatWriter heatWriter;
    private final HeatMapper heatMapper;
    private final HeatMemberMapper heatMemberMapper;
    private final LaneMapper laneMapper;
    private final FieldOrderMapper fieldOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HeatGenerateResultVO generate(HeatsGenerateDTO dto) {
        if (dto == null || dto.getScheduleId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程条目不能为空");
        }
        Schedule schedule = scheduleService.getEntity(dto.getScheduleId());
        Long meetId = schedule.getMeetId();
        cachedData.load(meetId, null, null, false);
        var event = cachedData.event(schedule.getEventId());
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "日程对应项目不存在");
        }
        var rule = ruleMatcher.match(cachedData.getScheduleRules(), event.getEventType(), event.getSubType(),
                event.getGender());
        int callAheadMin = ruleMatcher.resolveCallAheadMin(rule);

        // 既有分组（含组内人员/道次/出场顺序），锁定组整组保留
        List<HeatVO> existingHeats = loadHeatsWithDetails(meetId, schedule.getId());

        GroupDraft draft = heatDraftBuilder.build(schedule, event, dto, existingHeats, cachedData);
        boolean apply = dto.getApply() == null || Boolean.TRUE.equals(dto.getApply());

        HeatGenerateResultVO result = new HeatGenerateResultVO();
        result.setApplied(apply);
        result.setScheduleId(schedule.getId());
        result.setDrawMode(draft.getDrawMode());
        result.setLaneMode(draft.getLaneMode());
        result.setHeatCount(draft.getHeatCount());
        result.setParticipantCount(draft.getParticipantCount());

        if (draft.getParticipantCount() == 0 && draft.getLockedHeatCount() == 0) {
            // 无有效报名：不产生空分组
            throw new BizException(ResultCode.PARAM_ERROR, "该项目暂无有效报名数据，无法生成分组");
        }

        if (apply) {
            String batchNo = schedule.getBatchNo() == null ? buildBatchNo() : schedule.getBatchNo();
            persistDraft(schedule, draft, existingHeats, batchNo, callAheadMin, rule);
        }

        List<HeatVO> heats = loadHeatsWithDetails(meetId, schedule.getId());
        if (!apply) {
            heats = previewHeats(draft, existingHeats);
        }
        result.setHeats(heats);
        result.setLanes(heats.stream()
                .flatMap(heat -> heat.getLanes() == null ? java.util.stream.Stream.<LaneVO>empty()
                        : heat.getLanes().stream())
                .collect(Collectors.toList()));
        result.setHeatCount(heats.size());
        return result;
    }

    @Override
    public List<HeatVO> listHeats(Long scheduleId) {
        if (scheduleId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程条目不能为空");
        }
        Schedule schedule = scheduleService.getEntity(scheduleId);
        return loadHeatsWithDetails(schedule.getMeetId(), scheduleId);
    }

    @Override
    public Heat getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "分组 ID 不能为空");
        }
        Heat heat = this.getById(id);
        if (heat == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分组不存在");
        }
        MeetGuard.check(heat.getMeetId());
        return heat;
    }

    @Override
    public List<HeatMemberVO> listMembers(Long heatId) {
        Heat heat = getEntity(heatId);
        return dataProvider.listMembers(heat.getMeetId(), java.util.Collections.singletonList(heatId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addMember(Long heatId, HeatMemberSaveDTO dto) {
        Heat heat = getEntity(heatId);
        if (dto == null || dto.getRegistrationId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名记录不能为空");
        }
        Schedule schedule = scheduleService.getEntity(heat.getScheduleId());
        List<HeatMemberVO> current = dataProvider.listMembers(heat.getMeetId(),
                java.util.Collections.singletonList(heatId));
        HeatMember existing = current.stream()
                .filter(member -> Objects.equals(member.getRegistrationId(), dto.getRegistrationId()))
                .findFirst()
                .map(member -> this.heatMemberMapper.selectById(member.getId()))
                .orElse(null);
        if (existing != null) {
            // 幂等：已存在则仅调整顺序号
            HeatMember patch = new HeatMember();
            patch.setId(existing.getId());
            patch.setOrderNo(dto.getOrderNo() == null ? existing.getOrderNo() : dto.getOrderNo());
            heatMemberMapper.updateById(patch);
            resequence(heatId);
            refreshHeatStat(heatId);
            return existing.getId();
        }

        List<AthleteEntryRowVO> rows =
                dataProvider.seedEntries(heat.getMeetId(), schedule.getEventId());
        var row = rows.stream()
                .filter(item -> Objects.equals(item.getRegistrationId(), dto.getRegistrationId()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "报名记录不存在或未通过审核"));

        HeatMember member = new HeatMember();
        member.setMeetId(heat.getMeetId());
        member.setHeatId(heatId);
        member.setScheduleId(heat.getScheduleId());
        member.setRegistrationId(row.getRegistrationId());
        member.setAthleteId(row.getAthleteId());
        member.setUnitId(row.getUnitId());
        member.setBibNo(row.getBibNo());
        member.setGroupId(row.getGroupId());
        member.setOrderNo(dto.getOrderNo() == null ? current.size() + 1 : dto.getOrderNo());
        member.setIsSeed(row.getSeedValue() == null ? 0 : 1);
        member.setIsScratch(0);
        member.setStatus(HeatMember.STATUS_NOT_CALLED);
        member.setSeedResult(row.getSeedValue());
        member.setSeedResultRaw(row.getSeedResultRaw());
        member.setSeedResultUnit(row.getSeedResultUnit());
        member.setIsLocked(HeatMember.UNLOCKED);
        member.setBatchNo(heat.getBatchNo());
        heatMemberMapper.insert(member);
        resequence(heatId);
        refreshHeatStat(heatId);
        return member.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long heatId, Long memberId) {
        Heat heat = getEntity(heatId);
        HeatMember member = getMember(heatId, memberId);
        laneMapper.deleteByHeatMemberPhysically(member.getId());
        fieldOrderMapper.deleteByHeatMemberPhysically(member.getId());
        // 物理删除组内人员：逻辑删除会残留 uk_hm_heat_order / uk_hm_heat_registration 唯一键占位行
        heatMemberMapper.deleteByIdPhysically(member.getId());
        resequence(heatId);
        refreshHeatStat(heatId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveMember(HeatMemberMoveDTO dto) {
        if (dto == null || dto.getMemberId() == null || dto.getTargetHeatId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成员与目标分组不能为空");
        }
        HeatMember member = heatMemberMapper.selectById(dto.getMemberId());
        if (member == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组内人员不存在");
        }
        MeetGuard.check(member.getMeetId());
        Heat sourceHeat = getEntity(member.getHeatId());
        Heat targetHeat = getEntity(dto.getTargetHeatId());
        if (!Objects.equals(sourceHeat.getScheduleId(), targetHeat.getScheduleId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "只能在同一条日程的分组之间移动成员");
        }
        if (Objects.equals(member.getHeatId(), dto.getTargetHeatId())) {
            resequence(dto.getTargetHeatId());
            return;
        }
        // 目标组已有该报名记录时不重复挂载
        Long duplicated = heatMemberMapper.selectCount(new LambdaQueryWrapper<HeatMember>()
                .eq(HeatMember::getHeatId, dto.getTargetHeatId())
                .eq(HeatMember::getRegistrationId, member.getRegistrationId()));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ResultCode.CONFLICT, "目标分组已包含该报名记录");
        }
        int targetOrderNo = dto.getTargetOrderNo() == null || dto.getTargetOrderNo() <= 0
                ? (int) countMembers(dto.getTargetHeatId()) + 1
                : dto.getTargetOrderNo();
        LambdaUpdateWrapper<HeatMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HeatMember::getId, member.getId())
                .set(HeatMember::getHeatId, dto.getTargetHeatId())
                .set(HeatMember::getScheduleId, targetHeat.getScheduleId())
                .set(HeatMember::getOrderNo, targetOrderNo)
                .set(HeatMember::getLaneNo, null);
        heatMemberMapper.update(wrapper);
        // 道次随换组失效，需重新分配
        laneMapper.deleteByHeatMemberPhysically(member.getId());
        resequence(member.getHeatId());
        resequence(dto.getTargetHeatId());
        refreshHeatStat(member.getHeatId());
        refreshHeatStat(dto.getTargetHeatId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockHeat(Long id, Integer isLocked) {
        getEntity(id);
        int locked = isLocked != null && isLocked == Heat.LOCKED ? Heat.LOCKED : Heat.UNLOCKED;
        LambdaUpdateWrapper<Heat> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Heat::getId, id).set(Heat::getIsLocked, locked);
        if (locked == Heat.LOCKED) {
            wrapper.set(Heat::getLockedBy, SecurityUtils.getUserId()).set(Heat::getLockTime, LocalDateTime.now());
        } else {
            wrapper.set(Heat::getLockedBy, null).set(Heat::getLockTime, null);
        }
        this.update(wrapper);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private HeatMember getMember(Long heatId, Long memberId) {
        HeatMember member = heatMemberMapper.selectById(memberId);
        if (member == null || !Objects.equals(member.getHeatId(), heatId)) {
            throw new BizException(ResultCode.NOT_FOUND, "组内人员不存在");
        }
        MeetGuard.check(member.getMeetId());
        return member;
    }

    private long countMembers(Long heatId) {
        Long count = heatMemberMapper.selectCount(new LambdaQueryWrapper<HeatMember>().eq(HeatMember::getHeatId, heatId));
        return count == null ? 0L : count;
    }

    /**
     * 组内顺序号重排为 1..N（唯一键 {@code uk_hm_heat_order} 要求组内顺序号唯一）。
     * <p>
     * 重排通过 {@link OrderResequencer} 的两段式写法完成：先整体搬到 {@code +1000} 空档区间，
     * 再按目标顺序回写，避免直接把顺序号往前挪时撞唯一键。
     */
    private void resequence(Long heatId) {
        List<HeatMember> members = heatMemberMapper.selectList(new LambdaQueryWrapper<HeatMember>()
                .eq(HeatMember::getHeatId, heatId));
        if (members.isEmpty()) {
            return;
        }
        members.sort(Comparator
                .comparing((HeatMember member) -> member.getOrderNo() == null ? Integer.MAX_VALUE : member.getOrderNo())
                .thenComparing(member -> member.getId() == null ? Long.MAX_VALUE : member.getId()));
        OrderResequencer.resequence(members, HeatMember::getId, (id, orderNo) -> {
            LambdaUpdateWrapper<HeatMember> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(HeatMember::getId, id).set(HeatMember::getOrderNo, orderNo);
            heatMemberMapper.update(wrapper);
        });
    }

    /**
     * 回填分组派生统计（人数 / 种子数）与日程组数。
     */
    private void refreshHeatStat(Long heatId) {
        Heat heat = this.getById(heatId);
        if (heat == null) {
            return;
        }
        List<HeatMember> members = heatMemberMapper.selectList(new LambdaQueryWrapper<HeatMember>()
                .eq(HeatMember::getHeatId, heatId));
        Heat patch = new Heat();
        patch.setId(heatId);
        patch.setMemberCount(members.size());
        patch.setSeedCount((int) members.stream().filter(member -> Objects.equals(member.getIsSeed(), 1)).count());
        this.updateById(patch);
        if (heat.getScheduleId() != null) {
            long heatCount = this.count(new LambdaQueryWrapper<Heat>()
                    .eq(Heat::getScheduleId, heat.getScheduleId()));
            Schedule schedulePatch = new Schedule();
            schedulePatch.setId(heat.getScheduleId());
            schedulePatch.setHeatCount((int) heatCount);
            scheduleService.updateById(schedulePatch);
        }
    }

    /**
     * 落库草案：锁定组保留、未锁定组原地更新内容、新增组插入。
     */
    private void persistDraft(Schedule schedule, GroupDraft draft, List<HeatVO> existingHeats,
                              String batchNo, int callAheadMin,
                              EventScheduleRuleVO rule) {
        Map<Integer, HeatVO> existingByNo = existingHeats.stream()
                .filter(heat -> heat.getHeatNo() != null)
                .collect(Collectors.toMap(HeatVO::getHeatNo, heat -> heat, (first, second) -> first,
                        LinkedHashMap::new));
        List<GroupDraft.HeatDraft> newHeats = draft.getHeats().stream()
                .filter(heat -> !heat.isLocked())
                .collect(Collectors.toList());
        List<Long> reusedHeatIds = new ArrayList<>();
        List<GroupDraft.HeatDraft> toInsert = new ArrayList<>();
        for (GroupDraft.HeatDraft heatDraft : newHeats) {
            HeatVO existing = existingByNo.get(heatDraft.getHeatNo());
            if (existing != null) {
                heatWriter.clearHeatContent(existing.getId());
                reusedHeatIds.add(existing.getId());
                heatDraft.setExistingHeatId(existing.getId());
            } else {
                toInsert.add(heatDraft);
            }
        }
        // 复用组：直接写入成员与道次
        if (!reusedHeatIds.isEmpty()) {
            GroupDraft reuseDraft = new GroupDraft();
            reuseDraft.setScheduleId(draft.getScheduleId());
            reuseDraft.setEventId(draft.getEventId());
            reuseDraft.setTrack(draft.isTrack());
            reuseDraft.setDrawMode(draft.getDrawMode());
            reuseDraft.setLaneMode(draft.getLaneMode());
            reuseDraft.setLaneCount(draft.getLaneCount());
            reuseDraft.setLaneOrder(draft.getLaneOrder());
            reuseDraft.getHeats().addAll(newHeats.stream()
                    .filter(heat -> heat.getExistingHeatId() != null)
                    .collect(Collectors.toList()));
            heatWriter.writeInto(schedule, reuseDraft, reusedHeatIds, batchNo);
        }
        // 新增组：整批插入（含成员与道次）
        if (!toInsert.isEmpty()) {
            GroupDraft insertDraft = new GroupDraft();
            insertDraft.setScheduleId(draft.getScheduleId());
            insertDraft.setEventId(draft.getEventId());
            insertDraft.setTrack(draft.isTrack());
            insertDraft.setDrawMode(draft.getDrawMode());
            insertDraft.setLaneMode(draft.getLaneMode());
            insertDraft.setLaneCount(draft.getLaneCount());
            insertDraft.setLaneOrder(draft.getLaneOrder());
            insertDraft.getHeats().addAll(toInsert);
            heatWriter.write(schedule, insertDraft, batchNo, callAheadMin, rule);
        }
        // 收缩清理：草案未保留的既有未锁定分组整组删除，避免残留旧成员
        Set<Integer> keepHeatNos = draft.getHeats().stream()
                .map(GroupDraft.HeatDraft::getHeatNo)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<Long> staleHeatIds = existingHeats.stream()
                .filter(heat -> heat.getIsLocked() == null || heat.getIsLocked() != Heat.LOCKED)
                .filter(heat -> heat.getHeatNo() == null || !keepHeatNos.contains(heat.getHeatNo()))
                .map(HeatVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!staleHeatIds.isEmpty()) {
            // 逻辑删除会残留 uk_heat_schedule_no / uk_hm_heat_order 唯一键占位行，故先清子表再逐条物理删除
            for (Long staleHeatId : staleHeatIds) {
                heatWriter.clearHeatContent(staleHeatId);
            }
            for (Long staleHeatId : staleHeatIds) {
                heatWriter.deleteHeatPhysically(staleHeatId);
            }
        }
        // 锁定组同步时间窗与统计（成员保持原样）
        if (draft.getLockedHeatCount() > 0) {
            GroupDraft lockedDraft = new GroupDraft();
            lockedDraft.setScheduleId(draft.getScheduleId());
            lockedDraft.setEventId(draft.getEventId());
            lockedDraft.setTrack(draft.isTrack());
            lockedDraft.setDrawMode(draft.getDrawMode());
            lockedDraft.setLaneCount(draft.getLaneCount());
            lockedDraft.setLaneOrder(draft.getLaneOrder());
            lockedDraft.getHeats().addAll(draft.getHeats().stream()
                    .filter(GroupDraft.HeatDraft::isLocked)
                    .collect(Collectors.toList()));
            heatWriter.write(schedule, lockedDraft, batchNo, callAheadMin, rule);
        }
        // 日程组数回填
        long heatCount = this.count(new LambdaQueryWrapper<Heat>().eq(Heat::getScheduleId, schedule.getId()));
        Schedule patch = new Schedule();
        patch.setId(schedule.getId());
        patch.setHeatCount((int) heatCount);
        scheduleService.updateById(patch);
    }

    /**
     * 预览分组（不落库）：按草案组装视图对象，成员不带主键。
     */
    private List<HeatVO> previewHeats(GroupDraft draft, List<HeatVO> existingHeats) {
        Map<Integer, HeatVO> existingByNo = existingHeats.stream()
                .filter(heat -> heat.getHeatNo() != null)
                .collect(Collectors.toMap(HeatVO::getHeatNo, heat -> heat, (first, second) -> first,
                        LinkedHashMap::new));
        List<HeatVO> heats = new ArrayList<>();
        for (GroupDraft.HeatDraft heatDraft : draft.getHeats()) {
            if (heatDraft.isLocked()) {
                HeatVO existing = existingByNo.get(heatDraft.getHeatNo());
                if (existing != null) {
                    heats.add(existing);
                }
                continue;
            }
            HeatVO heat = new HeatVO();
            heat.setScheduleId(draft.getScheduleId());
            heat.setHeatNo(heatDraft.getHeatNo());
            heat.setLaneCount(heatDraft.getLaneCount());
            heat.setMemberCount(heatDraft.getMembers().size());
            heat.setSeedCount(heatDraft.getSeedCount());
            List<HeatMemberVO> members = new ArrayList<>();
            int orderNo = 1;
            for (GroupDraft.MemberDraft memberDraft : heatDraft.getMembers()) {
                HeatMemberVO member = new HeatMemberVO();
                member.setHeatId(heat.getId());
                member.setRegistrationId(memberDraft.getRegistrationId());
                member.setAthleteId(memberDraft.getAthleteId());
                member.setUnitId(memberDraft.getUnitId());
                member.setBibNo(memberDraft.getBibNo());
                member.setGroupId(memberDraft.getGroupId());
                member.setOrderNo(orderNo++);
                member.setIsSeed(memberDraft.getIsSeed());
                member.setSeedResult(memberDraft.getSeedResult());
                member.setSeedResultRaw(memberDraft.getSeedResultRaw());
                member.setSeedResultUnit(memberDraft.getSeedResultUnit());
                members.add(member);
            }
            heat.setMembers(members);
            heat.setLanes(new ArrayList<>());
            heat.setFieldOrders(new ArrayList<>());
            heats.add(heat);
        }
        heats.sort(Comparator.comparingInt(heat -> heat.getHeatNo() == null ? Integer.MAX_VALUE : heat.getHeatNo()));
        return heats;
    }

    /**
     * 分组 + 组内人员 + 道次 + 田赛出场顺序（批量装配，避免 N+1）。
     */
    private List<HeatVO> loadHeatsWithDetails(Long meetId, Long scheduleId) {
        List<HeatVO> heats = dataProvider.listHeats(meetId, scheduleId);
        if (heats.isEmpty()) {
            return heats;
        }
        List<Long> heatIds = dataProvider.heatIdsOf(heats);
        Map<Long, List<HeatMemberVO>> membersByHeat = dataProvider.listMembers(meetId, heatIds).stream()
                .collect(Collectors.groupingBy(HeatMemberVO::getHeatId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<LaneVO>> lanesByHeat = dataProvider.listLanes(meetId, heatIds).stream()
                .collect(Collectors.groupingBy(LaneVO::getHeatId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<FieldOrderVO>> ordersByHeat =
                dataProvider.listFieldOrders(meetId, heatIds).stream()
                        .collect(Collectors.groupingBy(FieldOrderVO::getHeatId,
                                LinkedHashMap::new, Collectors.toList()));
        for (HeatVO heat : heats) {
            heat.setMembers(membersByHeat.getOrDefault(heat.getId(), new ArrayList<>()));
            heat.setLanes(lanesByHeat.getOrDefault(heat.getId(), new ArrayList<>()));
            heat.setFieldOrders(ordersByHeat.getOrDefault(heat.getId(), new ArrayList<>()));
        }
        heats.sort(Comparator.comparingInt(heat -> heat.getHeatNo() == null ? Integer.MAX_VALUE : heat.getHeatNo()));
        return heats;
    }

    private String buildBatchNo() {
        return "HEAT" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
