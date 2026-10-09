package com.trackmeet.modules.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.dto.EventRoundRuleSaveDTO;
import com.trackmeet.modules.event.dto.EventRoundSaveDTO;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.EventRound;
import com.trackmeet.modules.event.entity.EventRoundRule;
import com.trackmeet.modules.event.mapper.EventRoundMapper;
import com.trackmeet.modules.event.mapper.EventRoundRuleMapper;
import com.trackmeet.modules.event.service.EventRoundService;
import com.trackmeet.modules.event.service.EventService;
import com.trackmeet.modules.event.vo.EventRoundRuleVO;
import com.trackmeet.modules.event.vo.EventRoundVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 轮次服务实现：轮次 CRUD、项目内 {@code round_no} 顺序与 {@code is_final} 自动维护。
 * <p>
 * {@code round_no} 按轮次码的内置次序（及格赛 → 预赛 → 次赛 → 复赛 → 半决赛 → 决赛）生成，
 * 该顺序仅为「显示与编排的默认次序」，不构成任何成绩 / 晋级业务阈值；
 * 晋级与录取规则一律取自 {@link EventRoundRule}。
 *
 * @author TrackMeet
 */
@Service
public class EventRoundServiceImpl extends ServiceImpl<EventRoundMapper, EventRound> implements EventRoundService {

    /** 项目内轮次码的默认显示次序（非业务阈值） */
    private static final Map<String, Integer> ROUND_CODE_SEQ = Map.of(
            EventRound.CODE_QUALIFY, 1,
            EventRound.CODE_PRE, 2,
            EventRound.CODE_R1, 3,
            EventRound.CODE_R2, 4,
            EventRound.CODE_SEMI, 5,
            EventRound.CODE_FINAL, 6);

    /** 兜底默认：同项目仅允许一个决赛轮 */
    private static final int MAX_FINAL_ROUND = 1;

    /** 兜底默认：round_code 列长度上限（VARCHAR(32)） */
    private static final int CODE_MAX_LENGTH = 32;

    /** 逻辑删除时唯一键后缀前缀 */
    private static final String DELETED_SUFFIX = "_d";

    private final EventService eventService;
    private final EventRoundRuleMapper eventRoundRuleMapper;

    public EventRoundServiceImpl(EventService eventService, EventRoundRuleMapper eventRoundRuleMapper) {
        this.eventService = eventService;
        this.eventRoundRuleMapper = eventRoundRuleMapper;
    }

