package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.schedule.dto.SnapshotSaveDTO;
import com.trackmeet.modules.schedule.entity.ConflictLog;
import com.trackmeet.modules.schedule.entity.FieldOrder;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.entity.Lane;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.entity.ScheduleSnapshot;
import com.trackmeet.modules.schedule.mapper.ConflictLogMapper;
import com.trackmeet.modules.schedule.mapper.FieldOrderMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.mapper.LaneMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleSnapshotMapper;
import com.trackmeet.modules.schedule.service.SnapshotsService;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.service.support.SnapshotHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 编排快照服务实现。
 * <p>
 * 保存：把当届 {@code schedule + heat + heat_member + lane + field_order} 序列化为 JSON 存
 * {@code schedule_snapshot.snapshot_json}，并统计条目/分组/人员/锁定/冲突数量。
 * <p>
 * 回滚（{@link #restore}）：
 * <ol>
 *   <li>清理当届<b>未锁定</b>编排数据（锁定的日程与其分组/成员/道次/出场顺序保持不动）；</li>
 *   <li>按快照重建：日程 → 分组 → 组内人员 → 道次 → 出场顺序，逐层建立「旧主键 → 新主键」映射
 *       后回填外键，保证快照内的引用关系不丢失；</li>
 *   <li>回滚动作本身也留一次 {@code RESTORE} 快照留痕，便于二次回退（与「快照回滚会覆盖当前日程」的
 *       已知边界一致，恢复前应先手动保存）。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class SnapshotsServiceImpl extends ServiceImpl<ScheduleSnapshotMapper, ScheduleSnapshot>
        implements SnapshotsService {

    /** 清理顺序号的临时偏移量（唯一键避让） */
    private static final int ORDER_OFFSET = 1000;

    private final ScheduleMapper scheduleMapper;
    private final HeatMapper heatMapper;
    private final HeatMemberMapper heatMemberMapper;
    private final LaneMapper laneMapper;
    private final FieldOrderMapper fieldOrderMapper;
    private final ConflictLogMapper conflictLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSnapshot(SnapshotSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSnapshotName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "快照名称不能为空");
        }
        Long meetId = MeetGuard.resolve(dto.getMeetId());

        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMeetId, meetId)
                .orderByAsc(Schedule::getPlanStartTime)
                .orderByAsc(Schedule::getId));
        Set<Long> scheduleIds = schedules.stream().map(Schedule::getId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Heat> heats = scheduleIds.isEmpty() ? new ArrayList<>() : heatMapper.selectList(
                new LambdaQueryWrapper<Heat>().eq(Heat::getMeetId, meetId)
                        .in(Heat::getScheduleId, scheduleIds)
                        .orderByAsc(Heat::getScheduleId).orderByAsc(Heat::getHeatNo));
        Set<Long> heatIds = heats.stream().map(Heat::getId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<HeatMember> members = heatIds.isEmpty() ? new ArrayList<>() : heatMemberMapper.selectList(
                new LambdaQueryWrapper<HeatMember>().eq(HeatMember::getMeetId, meetId)
                        .in(HeatMember::getHeatId, heatIds)
                        .orderByAsc(HeatMember::getHeatId).orderByAsc(HeatMember::getOrderNo));
        List<Lane> lanes = heatIds.isEmpty() ? new ArrayList<>() : laneMapper.selectList(
                new LambdaQueryWrapper<Lane>().eq(Lane::getMeetId, meetId)
                        .in(Lane::getHeatId, heatIds)
                        .orderByAsc(Lane::getHeatId).orderByAsc(Lane::getLaneNo));
        List<FieldOrder> fieldOrders = heatIds.isEmpty() ? new ArrayList<>() : fieldOrderMapper.selectList(
                new LambdaQueryWrapper<FieldOrder>().eq(FieldOrder::getMeetId, meetId)
                        .in(FieldOrder::getHeatId, heatIds)
                        .orderByAsc(FieldOrder::getHeatId).orderByAsc(FieldOrder::getOrderNo));

        SnapshotHolder holder = new SnapshotHolder();
        holder.setMeetId(meetId);
        holder.setSnapshotType(StringUtils.hasText(dto.getSnapshotType())
                ? dto.getSnapshotType() : ScheduleSnapshot.TYPE_MANUAL);
        holder.setScopeType(StringUtils.hasText(dto.getScopeType())
                ? dto.getScopeType() : ScheduleSnapshot.SCOPE_ALL);
        holder.setBatchNo(dto.getBatchNo());
        holder.setSchedules(schedules);
        holder.setHeats(heats);
        holder.setMembers(members);
        holder.setLanes(lanes);
        holder.setFieldOrders(fieldOrders);

        ScheduleSnapshot snapshot = new ScheduleSnapshot();
        snapshot.setMeetId(meetId);
        snapshot.setSnapshotName(dto.getSnapshotName().trim());
        snapshot.setSnapshotType(holder.getSnapshotType());
        snapshot.setScopeType(holder.getScopeType());
        snapshot.setBatchNo(resolveBatchNo(dto, schedules));
        snapshot.setSnapshotJson(writeJson(holder));
        snapshot.setItemCount(schedules.size());
        snapshot.setHeatCount(heats.size());
        snapshot.setMemberCount(members.size());
        snapshot.setLockedCount((int) schedules.stream()
                .filter(schedule -> schedule.getIsLocked() != null && schedule.getIsLocked() == Schedule.LOCKED)
                .count());
        snapshot.setConflictCount(countUnresolvedConflicts(meetId));
        snapshot.setIsCurrent(ScheduleSnapshot.CURRENT);
        snapshot.setOperatorId(SecurityUtils.getUserId());
        snapshot.setRemark(dto.getRemark());
        this.save(snapshot);

        // 同届仅保留一个当前生效版本
        LambdaUpdateWrapper<ScheduleSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ScheduleSnapshot::getMeetId, meetId)
                .ne(ScheduleSnapshot::getId, snapshot.getId())
                .set(ScheduleSnapshot::getIsCurrent, ScheduleSnapshot.NOT_CURRENT);
        this.update(wrapper);
        return snapshot.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "快照 ID 不能为空");
        }
        ScheduleSnapshot snapshot = this.getById(id);
        if (snapshot == null) {
            throw new BizException(ResultCode.NOT_FOUND, "快照不存在");
        }
        MeetGuard.check(snapshot.getMeetId());
        Long meetId = snapshot.getMeetId();
        SnapshotHolder holder = readJson(snapshot.getSnapshotJson());
        if (holder == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "快照内容为空或格式不正确，无法回滚");
        }

        // 回滚前留一次备份快照，便于二次回退
        SnapshotSaveDTO backup = new SnapshotSaveDTO();
        backup.setMeetId(meetId);
        backup.setSnapshotName("回滚前备份 " + LocalDateTime.now());
        backup.setSnapshotType(ScheduleSnapshot.TYPE_RESTORE);
        backup.setScopeType(snapshot.getScopeType());
        saveSnapshot(backup);

        clearUnlockedData(meetId);
        restoreData(meetId, holder);

        LambdaUpdateWrapper<ScheduleSnapshot> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ScheduleSnapshot::getId, id)
                .set(ScheduleSnapshot::getRestoreTime, LocalDateTime.now())
                .set(ScheduleSnapshot::getRestoreBy, SecurityUtils.getUserId())
                .set(ScheduleSnapshot::getIsCurrent, ScheduleSnapshot.CURRENT);
        this.update(wrapper);
        LambdaUpdateWrapper<ScheduleSnapshot> others = new LambdaUpdateWrapper<>();
        others.eq(ScheduleSnapshot::getMeetId, meetId)
                .ne(ScheduleSnapshot::getId, id)
                .set(ScheduleSnapshot::getIsCurrent, ScheduleSnapshot.NOT_CURRENT);
        this.update(others);
    }

    // ------------------------------------------------------------------
    // 清理与重建
    // ------------------------------------------------------------------

    /**
     * 清理当届未锁定编排数据：锁定日程及其分组/成员/道次/出场顺序保持不动。
     */
    private void clearUnlockedData(Long meetId) {
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMeetId, meetId));
        Set<Long> removableHeatIds = new LinkedHashSet<>();
        for (Schedule schedule : schedules) {
            boolean locked = schedule.getIsLocked() != null && schedule.getIsLocked() == Schedule.LOCKED;
            if (locked) {
                // 锁定日程：仅清理其未锁定分组的成员与道次
                List<Heat> heats = heatMapper.selectList(new LambdaQueryWrapper<Heat>()
                        .eq(Heat::getMeetId, meetId)
                        .eq(Heat::getScheduleId, schedule.getId()));
                for (Heat heat : heats) {
                    if (heat.getIsLocked() != null && heat.getIsLocked() == Heat.LOCKED) {
                        continue;
                    }
                    clearHeatContent(heat.getId());
                    removableHeatIds.add(heat.getId());
                }
                continue;
            }
            List<Heat> heats = heatMapper.selectList(new LambdaQueryWrapper<Heat>()
                    .eq(Heat::getMeetId, meetId)
                    .eq(Heat::getScheduleId, schedule.getId()));
            for (Heat heat : heats) {
                clearHeatContent(heat.getId());
                removableHeatIds.add(heat.getId());
            }
            scheduleMapper.deleteByIdPhysically(schedule.getId());
        }
        for (Long removableHeatId : removableHeatIds) {
            // 物理删除分组：逻辑删除会残留 uk_heat_schedule_no 占位行，回滚重建时同组号无法插入
            heatMapper.deleteByIdPhysically(removableHeatId);
        }
    }

    private void clearHeatContent(Long heatId) {
        heatMemberMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heatId));
        laneMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heatId));
        fieldOrderMapper.deleteByHeatIdsPhysically(java.util.Collections.singletonList(heatId));
    }

    /**
     * 按快照重建数据，逐层建立主键映射并回填外键。
     */
    private void restoreData(Long meetId, SnapshotHolder holder) {
        Map<Long, Long> scheduleIdMap = new HashMap<>();
        for (Schedule source : nullSafe(holder.getSchedules())) {
            Schedule entity = copySchedule(source, meetId);
            scheduleMapper.insert(entity);
            if (source.getId() != null && entity.getId() != null) {
                scheduleIdMap.put(source.getId(), entity.getId());
            }
        }

        Map<Long, Long> heatIdMap = new HashMap<>();
        Map<Long, List<HeatMember>> membersByHeat = new LinkedHashMap<>();
        for (HeatMember member : nullSafe(holder.getMembers())) {
            membersByHeat.computeIfAbsent(member.getHeatId(), key -> new ArrayList<>()).add(member);
        }
        for (Heat source : nullSafe(holder.getHeats())) {
            Heat entity = copyHeat(source, meetId, scheduleIdMap);
            heatMapper.insert(entity);
            if (source.getId() != null && entity.getId() != null) {
                heatIdMap.put(source.getId(), entity.getId());
            }
            List<HeatMember> memberSources = membersByHeat.getOrDefault(source.getId(), new ArrayList<>());
            memberSources.sort(java.util.Comparator.comparingInt(
                    member -> member.getOrderNo() == null ? Integer.MAX_VALUE : member.getOrderNo()));
            int orderNo = 1;
            for (HeatMember memberSource : memberSources) {
                HeatMember member = copyMember(memberSource, meetId, entity);
                member.setOrderNo(orderNo++);
                heatMemberMapper.insert(member);
                // 回填道次
                for (Lane laneSource : nullSafe(holder.getLanes())) {
                    if (!Objects.equals(laneSource.getHeatMemberId(), memberSource.getId())) {
                        continue;
                    }
                    Lane lane = copyLane(laneSource, meetId, entity, member);
                    laneMapper.insert(lane);
                }
                // 回填出场顺序
                for (FieldOrder orderSource : nullSafe(holder.getFieldOrders())) {
                    if (!Objects.equals(orderSource.getHeatMemberId(), memberSource.getId())) {
                        continue;
                    }
                    FieldOrder order = copyFieldOrder(orderSource, meetId, entity, member);
                    fieldOrderMapper.insert(order);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 拷贝（清空审计字段与乐观锁，交由自动填充）
    // ------------------------------------------------------------------

    private Schedule copySchedule(Schedule source, Long meetId) {
        Schedule entity = new Schedule();
        entity.setMeetId(meetId);
        entity.setSessionId(source.getSessionId());
        entity.setEventId(source.getEventId());
        entity.setRoundId(source.getRoundId());
        entity.setVenueId(source.getVenueId());
        entity.setPlanStartTime(source.getPlanStartTime());
        entity.setPlanEndTime(source.getPlanEndTime());
        entity.setActualStartTime(source.getActualStartTime());
        entity.setActualEndTime(source.getActualEndTime());
        entity.setDurationMin(source.getDurationMin());
        entity.setActualDurationMin(source.getActualDurationMin());
        entity.setHeatCount(source.getHeatCount());
        entity.setMemberCount(source.getMemberCount());
        entity.setOrderNo(source.getOrderNo());
        entity.setSourceType(source.getSourceType());
        entity.setBatchNo(source.getBatchNo());
        entity.setIsLocked(source.getIsLocked());
        entity.setMoveCount(source.getMoveCount());
        entity.setPublishStatus(source.getPublishStatus());
        entity.setConflictFlag(source.getConflictFlag());
        entity.setConflictCount(source.getConflictCount());
        entity.setRemark(source.getRemark());
        return entity;
    }

    private Heat copyHeat(Heat source, Long meetId, Map<Long, Long> scheduleIdMap) {
        Heat entity = new Heat();
        entity.setMeetId(meetId);
        entity.setScheduleId(scheduleIdMap.getOrDefault(source.getScheduleId(), source.getScheduleId()));
        entity.setHeatNo(source.getHeatNo());
        entity.setHeatName(source.getHeatName());
        entity.setLaneCount(source.getLaneCount());
        entity.setMemberCount(source.getMemberCount());
        entity.setSeedCount(source.getSeedCount());
        entity.setCallTime(source.getCallTime());
        entity.setCallPlace(source.getCallPlace());
        entity.setStartTime(source.getStartTime());
        entity.setEndTime(source.getEndTime());
        entity.setStatus(source.getStatus());
        entity.setIsLocked(source.getIsLocked());
        entity.setSourceType(source.getSourceType());
        entity.setBatchNo(source.getBatchNo());
        entity.setDrawMode(source.getDrawMode());
        entity.setRemark(source.getRemark());
        return entity;
    }

    private HeatMember copyMember(HeatMember source, Long meetId, Heat heat) {
        HeatMember entity = new HeatMember();
        entity.setMeetId(meetId);
        entity.setHeatId(heat.getId());
        entity.setScheduleId(heat.getScheduleId());
        entity.setRegistrationId(source.getRegistrationId());
        entity.setAthleteId(source.getAthleteId());
        entity.setUnitId(source.getUnitId());
        entity.setBibNo(source.getBibNo());
        entity.setGroupId(source.getGroupId());
        entity.setIsSeed(source.getIsSeed());
        entity.setIsScratch(source.getIsScratch());
        entity.setStatus(source.getStatus());
        entity.setSeedResult(source.getSeedResult());
        entity.setSeedResultRaw(source.getSeedResultRaw());
        entity.setSeedResultUnit(source.getSeedResultUnit());
        entity.setLaneNo(source.getLaneNo());
        entity.setIsLocked(source.getIsLocked());
        entity.setBatchNo(source.getBatchNo());
        entity.setRemark(source.getRemark());
        return entity;
    }

    private Lane copyLane(Lane source, Long meetId, Heat heat, HeatMember member) {
        Lane entity = new Lane();
        entity.setMeetId(meetId);
        entity.setHeatId(heat.getId());
        entity.setHeatMemberId(member.getId());
        entity.setScheduleId(heat.getScheduleId());
        entity.setLaneNo(source.getLaneNo());
        entity.setAssignMode(source.getAssignMode());
        entity.setLaneOrderSource(source.getLaneOrderSource());
        entity.setLaneOrderJson(source.getLaneOrderJson());
        entity.setSeedRank(source.getSeedRank());
        entity.setIsBestLane(source.getIsBestLane());
        entity.setIsLocked(source.getIsLocked());
        entity.setBatchNo(source.getBatchNo());
        entity.setRemark(source.getRemark());
        return entity;
    }

    private FieldOrder copyFieldOrder(FieldOrder source, Long meetId, Heat heat, HeatMember member) {
        FieldOrder entity = new FieldOrder();
        entity.setMeetId(meetId);
        entity.setHeatId(heat.getId());
        entity.setHeatMemberId(member.getId());
        entity.setScheduleId(heat.getScheduleId());
        entity.setEventId(source.getEventId());
        entity.setOrderNo(source.getOrderNo());
        entity.setGroupNo(source.getGroupNo());
        entity.setOrderMode(source.getOrderMode());
        entity.setBestSeed(source.getBestSeed());
        entity.setBestSeedRaw(source.getBestSeedRaw());
        entity.setBestSeedUnit(source.getBestSeedUnit());
        entity.setStartHeight(source.getStartHeight());
        entity.setPassCount(source.getPassCount());
        entity.setIsScratch(source.getIsScratch());
        entity.setIsPass(source.getIsPass());
        entity.setIsLocked(source.getIsLocked());
        entity.setMoveCount(source.getMoveCount());
        entity.setBatchNo(source.getBatchNo());
        entity.setRemark(source.getRemark());
        return entity;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private String resolveBatchNo(SnapshotSaveDTO dto, List<Schedule> schedules) {
        if (StringUtils.hasText(dto.getBatchNo())) {
            return dto.getBatchNo().trim();
        }
        for (Schedule schedule : schedules) {
            if (StringUtils.hasText(schedule.getBatchNo())) {
                return schedule.getBatchNo();
            }
        }
        return null;
    }

    private int countUnresolvedConflicts(Long meetId) {
        Long count = conflictLogMapper.selectCount(new LambdaQueryWrapper<ConflictLog>()
                .eq(ConflictLog::getMeetId, meetId)
                .eq(ConflictLog::getResolved, ConflictLog.UNRESOLVED));
        return count == null ? 0 : count.intValue();
    }

    private String writeJson(SnapshotHolder holder) {
        try {
            return objectMapper.writeValueAsString(holder);
        } catch (Exception ex) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "快照序列化失败：" + ex.getMessage());
        }
    }

    private SnapshotHolder readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SnapshotHolder.class);
        } catch (Exception ex) {
            throw new BizException(ResultCode.PARAM_ERROR, "快照内容解析失败：" + ex.getMessage());
        }
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
