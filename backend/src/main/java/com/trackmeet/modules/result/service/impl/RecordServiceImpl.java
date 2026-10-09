package com.trackmeet.modules.result.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.mapper.EventMapper;
import com.trackmeet.modules.result.dto.RecordConfirmDTO;
import com.trackmeet.modules.result.dto.RecordQuery;
import com.trackmeet.modules.result.dto.RecordSaveDTO;
import com.trackmeet.modules.result.entity.Record;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.mapper.RecordMapper;
import com.trackmeet.modules.result.mapper.ResultMapper;
import com.trackmeet.modules.result.service.RecordService;
import com.trackmeet.modules.result.vo.RecordCandidateRowVO;
import com.trackmeet.modules.result.vo.RecordVO;
import com.trackmeet.modules.result.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 纪录服务实现（对应 {@code record} 表，DDL 见 {@code sql/02-schema-meet-athlete.sql}）。
 * <p>
 * 破纪录认定落点：
 * <ul>
 *     <li>{@code result.is_record_confirmed}：1 已认定 / 0 未认定；</li>
 *     <li>{@code result.record_id}：对标纪录 ID（取消认定时置空）；</li>
 *     <li>{@code result.record_type}：认定层级（WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP）；</li>
 *     <li>{@code result.is_record_candidate}：候选位，超风速仍可置位但不得认定。</li>
 * </ul>
 * 超风速（{@code is_wind_legal = 0}）时认定被拒绝并抛 {@code WIND_ILLEGAL}（风速超限，纪录不予认定）。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class RecordServiceImpl extends ServiceImpl<RecordMapper, Record> implements RecordService {

    /** 成绩比较方向：径赛（用时越少越好） */
    private static final String TRACK = "TRACK";

    /** 纪录层级取值顺序（由高到低，用于匹配「最高命中层级」） */
    private static final List<String> RECORD_TYPE_ORDER = List.of(
            Record.TYPE_WORLD, Record.TYPE_ASIA, Record.TYPE_NATION,
            Record.TYPE_PROVINCE, Record.TYPE_CITY, Record.TYPE_MEET, Record.TYPE_GROUP);

    private final RecordMapper recordMapper;

    private final ResultMapper resultMapper;

    private final EventMapper eventMapper;

    @Override
    public PageResult<RecordVO> pageRecord(RecordQuery query) {
        RecordQuery condition = query == null ? new RecordQuery() : query;
        Long meetId = condition.getMeetId() == null ? currentMeetId() : condition.getMeetId();
        if (!Objects.equals(meetId, MeetContext.get()) && MeetContext.get() != null) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查询其他届次的数据");
        }
        Page<RecordVO> pageParam = new Page<>(condition.pageNo(), condition.pageSize());
        Page<RecordVO> result = recordMapper.selectRecordPage(pageParam, meetId,
                condition.getEventId(), condition.getGroupId(), condition.getGroupCode(),
                condition.getRecordType(), condition.getGender(), condition.getIsRelay(),
                condition.getIsCurrent(), condition.getKeyword());
        PageResult<RecordVO> pageResult = PageResult.of(result);
        // 逐条补齐「被成绩引用次数」，便于前端判断纪录可维护性
        for (RecordVO vo : pageResult.getRecords()) {
            if (vo.getId() != null) {
                vo.setReferencedCount(recordMapper.countResultReference(meetId, vo.getId()));
            }
        }
        return pageResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(RecordSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "纪录信息不能为空");
        }
        Long meetId = resolveMeetId(dto.getMeetId());
        Event event = requireEvent(meetId, dto.getEventId());
        checkRecordValue(dto);
        Record entity = new Record();
        entity.setMeetId(meetId);
        applySave(dto, entity, event, null);
        entity.setId(null);
        if (!save(entity)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "纪录新增失败");
        }
        // 新增纪录后重扫候选，避免既有更好成绩被漏标
        scanRecordCandidates(dto.getEventId(), null);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(Long id, RecordSaveDTO dto) {
        if (id == null || dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "纪录 ID 与纪录信息不能为空");
        }
        Record exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "纪录不存在");
        }
        Long meetId = resolveMeetId(dto.getMeetId() == null ? exists.getMeetId() : dto.getMeetId());
        if (!Objects.equals(meetId, exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改纪录");
        }
        // 项目不允许迁移；未传时沿用原值
        if (dto.getEventId() == null) {
            dto.setEventId(exists.getEventId());
        }
        Event event = requireEvent(meetId, dto.getEventId());
        checkRecordValue(dto);
        Record entity = new Record();
        entity.setId(id);
        // 更新语义：入参为空字段沿用原值，避免把未提交的列写成 NULL
        applySave(dto, entity, event, exists);
        if (!updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "纪录已被他人修改，请刷新后重试");
        }
        scanRecordCandidates(dto.getEventId(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRecord(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "纪录 ID 不能为空");
        }
        Long meetId = currentMeetId();
        Record exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "纪录不存在");
        }
        if (!Objects.equals(meetId, exists.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        long referenced = recordMapper.countResultReference(meetId, id);
        if (referenced > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该纪录已被 " + referenced + " 条成绩引用，请先取消认定后再删除");
        }
        if (!removeById(id)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "纪录删除失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmRecord(RecordConfirmDTO dto) {
        if (dto == null || dto.getResultId() == null || dto.getConfirmed() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 与是否认定不能为空");
        }
        Long meetId = currentMeetId();
        Result result = resultMapper.selectById(dto.getResultId());
        if (result == null) {
            throw new BizException(ResultCode.NOT_FOUND, "成绩不存在");
        }
        if (!Objects.equals(meetId, result.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }

        // ① 取消认定：清空认定标记与对标纪录，保留候选位供复核
        if (!Boolean.TRUE.equals(dto.getConfirmed())) {
            resultMapper.updateRecordConfirm(meetId, result.getId(), Record.HISTORY, null, null,
                    Record.YES, dto.getReason());
            return;
        }

        // ② 认定：超风速一律拒绝（可保留候选位）
        if (isIllegalWind(result)) {
            throw new BizException(ResultCode.WIND_ILLEGAL,
                    "风速超限（" + result.getWindSpeed() + " m/s），纪录不予认定");
        }
        if (result.getResultValue() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "无有效成绩数值，无法认定纪录");
        }

        // ③ 对标纪录：显式 ID 优先，其次按层级匹配，最后按成绩自动匹配
        Record target = resolveTargetRecord(meetId, result, dto);
        String recordType = target != null ? target.getRecordType()
                : (StringUtils.hasText(dto.getRecordType()) ? dto.getRecordType() : result.getRecordType());
        Long recordId = target != null ? target.getId() : result.getRecordId();
        resultMapper.updateRecordConfirm(meetId, result.getId(), Record.CURRENT, recordId, recordType,
                Record.YES, dto.getReason());
    }

    @Override
    public List<ResultVO> listRecordCandidates(Long eventId, Long roundId) {
        Long meetId = currentMeetId();
        if (eventId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目不能为空");
        }
        List<RecordCandidateRowVO> rows = recordMapper.selectRecordCandidateRows(meetId, eventId, roundId);
        return buildCandidateViews(meetId, eventId, roundId, rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanRecordCandidates(Long eventId, Long roundId) {
        Long meetId = currentMeetId();
        if (eventId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目不能为空");
        }
        List<RecordCandidateRowVO> rows = recordMapper.selectRecordCandidateRows(meetId, eventId, roundId);
        int hit = 0;
        for (RecordCandidateRowVO row : rows) {
            if (row.getResultId() == null || row.getResultValue() == null) {
                continue;
            }
            Record target = matchRecord(meetId, row);
            if (target == null) {
                // 无对标纪录：清理陈旧候选位
                if (Integer.valueOf(1).equals(row.getIsRecordCandidate())) {
                    recordMapper.updateCandidate(meetId, row.getResultId(), Record.NO, null, null);
                }
                continue;
            }
            // 已人工认定的成绩不覆盖其认定层级
            if (Integer.valueOf(1).equals(row.getIsRecordConfirmed())) {
                hit++;
                continue;
            }
            recordMapper.updateCandidate(meetId, row.getResultId(), Record.YES, target.getId(), target.getRecordType());
            hit++;
        }
        return hit;
    }

    /* ==================================================================== */
    /*                            内部实现                                   */
    /* ==================================================================== */

    /**
     * 组装候选成绩视图（复用成绩列表口径列）。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @param roundId 轮次 ID（可空）
     * @param rows    候选投影行
     * @return 成绩视图列表（含项目与运动员展示字段）
     */
    private List<ResultVO> buildCandidateViews(Long meetId, Long eventId, Long roundId,
                                               List<RecordCandidateRowVO> rows) {
        List<ResultVO> views = new ArrayList<>(rows.size());
        for (RecordCandidateRowVO row : rows) {
            ResultVO vo = new ResultVO();
            vo.setId(row.getResultId());
            vo.setMeetId(row.getMeetId());
            vo.setEventId(row.getEventId());
            vo.setRoundId(row.getRoundId());
            vo.setScheduleId(row.getScheduleId());
            vo.setHeatId(row.getHeatId());
            vo.setRegistrationId(row.getRegistrationId());
            vo.setAthleteId(row.getAthleteId());
            vo.setUnitId(row.getUnitId());
            vo.setGroupId(row.getGroupId());
            vo.setResultRaw(row.getResultRaw());
            vo.setResultValue(row.getResultValue());
            vo.setResultUnit(row.getResultUnit());
            vo.setRankNo(row.getRankNo());
            vo.setRankDisplay(row.getRankDisplay());
            vo.setStatus(row.getStatus());
            vo.setWindSpeed(row.getWindSpeed());
            vo.setIsWindLegal(row.getIsWindLegal());
            vo.setIsRecordCandidate(row.getIsRecordCandidate());
            vo.setIsRecordConfirmed(row.getIsRecordConfirmed());
            vo.setRecordId(row.getRecordId());
            vo.setRecordType(row.getRecordType());
            vo.setAthleteName(row.getAthleteName());
            vo.setBibNo(row.getBibNo());
            vo.setUnitName(row.getUnitName());
            vo.setGroupName(row.getGroupName());
            vo.setEventName(row.getEventName());
            vo.setEventType(row.getEventType());
            vo.setSubType(row.getSubType());
            vo.setRelayFlag(row.getRelayFlag());
            views.add(vo);
        }
        return views;
    }

    /**
     * 匹配成绩命中的最高层级现行纪录。
     *
     * @param meetId 届次 ID
     * @param row    候选投影行
     * @return 命中的纪录，未命中返回 null
     */
    private Record matchRecord(Long meetId, RecordCandidateRowVO row) {
        List<RecordVO> current = recordMapper.selectCurrentRecords(meetId, row.getEventId(),
                row.getGroupCode(), row.getGender());
        if (current.isEmpty()) {
            return null;
        }
        boolean track = TRACK.equalsIgnoreCase(row.getEventType());
        Record best = null;
        for (RecordVO vo : current) {
            if (!Objects.equals(row.getRelayFlag(), vo.getIsRelay())) {
                continue;
            }
            if (vo.getRecordValue() == null) {
                continue;
            }
            if (!beats(row.getResultValue(), vo.getRecordValue(), track)) {
                continue;
            }
            if (best == null || typePriority(vo.getRecordType()) < typePriority(best.getRecordType())) {
                best = toRecord(vo);
            }
        }
        return best;
    }

    /**
     * 层级优先级（数值越小层级越高，用于取「最高命中层级」）。
     *
     * @param recordType 纪录层级
     * @return 优先级序号，未知层级排最后
     */
    private int typePriority(String recordType) {
        int index = RECORD_TYPE_ORDER.indexOf(recordType);
        return index < 0 ? RECORD_TYPE_ORDER.size() : index;
    }

    /**
     * 纪录视图转实体（仅用于取出 ID 与层级）。
     *
     * @param vo 纪录视图
     * @return 纪录实体
     */
    private Record toRecord(RecordVO vo) {
        Record entity = new Record();
        entity.setId(vo.getId());
        entity.setMeetId(vo.getMeetId());
        entity.setRecordType(vo.getRecordType());
        entity.setEventId(vo.getEventId());
        entity.setRecordValue(vo.getRecordValue());
        entity.setRecordUnit(vo.getRecordUnit());
        return entity;
    }

    /**
     * 成绩是否优于纪录值。
     *
     * @param resultValue 成绩数值
     * @param recordValue 纪录数值
     * @param track       是否径赛（成绩越小越好）
     * @return 优于纪录返回 true
     */
    private boolean beats(BigDecimal resultValue, BigDecimal recordValue, boolean track) {
        if (resultValue == null || recordValue == null) {
            return false;
        }
        return track ? resultValue.compareTo(recordValue) < 0 : resultValue.compareTo(recordValue) > 0;
    }

    /**
     * 解析认定对标的纪录。
     *
     * @param meetId 届次 ID
     * @param result 成绩实体
     * @param dto    认定入参
     * @return 对标纪录，无法确定时返回 null（按入参层级回写）
     */
    private Record resolveTargetRecord(Long meetId, Result result, RecordConfirmDTO dto) {
        if (dto.getRecordId() != null) {
            Record record = getById(dto.getRecordId());
            if (record == null) {
                throw new BizException(ResultCode.NOT_FOUND, "对标纪录不存在");
            }
            if (!Objects.equals(meetId, record.getMeetId())) {
                throw new BizException(ResultCode.FORBIDDEN, "无权引用其他届次的纪录");
            }
            return record;
        }
        RecordCandidateRowVO row = new RecordCandidateRowVO();
        row.setEventId(result.getEventId());
        row.setGender(null);
        row.setGroupCode(null);
        row.setResultValue(result.getResultValue());
        row.setRelayFlag(null);
        Event event = eventMapper.selectById(result.getEventId());
        row.setEventType(event == null ? null : event.getEventType());
        Record matched = matchRecord(meetId, row);
        if (matched == null && StringUtils.hasText(dto.getRecordType())) {
            // 显式层级：按层级取现行纪录
            List<RecordVO> current = recordMapper.selectCurrentRecords(meetId, result.getEventId(), null, null);
            for (RecordVO vo : current) {
                if (dto.getRecordType().equals(vo.getRecordType())) {
                    return toRecord(vo);
                }
            }
        }
        return matched;
    }

    /**
     * 成绩是否超风速（{@code is_wind_legal = 0} 且已测风速）。
     *
     * @param result 成绩实体
     * @return 超风速返回 true
     */
    private boolean isIllegalWind(Result result) {
        return result.getWindSpeed() != null && Integer.valueOf(0).equals(result.getIsWindLegal());
    }

    /**
     * 纪录必填项与数值校验。
     *
     * @param dto 纪录入参
     */
    private void checkRecordValue(RecordSaveDTO dto) {
        if (!StringUtils.hasText(dto.getRecordRaw()) && dto.getRecordValue() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "纪录原文与纪录数值至少填写一项");
        }
        if (dto.getRecordValue() == null && StringUtils.hasText(dto.getRecordRaw())) {
            BigDecimal parsed = ResultRawParser.parse(dto.getRecordRaw());
            if (parsed == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "纪录原文无法解析为数值：" + dto.getRecordRaw());
            }
            dto.setRecordValue(parsed);
        }
        if (!StringUtils.hasText(dto.getRecordRaw()) && dto.getRecordValue() != null) {
            dto.setRecordRaw(dto.getRecordValue().stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 把入参写入实体（新增与修改共用）。
     * <p>
     * 修改时 {@code existing} 非空：入参未提交的字段（null）沿用原值，
     * 避免 MyBatis-Plus 的「非空字段更新」语义把未提交列写成 NULL。
     *
     * @param dto      纪录入参
     * @param entity   目标实体
     * @param event    项目（用于补全项目名称、性别、接力标记与单位）
     * @param existing 修改前的原纪录（新增时为 null）
     */
    private void applySave(RecordSaveDTO dto, Record entity, Event event, Record existing) {
        entity.setRecordType(dto.getRecordType());
        entity.setEventId(dto.getEventId());
        entity.setEventName(firstText(dto.getEventName(), existing == null ? null : existing.getEventName(),
                event.getEventName()));
        entity.setGroupId(dto.getGroupId() != null ? dto.getGroupId() : existingValue(existing, Record::getGroupId));
        entity.setGroupCode(dto.getGroupCode() != null ? dto.getGroupCode()
                : existingValue(existing, Record::getGroupCode));
        entity.setGender(firstText(dto.getGender(), existing == null ? null : existing.getGender(), event.getGender()));
        entity.setIsRelay(dto.getIsRelay() != null ? dto.getIsRelay()
                : (existing != null && existing.getIsRelay() != null ? existing.getIsRelay()
                        : (Integer.valueOf(1).equals(event.getIsRelay()) ? Record.YES : Record.NO)));
        entity.setAthleteName(dto.getAthleteName() != null ? dto.getAthleteName()
                : existingValue(existing, Record::getAthleteName));
        entity.setUnitName(dto.getUnitName() != null ? dto.getUnitName()
                : existingValue(existing, Record::getUnitName));
        entity.setRegionName(dto.getRegionName() != null ? dto.getRegionName()
                : existingValue(existing, Record::getRegionName));
        entity.setRecordValue(dto.getRecordValue() != null ? dto.getRecordValue()
                : existingValue(existing, Record::getRecordValue));
        entity.setRecordRaw(dto.getRecordRaw() != null ? dto.getRecordRaw()
                : existingValue(existing, Record::getRecordRaw));
        entity.setRecordUnit(firstText(dto.getRecordUnit(), existing == null ? null : existing.getRecordUnit(),
                event.getUnitOfMeasure()));
        entity.setWindSpeed(dto.getWindSpeed() != null ? dto.getWindSpeed()
                : existingValue(existing, Record::getWindSpeed));
        entity.setIsWindLegal(dto.getIsWindLegal() != null ? dto.getIsWindLegal()
                : existingValue(existing, Record::getIsWindLegal));
        entity.setRecordDate(dto.getRecordDate() != null ? dto.getRecordDate()
                : existingValue(existing, Record::getRecordDate));
        entity.setRecordPlace(dto.getRecordPlace() != null ? dto.getRecordPlace()
                : existingValue(existing, Record::getRecordPlace));
        entity.setIsCurrent(dto.getIsCurrent() != null ? dto.getIsCurrent()
                : (existing != null && existing.getIsCurrent() != null ? existing.getIsCurrent() : Record.CURRENT));
        entity.setSource(dto.getSource() != null ? dto.getSource() : existingValue(existing, Record::getSource));
    }

    /**
     * 新增场景下的原值取值辅助（{@code existing} 为 null）。
     *
     * @param existing 原纪录
     * @param getter   取值函数
     * @param <T>      值类型
     * @return 原值或 null
     */
    private <T> T existingValue(Record existing, java.util.function.Function<Record, T> getter) {
        return existing == null ? null : getter.apply(existing);
    }

    /**
     * 取第一个非空文本。
     *
     * @param values 候选文本（按优先级）
     * @return 命中的文本或 null
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 校验项目存在且属于指定届次。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 项目实体
     */
    private Event requireEvent(Long meetId, Long eventId) {
        if (eventId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目不能为空");
        }
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        if (!Objects.equals(meetId, event.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        return event;
    }

    /**
     * 解析届次：入参优先，其次上下文；与上下文不一致按越权处理。
     *
     * @param meetId 入参届次 ID
     * @return 届次 ID
     */
    private Long resolveMeetId(Long meetId) {
        Long current = MeetContext.get();
        Long resolved = meetId == null ? current : meetId;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        if (current != null && !Objects.equals(current, resolved)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        return resolved;
    }

    /**
     * 当前届次 ID。
     *
     * @return 届次 ID
     */
    private Long currentMeetId() {
        Long meetId = MeetContext.get();
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次上下文缺失");
        }
        return meetId;
    }
}