    @Override
    public List<EventRoundVO> listByEventId(Long eventId) {
        eventService.getEntity(eventId);
        List<EventRound> rounds = listOrdered(eventId);
        if (rounds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roundIds = rounds.stream().map(EventRound::getId).collect(Collectors.toList());
        List<EventRoundRule> rules = eventRoundRuleMapper.selectList(new LambdaQueryWrapper<EventRoundRule>()
                .in(EventRoundRule::getRoundId, roundIds));
        Map<Long, EventRoundRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(EventRoundRule::getRoundId, rule -> rule, (a, b) -> a));
        return rounds.stream()
                .map(round -> toVo(round, ruleMap.get(round.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public EventRoundVO getRoundDetail(Long id) {
        EventRound round = getEntity(id);
        EventRoundRule rule = eventRoundRuleMapper.selectOne(new LambdaQueryWrapper<EventRoundRule>()
                .eq(EventRoundRule::getRoundId, id)
                .last("LIMIT 1"));
        return toVo(round, rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRound(Long eventId, EventRoundSaveDTO dto) {
        Event event = eventService.getEntity(eventId);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次参数不能为空");
        }
        if (!StringUtils.hasText(dto.getRoundCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次码不能为空");
        }
        checkRoundCodeUnique(eventId, dto.getRoundCode(), null);

        EventRound entity = new EventRound();
        entity.setMeetId(event.getMeetId());
        entity.setEventId(eventId);
        entity.setRoundCode(dto.getRoundCode());
        entity.setRoundName(dto.getRoundName());
        entity.setRoundNo(dto.getRoundNo());
        entity.setIsFinal(dto.getIsFinal());
        entity.setPlanHeatCount(dto.getPlanHeatCount());
        entity.setPlanHeatSize(dto.getPlanHeatSize());
        entity.setPlanAdvanceCount(dto.getPlanAdvanceCount());
        entity.setPlanAdvanceGroupTop(dto.getPlanAdvanceGroupTop());
        entity.setPlanBestLoserCount(dto.getPlanBestLoserCount());
        entity.setEntryCount(dto.getEntryCount());
        entity.setRoundDate(dto.getRoundDate());
        entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : EventRound.STATUS_PLANNED);
        entity.setRemark(dto.getRemark());
        this.save(entity);

        // 自动维护项目内 round_no 顺序与 is_final 标记，并同步项目 round_count
        reorderRounds(eventId);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRound(Long id, EventRoundSaveDTO dto) {
        EventRound exists = getEntity(id);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次参数不能为空");
        }
        if (dto.getEventId() != null && !Objects.equals(dto.getEventId(), exists.getEventId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨项目修改轮次");
        }
        if (StringUtils.hasText(dto.getRoundCode())) {
            checkRoundCodeUnique(exists.getEventId(), dto.getRoundCode(), id);
        }
        EventRound patch = new EventRound();
        patch.setId(id);
        // 允许修改的字段：round_code / round_name / plan_heat_count / plan_advance_count / status
        patch.setRoundCode(dto.getRoundCode());
        patch.setRoundName(dto.getRoundName());
        patch.setPlanHeatCount(dto.getPlanHeatCount());
        patch.setPlanAdvanceCount(dto.getPlanAdvanceCount());
        patch.setStatus(dto.getStatus());
        // 随同可维护的编排计划值
        patch.setRoundNo(dto.getRoundNo());
        patch.setIsFinal(dto.getIsFinal());
        patch.setPlanHeatSize(dto.getPlanHeatSize());
        patch.setPlanAdvanceGroupTop(dto.getPlanAdvanceGroupTop());
        patch.setPlanBestLoserCount(dto.getPlanBestLoserCount());
        patch.setEntryCount(dto.getEntryCount());
        patch.setRoundDate(dto.getRoundDate());
        patch.setRemark(dto.getRemark());
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "轮次已被其他用户修改，请刷新后重试");
        }
        reorderRounds(exists.getEventId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRound(Long id) {
        EventRound round = getEntity(id);
        long reference = baseMapper.countSourceRoundReference(round.getMeetId(), id);
        if (reference > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该轮次已被下级轮次作为晋级来源引用（" + reference + " 条），禁止删除");
        }
        // 级联清理本轮的轮次级规则
        baseMapper.deleteRoundRuleByRound(round.getMeetId(), id);
        // 逻辑删除前改写 round_code 释放项目内唯一键（uk_round_event_code），轮次码可在后续复用
        EventRound patch = new EventRound();
        patch.setId(id);
        patch.setRoundCode(buildDeletedCode(round.getRoundCode(), id));
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "轮次已被其他用户修改，请刷新后重试");
        }
        this.removeById(id);
        reorderRounds(round.getEventId());
    }

    @Override
    public EventRound getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次 ID 不能为空");
        }
        EventRound round = this.getById(id);
        if (round == null) {
            throw new BizException(ResultCode.NOT_FOUND, "轮次不存在");
        }
        return round;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveRoundRule(Long roundId, EventRoundRuleSaveDTO dto) {
        EventRound round = getEntity(roundId);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则参数不能为空");
        }
        // 请求体中的 roundId 仅作契约兼容，一旦传入必须与路径一致
        if (dto.getRoundId() != null && !dto.getRoundId().equals(roundId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "请求体中的轮次 ID 与路径不一致");
        }
        RuleJsonValidator.validate(dto.getAdvanceJson(), "晋级规则");
        RuleJsonValidator.validate(dto.getTieBreakJson(), "平局规则");
        if (dto.getAdmitRank() != null && dto.getAdmitRank() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "录取名次必须大于 0");
        }
        if (dto.getGroupTopN() != null && dto.getGroupTopN() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "每组晋级人数不能为负数");
        }
        if (dto.getBestLoserCount() != null && dto.getBestLoserCount() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "递补人数不能为负数");
        }
        if (dto.getTargetCount() != null && dto.getTargetCount() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "目标晋级人数不能为负数");
        }
        if (dto.getSourceRoundId() != null) {
            if (Objects.equals(dto.getSourceRoundId(), roundId)) {
                throw new BizException(ResultCode.PARAM_ERROR, "晋级来源轮次不能是本轮自身");
            }
            EventRound sourceRound = getEntity(dto.getSourceRoundId());
            if (!Objects.equals(sourceRound.getEventId(), round.getEventId())) {
                throw new BizException(ResultCode.PARAM_ERROR, "晋级来源轮次必须属于同一项目");
            }
        }

        EventRoundRule entity = new EventRoundRule();
        entity.setMeetId(round.getMeetId());
        entity.setRoundId(roundId);
        entity.setEventId(round.getEventId());
        entity.setAdmitRank(dto.getAdmitRank());
        entity.setGroupTopN(dto.getGroupTopN());
        entity.setBestLoserCount(dto.getBestLoserCount());
        entity.setTargetCount(dto.getTargetCount());
        entity.setAdvanceJson(dto.getAdvanceJson());
        entity.setTieBreakJson(dto.getTieBreakJson());
        entity.setSourceRoundId(dto.getSourceRoundId());
        entity.setIsInherit(dto.getIsInherit());
        entity.setExcludeIllegalWind(dto.getExcludeIllegalWind());
        entity.setManualAdvanceCount(dto.getManualAdvanceCount());
        entity.setRemark(dto.getRemark());

        // 唯一键 uk_roundrule_round：先查后插 / 更新
        EventRoundRule exists = eventRoundRuleMapper.selectOne(new LambdaQueryWrapper<EventRoundRule>()
                .eq(EventRoundRule::getRoundId, roundId)
                .last("LIMIT 1"));
        if (exists == null) {
            eventRoundRuleMapper.insert(entity);
            return entity.getId();
        }
        entity.setId(exists.getId());
        entity.setVersion(exists.getVersion());
        if (eventRoundRuleMapper.updateById(entity) <= 0) {
            throw new BizException(ResultCode.CONFLICT, "轮次级规则已被其他用户修改，请刷新后重试");
        }
        return exists.getId();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 查询项目下的轮次（按 round_no 升序、id 升序）。
     */
    private List<EventRound> listOrdered(Long eventId) {
        return this.list(new LambdaQueryWrapper<EventRound>()
                .eq(EventRound::getEventId, eventId)
                .orderByAsc(EventRound::getRoundNo)
                .orderByAsc(EventRound::getId));
    }

    /**
     * 重排项目内轮次顺序：先按「轮次码次序 + 当前 round_no」排序，
     * 再重写连续的 round_no，并按「决赛唯一」规则设置 is_final；最后同步项目 round_count。
     */
    private void reorderRounds(Long eventId) {
        List<EventRound> rounds = listOrdered(eventId);
        if (rounds.isEmpty()) {
            updateRoundCount(eventId, 0);
            return;
        }
        List<EventRound> sorted = new ArrayList<>(rounds);
        sorted.sort((a, b) -> {
            int seqA = ROUND_CODE_SEQ.getOrDefault(a.getRoundCode(), Integer.MAX_VALUE);
            int seqB = ROUND_CODE_SEQ.getOrDefault(b.getRoundCode(), Integer.MAX_VALUE);
            if (seqA != seqB) {
                return Integer.compare(seqA, seqB);
            }
            int noA = a.getRoundNo() == null ? Integer.MAX_VALUE : a.getRoundNo();
            int noB = b.getRoundNo() == null ? Integer.MAX_VALUE : b.getRoundNo();
            if (noA != noB) {
                return Integer.compare(noA, noB);
            }
            return Long.compare(a.getId(), b.getId());
        });
        long finalCount = sorted.stream()
                .filter(round -> EventRound.CODE_FINAL.equals(round.getRoundCode()))
                .count();
        boolean finalUnique = finalCount <= MAX_FINAL_ROUND;

        List<EventRound> patches = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            EventRound round = sorted.get(i);
            int expectedNo = i + 1;
            Integer expectedFinal = resolveFinalFlag(round, i, sorted.size(), finalUnique);
            boolean noChanged = round.getRoundNo() == null || round.getRoundNo() != expectedNo;
            boolean finalChanged = !Objects.equals(round.getIsFinal(), expectedFinal);
            if (noChanged || finalChanged) {
                EventRound patch = new EventRound();
                patch.setId(round.getId());
                patch.setRoundNo(expectedNo);
                patch.setIsFinal(expectedFinal);
                patch.setVersion(round.getVersion());
                patches.add(patch);
            }
        }
        if (!patches.isEmpty()) {
            this.updateBatchById(patches);
        }
        updateRoundCount(eventId, sorted.size());
    }

    /**
     * 决赛标记：显式声明 FINAL 的轮次为决赛；若未声明任何 FINAL，则最后一轮视为决赛。
     * 未声明 FINAL 但存在多个 FINAL 码属于配置错误，此处按轮次码顺序保留最后一轮为决赛。
     */
    private Integer resolveFinalFlag(EventRound round, int index, int size, boolean finalUnique) {
        if (EventRound.CODE_FINAL.equals(round.getRoundCode())) {
            if (finalUnique) {
                return 1;
            }
            return index == size - 1 ? 1 : 0;
        }
        return 0;
    }

    /**
     * 同步项目 round_count 快照。
     */
    private void updateRoundCount(Long eventId, int roundCount) {
        Event patch = new Event();
        patch.setId(eventId);
        patch.setRoundCount(roundCount);
        eventService.updateById(patch);
    }

    /**
     * 校验 {@code (event_id, round_code)} 唯一（uk_round_event_code）。
     */
    private void checkRoundCodeUnique(Long eventId, String roundCode, Long excludeId) {
        LambdaQueryWrapper<EventRound> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventRound::getEventId, eventId)
                .eq(EventRound::getRoundCode, roundCode)
                .ne(excludeId != null, EventRound::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该项目下轮次码已存在：" + roundCode);
        }
    }

    /**
     * 实体 + 规则 → 视图对象。
     */
    private EventRoundVO toVo(EventRound round, EventRoundRule rule) {
        EventRoundVO vo = new EventRoundVO();
        vo.setId(round.getId());
        vo.setEventId(round.getEventId());
        vo.setRoundCode(round.getRoundCode());
        vo.setRoundName(round.getRoundName());
        vo.setRoundNo(round.getRoundNo());
        vo.setIsFinal(round.getIsFinal());
        vo.setPlanHeatCount(round.getPlanHeatCount());
        vo.setPlanAdvanceCount(round.getPlanAdvanceCount());
        vo.setStatus(round.getStatus());
        if (rule != null) {
            vo.setRule(toRuleVo(rule));
        }
        return vo;
    }

    /**
     * 轮次级规则实体 → 视图对象。
     */
    private EventRoundRuleVO toRuleVo(EventRoundRule rule) {
        EventRoundRuleVO ruleVo = new EventRoundRuleVO();
        ruleVo.setId(rule.getId());
        ruleVo.setRoundId(rule.getRoundId());
        ruleVo.setAdmitRank(rule.getAdmitRank());
        ruleVo.setGroupTopN(rule.getGroupTopN());
        ruleVo.setBestLoserCount(rule.getBestLoserCount());
        ruleVo.setTargetCount(rule.getTargetCount());
        ruleVo.setAdvanceJson(rule.getAdvanceJson());
        ruleVo.setTieBreakJson(rule.getTieBreakJson());
        ruleVo.setSourceRoundId(rule.getSourceRoundId());
        return ruleVo;
    }

    /**
     * 生成改写后的唯一键：原值 + _d{id}，超长时截断原值以保证不超出列长度。
     * <p>
     * 与 {@code MeetGroupServiceImpl} 的「逻辑删除时改写唯一键」策略保持一致。
     *
     * @param code 原轮次码
     * @param id   轮次 ID
     * @return 改写后的轮次码
     */
    private String buildDeletedCode(String code, Long id) {
        String suffix = DELETED_SUFFIX + id;
        String base = code == null ? "" : code;
        int maxBaseLength = CODE_MAX_LENGTH - suffix.length();
        if (base.length() > maxBaseLength) {
            base = base.substring(0, Math.max(maxBaseLength, 0));
        }
        return base + suffix;
    }
}
