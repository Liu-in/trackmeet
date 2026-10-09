package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.engine.fieldorder.FieldOrderGenerator;
import com.trackmeet.engine.model.AthleteEntry;
import com.trackmeet.engine.model.FieldOrderMode;
import com.trackmeet.modules.schedule.dto.FieldOrderGenerateDTO;
import com.trackmeet.modules.schedule.dto.FieldOrderReorderDTO;
import com.trackmeet.modules.schedule.entity.FieldOrder;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.mapper.FieldOrderMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.service.FieldOrdersService;
import com.trackmeet.modules.schedule.service.ScheduleService;
import com.trackmeet.modules.schedule.service.support.CachedScheduleData;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.service.support.OrderResequencer;
import com.trackmeet.modules.schedule.service.support.ScheduleDataProvider;
import com.trackmeet.modules.schedule.vo.FieldOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 田赛出场顺序服务实现。
 * <p>
 * 生成策略：按日程 → 分组 → 组内人员的层级，逐组调用
 * {@link FieldOrderGenerator#generate}，结果列表顺序即出场顺序（{@code order_no} 从 1 开始），
 * 并把报名最好成绩写入 {@code best_seed}。田赛成绩「越大越好」→ {@code seedAscending=false}。
 * <p>
 * 锁定保护：{@code field_order.is_locked=1} 的记录不重建。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class FieldOrdersServiceImpl extends ServiceImpl<FieldOrderMapper, FieldOrder> implements FieldOrdersService {

    /** 批次号前缀 */
    private static final String BATCH_PREFIX = "FIELD";

    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 缺省随机种子 */
    private static final long DEFAULT_RANDOM_SEED = 20250101L;

    private final ScheduleService scheduleService;
    private final ScheduleDataProvider dataProvider;
    private final CachedScheduleData cachedData;
    private final HeatMapper heatMapper;
    private final HeatMemberMapper heatMemberMapper;
    private final FieldOrderMapper fieldOrderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FieldOrderVO> generate(FieldOrderGenerateDTO dto) {
        if (dto == null || dto.getScheduleId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "日程条目不能为空");
        }
        Schedule schedule = scheduleService.getEntity(dto.getScheduleId());
        Long meetId = schedule.getMeetId();
        cachedData.load(meetId, null, null, false);
        FieldOrderMode mode = resolveMode(dto.getMode());
        boolean seedAscending = isTrack(schedule.getEventId());
        long randomSeed = dto.getRandomSeed() == null ? DEFAULT_RANDOM_SEED : dto.getRandomSeed();
        boolean apply = dto.getApply() == null || Boolean.TRUE.equals(dto.getApply());

        List<Heat> heats = heatMapper.selectList(new LambdaQueryWrapper<Heat>()
                .eq(Heat::getMeetId, meetId)
                .eq(Heat::getScheduleId, schedule.getId())
                .orderByAsc(Heat::getHeatNo));
        if (heats.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "该日程尚未生成分组，请先执行分组分道");
        }
        List<Long> heatIds = heats.stream().map(Heat::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<HeatMember> members = heatMemberMapper.selectList(new LambdaQueryWrapper<HeatMember>()
                .in(HeatMember::getHeatId, heatIds)
                .orderByAsc(HeatMember::getHeatId)
                .orderByAsc(HeatMember::getOrderNo)
                .orderByAsc(HeatMember::getId));
        if (members.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "该日程分组内暂无参赛人员");
        }
        Map<Long, List<HeatMember>> membersByHeat = members.stream()
                .collect(Collectors.groupingBy(HeatMember::getHeatId, LinkedHashMap::new, Collectors.toList()));

        List<FieldOrderVO> preview = new ArrayList<>();
        if (!apply) {
            for (Heat heat : heats) {
                List<HeatMember> heatMembers = membersByHeat.getOrDefault(heat.getId(), new ArrayList<>());
                List<AthleteEntry> entries = toEntries(heatMembers);
                List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, mode, seedAscending, randomSeed);
                preview.addAll(toPreview(ordered, heatMembers, schedule, heat, mode));
            }
            return preview;
        }

        String batchNo = BATCH_PREFIX + LocalDateTime.now().format(BATCH_FORMAT);
        for (Heat heat : heats) {
            List<HeatMember> heatMembers = membersByHeat.getOrDefault(heat.getId(), new ArrayList<>());
            if (heatMembers.isEmpty()) {
                continue;
            }
            // 锁定保护：锁定的出场顺序记录不重建
            List<FieldOrder> locked = this.list(new LambdaQueryWrapper<FieldOrder>()
                    .eq(FieldOrder::getHeatId, heat.getId())
                    .eq(FieldOrder::getIsLocked, FieldOrder.LOCKED));
            if (!locked.isEmpty()) {
                continue;
            }
            // 物理删除重建（逻辑删除会残留 uk_forder_heat_member 唯一键占位行）
            fieldOrderMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heat.getId()));
            List<HeatMember> orderedMembers = orderMembers(heatMembers, mode, seedAscending, randomSeed);
            int orderNo = 1;
            for (HeatMember member : orderedMembers) {
                FieldOrder order = new FieldOrder();
                order.setMeetId(meetId);
                order.setHeatId(heat.getId());
                order.setHeatMemberId(member.getId());
                order.setScheduleId(schedule.getId());
                order.setEventId(schedule.getEventId());
                order.setOrderNo(orderNo++);
                order.setOrderMode(mode.name());
                order.setBestSeed(member.getSeedResult());
                order.setBestSeedRaw(member.getSeedResultRaw());
                order.setBestSeedUnit(member.getSeedResultUnit());
                order.setPassCount(0);
                order.setIsScratch(0);
                order.setIsPass(0);
                order.setIsLocked(FieldOrder.UNLOCKED);
                order.setBatchNo(batchNo);
                this.save(order);
            }
        }
        return dataProvider.listFieldOrders(meetId, heatIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorder(FieldOrderReorderDTO dto) {
        if (dto == null || dto.getHeatId() == null || dto.getMemberIds() == null || dto.getMemberIds().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "分组与顺序列表不能为空");
        }
        Heat heat = heatMapper.selectById(dto.getHeatId());
        if (heat == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分组不存在");
        }
        MeetGuard.check(heat.getMeetId());
        List<FieldOrder> orders = this.list(new LambdaQueryWrapper<FieldOrder>()
                .eq(FieldOrder::getHeatId, heat.getId()));
        Map<Long, FieldOrder> byMember = orders.stream()
                .filter(order -> order.getHeatMemberId() != null)
                .collect(Collectors.toMap(FieldOrder::getHeatMemberId, order -> order, (first, second) -> first));
        Map<Long, Integer> targetOrder = new HashMap<>();
        int cursor = 1;
        for (Long memberId : dto.getMemberIds()) {
            if (memberId != null && byMember.containsKey(memberId) && !targetOrder.containsKey(memberId)) {
                targetOrder.put(memberId, cursor++);
            }
        }
        if (targetOrder.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "顺序列表中没有任何该分组的出场记录");
        }
        // 落单的记录（未出现在列表中）追加到末尾，保证顺序号连续
        for (FieldOrder order : orders) {
            if (order.getHeatMemberId() != null && !targetOrder.containsKey(order.getHeatMemberId())) {
                targetOrder.put(order.getHeatMemberId(), cursor++);
            }
        }
        List<FieldOrder> ordered = new ArrayList<>(orders);
        ordered.sort(Comparator.comparingInt(order ->
                targetOrder.getOrDefault(order.getHeatMemberId(), Integer.MAX_VALUE)));
        OrderResequencer.resequence(ordered, FieldOrder::getId, (id, orderNo) -> {
            LambdaUpdateWrapper<FieldOrder> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(FieldOrder::getId, id).set(FieldOrder::getOrderNo, orderNo);
            this.update(wrapper);
        });
        // 同步组内人员的出发顺序，保证分组表与出场顺序一致
        List<HeatMember> members = heatMemberMapper.selectList(new LambdaQueryWrapper<HeatMember>()
                .eq(HeatMember::getHeatId, heat.getId()));
        List<HeatMember> orderedMembers = new ArrayList<>(members);
        orderedMembers.sort(Comparator.comparingInt(member ->
                targetOrder.getOrDefault(member.getId(), Integer.MAX_VALUE)));
        OrderResequencer.resequence(orderedMembers, HeatMember::getId, (id, orderNo) -> {
            LambdaUpdateWrapper<HeatMember> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(HeatMember::getId, id).set(HeatMember::getOrderNo, orderNo);
            heatMemberMapper.update(wrapper);
        });
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private FieldOrderMode resolveMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return FieldOrderMode.SEED;
        }
        try {
            return FieldOrderMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的出场顺序方式：" + mode
                    + "，可选值：RANDOM/SEED/UNIT/BIB");
        }
    }

    /**
     * 径赛型项目（成绩越小越好）与田赛型（越大越好）决定排序方向。
     */
    private boolean isTrack(Long eventId) {
        if (eventId == null) {
            return false;
        }
        String type = cachedData.eventType(eventId);
        if (type == null) {
            return false;
        }
        return "TRACK".equalsIgnoreCase(type) || "RELAY".equalsIgnoreCase(type);
    }

    private List<AthleteEntry> toEntries(List<HeatMember> members) {
        List<AthleteEntry> entries = new ArrayList<>();
        for (HeatMember member : members) {
            entries.add(AthleteEntry.builder()
                    .registrationId(member.getRegistrationId())
                    .athleteId(member.getAthleteId())
                    .bibNo(member.getBibNo())
                    .unitId(member.getUnitId())
                    .seedValue(member.getSeedResult())
                    .seedResult(member.getSeedResultRaw())
                    .build());
        }
        return entries;
    }

    private List<HeatMember> orderMembers(List<HeatMember> members, FieldOrderMode mode, boolean seedAscending,
                                          long randomSeed) {
        List<AthleteEntry> entries = toEntries(members);
        List<AthleteEntry> ordered = FieldOrderGenerator.generate(entries, mode, seedAscending, randomSeed);
        Map<Long, HeatMember> byRegistration = members.stream()
                .filter(member -> member.getRegistrationId() != null)
                .collect(Collectors.toMap(HeatMember::getRegistrationId, member -> member, (first, second) -> first));
        List<HeatMember> orderedMembers = new ArrayList<>();
        for (AthleteEntry entry : ordered) {
            HeatMember member = byRegistration.get(entry.getRegistrationId());
            if (member != null) {
                orderedMembers.add(member);
            }
        }
        return orderedMembers;
    }

    private List<FieldOrderVO> toPreview(List<AthleteEntry> ordered, List<HeatMember> members, Schedule schedule,
                                         Heat heat, FieldOrderMode mode) {
        Map<Long, HeatMember> byRegistration = members.stream()
                .filter(member -> member.getRegistrationId() != null)
                .collect(Collectors.toMap(HeatMember::getRegistrationId, member -> member, (first, second) -> first));
        List<FieldOrderVO> result = new ArrayList<>();
        int orderNo = 1;
        for (AthleteEntry entry : ordered) {
            HeatMember member = byRegistration.get(entry.getRegistrationId());
            if (member == null) {
                continue;
            }
            FieldOrderVO vo = new FieldOrderVO();
            vo.setMeetId(schedule.getMeetId());
            vo.setHeatId(heat.getId());
            vo.setHeatMemberId(member.getId());
            vo.setScheduleId(schedule.getId());
            vo.setEventId(schedule.getEventId());
            vo.setOrderNo(orderNo++);
            vo.setOrderMode(mode.name());
            vo.setBestSeed(member.getSeedResult());
            vo.setBestSeedRaw(member.getSeedResultRaw());
            vo.setBestSeedUnit(member.getSeedResultUnit());
            vo.setIsScratch(0);
            vo.setIsPass(0);
            vo.setIsLocked(FieldOrder.UNLOCKED);
            vo.setHeatNo(heat.getHeatNo());
            vo.setRegistrationId(member.getRegistrationId());
            vo.setAthleteId(member.getAthleteId());
            vo.setBibNo(member.getBibNo());
            vo.setUnitId(member.getUnitId());
            result.add(vo);
        }
        return result;
    }
}
