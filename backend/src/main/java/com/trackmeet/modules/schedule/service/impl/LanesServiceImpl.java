package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.engine.lane.LaneAllocator;
import com.trackmeet.modules.schedule.dto.LaneAssignDTO;
import com.trackmeet.modules.schedule.dto.LaneSwapDTO;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.entity.Lane;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.mapper.HeatMemberMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.LaneMapper;
import com.trackmeet.modules.schedule.service.LanesService;
import com.trackmeet.modules.schedule.service.ScheduleService;
import com.trackmeet.modules.schedule.service.support.CachedScheduleData;
import com.trackmeet.modules.schedule.service.support.HeatWriter;
import com.trackmeet.modules.schedule.service.support.JsonTextUtils;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.service.support.ScheduleDataProvider;
import com.trackmeet.modules.schedule.vo.LaneVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 道次服务实现。
 * <p>
 * 关键约束：{@code lane} 表存在唯一键 {@code uk_lane_heat_no(heat_id, lane_no)}，
 * 因此「交换道次」不能直接互换两个 {@code lane_no}，需要先把两条记录搬到 +1000 的临时号段再回写，
 * 否则第二句 UPDATE 会撞唯一键。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class LanesServiceImpl extends ServiceImpl<LaneMapper, Lane> implements LanesService {

    /** 道次腾挪的临时偏移量（唯一键避让） */
    private static final int SWAP_OFFSET = 1000;

    private final HeatWriter heatWriter;
    private final ScheduleDataProvider dataProvider;
    private final CachedScheduleData cachedData;
    private final HeatMemberMapper heatMemberMapper;
    private final HeatMapper heatMapper;
    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LaneVO> assign(LaneAssignDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "道次分配参数不能为空");
        }
        List<Long> heatIds = dataProvider.distinctIds(dto.getHeatIds());
        if (heatIds.isEmpty()) {
            if (dto.getScheduleId() == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "分组列表与日程条目不能同时为空");
            }
            Schedule schedule = scheduleService.getEntity(dto.getScheduleId());
            heatIds = loadHeatIds(schedule.getMeetId(), schedule.getId());
        }
        if (heatIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<LaneVO> result = new ArrayList<>();
        Map<Long, Schedule> scheduleCache = new HashMap<>();
        for (Long heatId : heatIds) {
            Heat heat = heatMapper.selectById(heatId);
            if (heat == null) {
                continue;
            }
            MeetGuard.check(heat.getMeetId());
            Schedule schedule = scheduleCache.computeIfAbsent(heat.getScheduleId(),
                    key -> scheduleService.getEntity(key));
            int laneCount = dto.getLaneCount() != null && dto.getLaneCount() > 0
                    ? dto.getLaneCount()
                    : cachedData.laneCount(schedule.getEventId(), schedule.getVenueId());
            List<Integer> laneOrder = JsonTextUtils.parseIntArray(objectMapper,
                    cachedData.laneOrderJson(schedule.getEventId(), schedule.getVenueId()));
            if (laneOrder.isEmpty()) {
                laneOrder = LaneAllocator.defaultLaneOrder(laneCount);
            }
            String mode = StringUtils.hasText(dto.getMode()) ? dto.getMode().trim().toUpperCase() : Lane.MODE_SEED;
            List<HeatMember> members = heatMemberMapper.selectList(new LambdaQueryWrapper<HeatMember>()
                    .eq(HeatMember::getHeatId, heatId)
                    .orderByAsc(HeatMember::getOrderNo)
                    .orderByAsc(HeatMember::getId));
            // 道次只分配给可容纳的名额，超出者为替补（无道次）
            heatWriter.assignLanesForHeat(schedule, heat, members, mode, laneCount, laneOrder,
                    heat.getBatchNo(), laneCount);
            result.addAll(dataProvider.listLanes(heat.getMeetId(),
                    java.util.Collections.singletonList(heatId)));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLaneNo(Long id, Integer laneNo) {
        Lane lane = getEntity(id);
        if (laneNo == null || laneNo <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "道次号必须大于 0");
        }
        Long occupied = this.count(new LambdaQueryWrapper<Lane>()
                .eq(Lane::getHeatId, lane.getHeatId())
                .eq(Lane::getLaneNo, laneNo)
                .ne(Lane::getId, id));
        if (occupied != null && occupied > 0) {
            throw new BizException(ResultCode.CONFLICT, "第 " + laneNo + " 道已被占用，请先交换或调整该道次");
        }
        Lane patch = new Lane();
        patch.setId(id);
        patch.setLaneNo(laneNo);
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "道次已被其他用户修改，请刷新后重试");
        }
        syncMemberLaneNo(lane, laneNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void swap(LaneSwapDTO dto) {
        if (dto == null || dto.getLaneIdA() == null || dto.getLaneIdB() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "待交换的两条道次不能为空");
        }
        if (Objects.equals(dto.getLaneIdA(), dto.getLaneIdB())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能与自身交换道次");
        }
        Lane first = getEntity(dto.getLaneIdA());
        Lane second = getEntity(dto.getLaneIdB());
        if (!Objects.equals(first.getHeatId(), second.getHeatId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "只能在同一个分组内交换道次");
        }
        int firstNo = first.getLaneNo() == null ? 0 : first.getLaneNo();
        int secondNo = second.getLaneNo() == null ? 0 : second.getLaneNo();
        String swapGroup = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityUtils.getUserId();
        // 先把两条记录搬到临时号段，再互换，避免 uk_lane_heat_no 冲突
        setLaneNo(first.getId(), firstNo + SWAP_OFFSET);
        setLaneNo(second.getId(), secondNo + SWAP_OFFSET);
        applySwapTrace(first.getId(), firstNo, secondNo, swapGroup, dto.getSwapReason(), now, userId);
        applySwapTrace(second.getId(), secondNo, firstNo, swapGroup, dto.getSwapReason(), now, userId);
        syncMemberLaneNo(first, secondNo);
        syncMemberLaneNo(second, firstNo);
    }

    @Override
    public Lane getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "道次 ID 不能为空");
        }
        Lane lane = this.getById(id);
        if (lane == null) {
            throw new BizException(ResultCode.NOT_FOUND, "道次记录不存在");
        }
        MeetGuard.check(lane.getMeetId());
        return lane;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private List<Long> loadHeatIds(Long meetId, Long scheduleId) {
        return heatMapper.selectList(new LambdaQueryWrapper<Heat>()
                        .eq(Heat::getMeetId, meetId)
                        .eq(Heat::getScheduleId, scheduleId)
                        .orderByAsc(Heat::getHeatNo))
                .stream()
                .map(Heat::getId)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private void setLaneNo(Long laneId, int laneNo) {
        LambdaUpdateWrapper<Lane> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Lane::getId, laneId).set(Lane::getLaneNo, laneNo);
        this.update(wrapper);
    }

    private void applySwapTrace(Long laneId, Integer sourceLaneNo, Integer targetLaneNo, String swapGroup,
                                String reason, LocalDateTime now, Long userId) {
        LambdaUpdateWrapper<Lane> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Lane::getId, laneId)
                .set(Lane::getLaneNo, targetLaneNo)
                .set(Lane::getSwapGroup, swapGroup)
                .set(Lane::getSwapFromLane, sourceLaneNo)
                .set(Lane::getSwapReason, reason)
                .set(Lane::getSwapTime, now)
                .set(Lane::getSwapBy, userId);
        this.update(wrapper);
    }

    /**
     * 同步 {@code heat_member.lane_no} 冗余列，保证检录表与分道表口径一致。
     */
    private void syncMemberLaneNo(Lane lane, Integer laneNo) {
        if (lane.getHeatMemberId() == null) {
            return;
        }
        LambdaUpdateWrapper<HeatMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HeatMember::getId, lane.getHeatMemberId()).set(HeatMember::getLaneNo, laneNo);
        heatMemberMapper.update(wrapper);
    }
}
