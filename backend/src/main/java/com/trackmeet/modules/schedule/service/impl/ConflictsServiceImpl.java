package com.trackmeet.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.engine.conflict.ConflictDetector;
import com.trackmeet.engine.model.ConflictItem;
import com.trackmeet.engine.model.ConflictOptions;
import com.trackmeet.engine.model.ScheduledItem;
import com.trackmeet.modules.schedule.dto.ConflictQuery;
import com.trackmeet.modules.schedule.dto.ConflictResolveDTO;
import com.trackmeet.modules.schedule.entity.ConflictLog;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.mapper.ConflictLogMapper;
import com.trackmeet.modules.schedule.mapper.HeatMapper;
import com.trackmeet.modules.schedule.mapper.ScheduleMapper;
import com.trackmeet.modules.schedule.service.ConflictsService;
import com.trackmeet.modules.schedule.service.support.CachedScheduleData;
import com.trackmeet.modules.schedule.service.support.JsonTextUtils;
import com.trackmeet.modules.schedule.service.support.MeetGuard;
import com.trackmeet.modules.schedule.service.support.ScheduleDataProvider;
import com.trackmeet.modules.schedule.vo.ConflictVO;
import com.trackmeet.modules.schedule.vo.EventBriefRowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * 冲突检测服务实现。
 * <p>
 * 检测范围：默认当届全部「已排定（含计划起止时间）」的日程；可按单元或项目缩小范围。
 * 每次检测先清理范围内 {@code resolved=0} 的旧记录，再写入新记录，
 * 最后按日程回填 {@code schedule.conflict_flag / conflict_count}。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class ConflictsServiceImpl extends ServiceImpl<ConflictLogMapper, ConflictLog> implements ConflictsService {

    /** 批次号前缀 */
    private static final String BATCH_PREFIX = "CONFLICT";

    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ScheduleDataProvider dataProvider;
    private final CachedScheduleData cachedData;
    private final HeatMapper heatMapper;
    private final ScheduleMapper scheduleMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ConflictVO> detect(ConflictQuery query) {
        ConflictQuery condition = query == null ? new ConflictQuery() : query;
        Long meetId = MeetGuard.resolve(condition.getMeetId());
        cachedData.load(meetId, null, null, false);

        List<Schedule> schedules = loadSchedules(meetId, condition.getSessionId(), condition.getEventId());
        if (schedules.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> scheduleIds = schedules.stream()
                .map(Schedule::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Long> heatIdBySchedule = heatIdsBySchedule(meetId, scheduleIds);
        Map<Long, List<Long>> athleteIdsByEvent = dataProvider.athleteIdsByEvent(meetId,
                schedules.stream().map(Schedule::getEventId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, List<Long>> refereeIdsBySchedule = dataProvider.refereeIdsBySchedule(meetId, scheduleIds);
        Map<Long, Schedule> scheduleById = schedules.stream()
                .collect(Collectors.toMap(Schedule::getId, schedule -> schedule, (first, second) -> first));

        List<ScheduledItem> items = new ArrayList<>();
        for (Schedule schedule : schedules) {
            if (schedule.getPlanStartTime() == null || schedule.getPlanEndTime() == null) {
                continue;
            }
            EventBriefRowVO event = cachedData.event(schedule.getEventId());
            boolean track = event != null && event.getEventType() != null
                    && ("TRACK".equalsIgnoreCase(event.getEventType()) || "RELAY".equalsIgnoreCase(event.getEventType()));
            items.add(ScheduledItem.builder()
                    .scheduleId(schedule.getId())
                    .sessionId(schedule.getSessionId())
                    .venueId(schedule.getVenueId())
                    .eventId(schedule.getEventId())
                    .roundId(schedule.getRoundId())
                    .itemName(buildItemName(event))
                    .startTime(schedule.getPlanStartTime())
                    .endTime(schedule.getPlanEndTime())
                    .track(track)
                    .heatCount(schedule.getHeatCount())
                    .athleteIds(new ArrayList<>(athleteIdsByEvent.getOrDefault(schedule.getEventId(), new ArrayList<>())))
                    // 裁判分工模块未交付时 referee_assignment 为空，此处为空列表，裁判类冲突不会被触发
                    .refereeIds(new ArrayList<>(refereeIdsBySchedule.getOrDefault(schedule.getId(), new ArrayList<>())))
                    .build());
        }
        if (items.size() < 2) {
            clearOldConflicts(meetId, scheduleIds);
            rebuildConflictFlag(meetId, scheduleIds);
            return new ArrayList<>();
        }

        ConflictOptions options = ConflictOptions.builder()
                .athleteIntervalMin(cachedData.conflictIntervalMin())
                .refereeIntervalMin(cachedData.refereeIntervalMin())
                .checkVenue(true)
                .checkAthlete(true)
                .checkReferee(true)
                .build();
        List<ConflictItem> detected = ConflictDetector.detect(items, options);

        clearOldConflicts(meetId, scheduleIds);
        String batchNo = BATCH_PREFIX + LocalDateTime.now().format(BATCH_FORMAT);
        LocalDateTime now = LocalDateTime.now();
        for (ConflictItem item : detected) {
            ConflictLog log = new ConflictLog();
            log.setMeetId(meetId);
            log.setConflictType(item.getConflictType());
            log.setLevel(item.getLevel());
            log.setScheduleId(item.getScheduleId());
            log.setRelatedScheduleId(item.getRelatedScheduleId());
            log.setHeatId(item.getScheduleId() == null ? null : heatIdBySchedule.get(item.getScheduleId()));
            log.setRelatedHeatId(item.getRelatedScheduleId() == null
                    ? null : heatIdBySchedule.get(item.getRelatedScheduleId()));
            log.setAthleteId(item.getAthleteId());
            log.setRefereeId(item.getRefereeId());
            log.setVenueId(item.getVenueId());
            Schedule main = item.getScheduleId() == null ? null : scheduleById.get(item.getScheduleId());
            Schedule related = item.getRelatedScheduleId() == null
                    ? null : scheduleById.get(item.getRelatedScheduleId());
            if (main != null) {
                log.setSessionId(main.getSessionId());
                log.setStartTime(main.getPlanStartTime());
                log.setEndTime(main.getPlanEndTime());
            }
            if (related != null) {
                log.setRelatedStartTime(related.getPlanStartTime());
                log.setRelatedEndTime(related.getPlanEndTime());
            }
            log.setOverlapMinutes((int) item.getOverlapMinutes());
            log.setIntervalMinutes(item.getIntervalMinutes() == null ? null : item.getIntervalMinutes().intValue());
            log.setRequiredIntervalMin(item.getRequiredIntervalMin() == null
                    ? null : item.getRequiredIntervalMin().intValue());
            log.setDetailJson(buildDetailJson(item, main, related));
            log.setBatchNo(batchNo);
            log.setDetectTime(now);
            log.setResolved(ConflictLog.UNRESOLVED);
            this.save(log);
        }
        rebuildConflictFlag(meetId, scheduleIds);
        List<ConflictVO> detectedVOs = baseMapper.selectConflictVOs(meetId, condition.getSessionId(), null,
                condition.getEventId(), null, null, ConflictLog.UNRESOLVED);
        fillMessage(detectedVOs);
        return detectedVOs;
    }

    @Override
    public PageResult<ConflictVO> pageConflict(ConflictQuery query) {
        ConflictQuery condition = query == null ? new ConflictQuery() : query;
        Long meetId = MeetGuard.resolve(condition.getMeetId());
        List<ConflictVO> all = baseMapper.selectConflictVOs(meetId, condition.getSessionId(),
                condition.getScheduleId(), condition.getEventId(), condition.getConflictType(),
                condition.getLevel(), condition.getResolved());
        List<ConflictVO> records = all == null ? new ArrayList<>() : all;
        fillMessage(records);
        // 冲突清单为多表投影脚本查询，分页在服务层切分（单届冲突规模为数百条）
        List<ConflictVO> pageRecords = inMemoryPage(records, condition.pageNo(), condition.pageSize());
        return PageResult.of(pageRecords, records.size(), condition.pageNo(), condition.pageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long id, ConflictResolveDTO dto) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "冲突记录 ID 不能为空");
        }
        ConflictLog log = this.getById(id);
        if (log == null) {
            throw new BizException(ResultCode.NOT_FOUND, "冲突记录不存在");
        }
        MeetGuard.check(log.getMeetId());
        LambdaUpdateWrapper<ConflictLog> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ConflictLog::getId, id)
                .set(ConflictLog::getResolved, ConflictLog.RESOLVED)
                .set(ConflictLog::getResolveRemark, dto == null ? null : dto.getResolveRemark())
                .set(ConflictLog::getResolveBy, SecurityUtils.getUserId())
                .set(ConflictLog::getResolveTime, LocalDateTime.now());
        this.update(wrapper);
        Set<Long> touched = new LinkedHashSet<>();
        if (log.getScheduleId() != null) {
            touched.add(log.getScheduleId());
        }
        if (log.getRelatedScheduleId() != null) {
            touched.add(log.getRelatedScheduleId());
        }
        rebuildConflictFlag(log.getMeetId(), touched);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private List<Schedule> loadSchedules(Long meetId, Long sessionId, Long eventId) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Schedule::getMeetId, meetId)
                .eq(sessionId != null, Schedule::getSessionId, sessionId)
                .eq(eventId != null, Schedule::getEventId, eventId)
                .isNotNull(Schedule::getPlanStartTime)
                .isNotNull(Schedule::getPlanEndTime)
                .orderByAsc(Schedule::getPlanStartTime)
                .orderByAsc(Schedule::getId);
        return list(wrapper);
    }

    private Map<Long, Long> heatIdsBySchedule(Long meetId, Set<Long> scheduleIds) {
        Map<Long, Long> map = new LinkedHashMap<>();
        if (scheduleIds.isEmpty()) {
            return map;
        }
        List<Heat> heats = heatMapper.selectList(new LambdaQueryWrapper<Heat>()
                .eq(Heat::getMeetId, meetId)
                .in(Heat::getScheduleId, scheduleIds)
                .orderByAsc(Heat::getHeatNo));
        for (Heat heat : heats) {
            if (heat.getScheduleId() != null) {
                map.putIfAbsent(heat.getScheduleId(), heat.getId());
            }
        }
        return map;
    }

    /**
     * 清理检测范围内未处理的旧冲突记录。
     */
    private void clearOldConflicts(Long meetId, Set<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return;
        }
        this.remove(new LambdaQueryWrapper<ConflictLog>()
                .eq(ConflictLog::getMeetId, meetId)
                .eq(ConflictLog::getResolved, ConflictLog.UNRESOLVED)
                .and(wrapper -> wrapper.in(ConflictLog::getScheduleId, scheduleIds)
                        .or().in(ConflictLog::getRelatedScheduleId, scheduleIds)));
    }

    /**
     * 回填 {@code schedule.conflict_flag / conflict_count}（未处理冲突条数）。
     */
    private void rebuildConflictFlag(Long meetId, Set<Long> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return;
        }
        List<ConflictLog> unresolved = this.list(new LambdaQueryWrapper<ConflictLog>()
                .eq(ConflictLog::getMeetId, meetId)
                .eq(ConflictLog::getResolved, ConflictLog.UNRESOLVED)
                .and(wrapper -> wrapper.in(ConflictLog::getScheduleId, scheduleIds)
                        .or().in(ConflictLog::getRelatedScheduleId, scheduleIds)));
        Map<Long, Integer> counts = new HashMap<>();
        for (ConflictLog log : unresolved) {
            if (log.getScheduleId() != null) {
                counts.merge(log.getScheduleId(), 1, Integer::sum);
            }
            if (log.getRelatedScheduleId() != null && !Objects.equals(log.getRelatedScheduleId(), log.getScheduleId())) {
                counts.merge(log.getRelatedScheduleId(), 1, Integer::sum);
            }
        }
        for (Long scheduleId : scheduleIds) {
            int count = counts.getOrDefault(scheduleId, 0);
            LambdaUpdateWrapper<Schedule> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Schedule::getId, scheduleId)
                    .set(Schedule::getConflictFlag, count > 0 ? 1 : 0)
                    .set(Schedule::getConflictCount, count);
            scheduleMapper.update(null, wrapper);
        }
    }

    private List<ConflictVO> inMemoryPage(List<ConflictVO> records, long pageNo, long pageSize) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        int from = (int) Math.min((long) (pageNo - 1) * pageSize, records.size());
        int to = (int) Math.min(from + pageSize, records.size());
        return new ArrayList<>(records.subList(from, to));
    }

    private String buildItemName(EventBriefRowVO event) {
        return event == null || event.getEventName() == null ? "未命名项目" : event.getEventName();
    }

    private String buildDetailJson(ConflictItem item, Schedule main, Schedule related) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("message", item.getMessage());
        detail.put("conflictType", item.getConflictType());
        detail.put("level", item.getLevel());
        detail.put("overlapMinutes", item.getOverlapMinutes());
        detail.put("intervalMinutes", item.getIntervalMinutes());
        detail.put("requiredIntervalMin", item.getRequiredIntervalMin());
        detail.put("athleteId", item.getAthleteId());
        detail.put("refereeId", item.getRefereeId());
        detail.put("venueId", item.getVenueId());
        detail.put("scheduleStartTime", main == null ? null : main.getPlanStartTime());
        detail.put("scheduleEndTime", main == null ? null : main.getPlanEndTime());
        detail.put("relatedStartTime", related == null ? null : related.getPlanStartTime());
        detail.put("relatedEndTime", related == null ? null : related.getPlanEndTime());
        return JsonTextUtils.writeValue(objectMapper, detail);
    }

    /**
     * 冲突说明存储在 {@code detail_json.message}（{@code conflict_log} 无 message 列），
     * 列表返回前回填到 {@link ConflictVO#getMessage()}，保证前端可直接展示中文说明。
     */
    private void fillMessage(List<ConflictVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (ConflictVO vo : records) {
            if (vo == null || vo.getMessage() != null || vo.getDetailJson() == null) {
                continue;
            }
            try {
                var node = objectMapper.readTree(vo.getDetailJson());
                if (node != null && node.hasNonNull("message")) {
                    vo.setMessage(node.get("message").asText());
                }
            } catch (Exception ignored) {
                // 明细解析失败不影响清单展示
            }
        }
    }
}
