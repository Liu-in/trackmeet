package com.trackmeet.modules.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.dto.EventRuleConfigSaveDTO;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.EventRuleConfig;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import com.trackmeet.modules.event.mapper.EventRuleConfigMapper;
import com.trackmeet.modules.event.service.EventRuleService;
import com.trackmeet.modules.event.service.EventService;
import com.trackmeet.modules.event.service.MeetRuleConfigService;
import com.trackmeet.modules.event.vo.EventRuleConfigVO;
import com.trackmeet.modules.event.vo.MeetRuleConfigVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.function.BiConsumer;

/**
 * 项目级规则服务实现：三层继承（项目级 &gt; 届次级 &gt; 系统兜底默认）与来源标注。
 * <p>
 * <b>来源标注实现方式</b>：逐字段调用统一的三元解析方法
 * （{@code pickInt / pickString / pickDecimal}），
 * 项目级非空 → {@code PROJECT}，否则届次级非空 → {@code MEET}，否则系统兜底默认 → {@code DEFAULT}；
 * 解析出的来源字符串写入 VO 的 {@code xxxSource} 字段。
 * <p>
 * <b>为何按「非空」回退而不是按 {@code is_inherit_meet} 开关</b>：
 * DDL 与需求文档定义的空值语义即「继承上一层」（{@code event_rule_config} 注释：
 * 「空值语义为『继承上一层』」），{@code is_inherit_meet} 仅作为「是否继承」的显式声明标记持久化，
 * 因此读取路径统一采用非空回退，保证「未配置 = 继承」始终成立、无需硬编码判断分支。
 *
 * @author TrackMeet
 */
@Service
public class EventRuleServiceImpl extends ServiceImpl<EventRuleConfigMapper, EventRuleConfig>
        implements EventRuleService {

    /** 兜底默认：分组方式为蛇形（R-09） */
    private static final String DEFAULT_DRAW_MODE = "SERPENT";
    /** 兜底默认：田赛试跳 / 试掷次数（R-14） */
    private static final int DEFAULT_ATTEMPT_COUNT = 3;
    /** 项目级规则来源：项目级 */
    private static final String SOURCE_PROJECT = EventRuleConfigVO.SOURCE_PROJECT;
    /** 项目级规则来源：届次级 */
    private static final String SOURCE_MEET = EventRuleConfigVO.SOURCE_MEET;
    /** 项目级规则来源：系统兜底默认 */
    private static final String SOURCE_DEFAULT = EventRuleConfigVO.SOURCE_DEFAULT;

    private final EventService eventService;
    private final MeetRuleConfigService meetRuleConfigService;

    public EventRuleServiceImpl(EventService eventService,
                               MeetRuleConfigService meetRuleConfigService) {
        this.eventService = eventService;
        this.meetRuleConfigService = meetRuleConfigService;
    }

    @Override
    public EventRuleConfig getRaw(Long eventId) {
        if (eventId == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<EventRuleConfig>()
                .eq(EventRuleConfig::getEventId, eventId)
                .last("LIMIT 1"), false);
    }

    @Override
    public MeetRuleConfigVO getMeetRuleConfig(Long eventId) {
        Event event = eventService.getEntity(eventId);
        return meetRuleConfigService.getEffective(event.getMeetId());
    }

    @Override
    public EventRuleConfigVO getRuleConfig(Long eventId) {
        Event event = eventService.getEntity(eventId);
        EventRuleConfig project = getRaw(eventId);
        // 届次级解析结果已套用系统兜底默认，作为项目级空值时的回退来源
        MeetRuleConfig meet = meetRuleConfigService.resolveEffective(event.getMeetId());

        EventRuleConfigVO vo = new EventRuleConfigVO();
        vo.setId(project == null ? null : project.getId());
        vo.setEventId(eventId);

        // 每个字段：项目级 → 届次级 → 系统兜底默认，并写入同名 xxxSource 来源标记
        pickInt(vo, project == null ? null : project.getAdmitRank(),
                meet.getAdmitRank(), null,
                EventRuleConfigVO::setAdmitRank, EventRuleConfigVO::setAdmitRankSource);
        pickString(vo, project == null ? null : project.getDrawMode(),
                null, DEFAULT_DRAW_MODE,
                EventRuleConfigVO::setDrawMode, EventRuleConfigVO::setDrawModeSource);
        pickString(vo, project == null ? null : project.getLaneOrderJson(),
                meet.getDefaultLaneOrderJson(), null,
                EventRuleConfigVO::setLaneOrderJson, EventRuleConfigVO::setLaneOrderJsonSource);
        pickString(vo, project == null ? null : project.getLaneAssignMode(),
                meet.getLaneAssignMode(), null,
                EventRuleConfigVO::setLaneAssignMode, EventRuleConfigVO::setLaneAssignModeSource);
        pickInt(vo, project == null ? null : project.getConflictIntervalMin(),
                meet.getConflictIntervalMin(), null,
                EventRuleConfigVO::setConflictIntervalMin, EventRuleConfigVO::setConflictIntervalMinSource);
        pickString(vo, project == null ? null : project.getWindJson(),
                meet.getWindJson(), null,
                EventRuleConfigVO::setWindJson, EventRuleConfigVO::setWindJsonSource);
        pickInt(vo, project == null ? null : project.getAttemptCount(),
                meet.getFieldAttemptCount(), DEFAULT_ATTEMPT_COUNT,
                EventRuleConfigVO::setAttemptCount, EventRuleConfigVO::setAttemptCountSource);
        // 及格标准仅项目级可配，届次级无对应列
        pickString(vo, project == null ? null : project.getQualifyStandard(),
                null, null,
                EventRuleConfigVO::setQualifyStandard, EventRuleConfigVO::setQualifyStandardSource);
        pickDecimal(vo, project == null ? null : project.getQualifyStandardValue(),
                null, null,
                EventRuleConfigVO::setQualifyStandardValue, EventRuleConfigVO::setQualifyStandardValueSource);
        pickString(vo, project == null ? null : project.getHeightPlanJson(),
                null, null,
                EventRuleConfigVO::setHeightPlanJson, EventRuleConfigVO::setHeightPlanJsonSource);
        pickString(vo, project == null ? null : project.getTieBreakJson(),
                meet.getTieBreakJson(), null,
                EventRuleConfigVO::setTieBreakJson, EventRuleConfigVO::setTieBreakJsonSource);
        pickInt(vo, project == null ? null : project.getDurationMin(),
                null, event.getDurationMin(),
                EventRuleConfigVO::setDurationMin, EventRuleConfigVO::setDurationMinSource);
        pickString(vo, project == null ? null : project.getExtraJson(),
                meet.getExtraJson(), null,
                EventRuleConfigVO::setExtraJson, EventRuleConfigVO::setExtraJsonSource);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveRuleConfig(Long eventId, EventRuleConfigSaveDTO dto) {
        Event event = eventService.getEntity(eventId);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则参数不能为空");
        }
        // 请求体中的 eventId 仅作契约兼容，一旦传入必须与路径一致，避免跨项目写规则
        if (dto.getEventId() != null && !dto.getEventId().equals(eventId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "请求体中的项目 ID 与路径不一致");
        }
        validate(dto);

        EventRuleConfig entity = new EventRuleConfig();
        entity.setMeetId(event.getMeetId());
        entity.setEventId(eventId);
        entity.setAdmitRank(dto.getAdmitRank());
        entity.setDrawMode(dto.getDrawMode());
        entity.setDrawAvoidSameUnit(dto.getDrawAvoidSameUnit());
        entity.setDrawGroupSize(dto.getDrawGroupSize());
        entity.setLaneCount(dto.getLaneCount());
        entity.setLaneOrderJson(dto.getLaneOrderJson());
        entity.setLaneAssignMode(dto.getLaneAssignMode());
        entity.setConflictIntervalMin(dto.getConflictIntervalMin());
        entity.setWindJson(dto.getWindJson());
        entity.setAttemptCount(dto.getAttemptCount());
        entity.setFinalAttemptCount(dto.getFinalAttemptCount());
        entity.setQualifyStandard(dto.getQualifyStandard());
        entity.setQualifyStandardValue(dto.getQualifyStandardValue());
        entity.setQualifyStandardUnit(dto.getQualifyStandardUnit());
        entity.setHeightPlanJson(dto.getHeightPlanJson());
        entity.setTieBreakJson(dto.getTieBreakJson());
        entity.setDurationMin(dto.getDurationMin());
        entity.setIsInheritMeet(dto.getIsInheritMeet());
        entity.setExtraJson(dto.getExtraJson());

        // 唯一键 uk_evrule_event：不存在则插入、存在则更新（非空更新策略，null 字段保持原值以继续继承）
        EventRuleConfig exists = getRaw(eventId);
        if (exists == null) {
            this.save(entity);
            return entity.getId();
        }
        entity.setId(exists.getId());
        entity.setVersion(exists.getVersion());
        if (!this.updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "项目级规则已被其他用户修改，请刷新后重试");
        }
        return exists.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetRuleConfig(Long eventId) {
        Event event = eventService.getEntity(eventId);
        int rows = baseMapper.deleteByEventId(event.getMeetId(), eventId);
        return rows > 0;
    }

    /**
     * 入参业务校验：JSON 列合法性 + 数值范围。
     */
    private void validate(EventRuleConfigSaveDTO dto) {
        RuleJsonValidator.validate(dto.getLaneOrderJson(), "道次顺序");
        RuleJsonValidator.validate(dto.getWindJson(), "风速规则");
        RuleJsonValidator.validate(dto.getHeightPlanJson(), "升杆方案");
        RuleJsonValidator.validate(dto.getTieBreakJson(), "平局规则");
        RuleJsonValidator.validate(dto.getExtraJson(), "扩展规则");
        if (dto.getAdmitRank() != null && dto.getAdmitRank() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "录取名次必须大于 0");
        }
        if (dto.getConflictIntervalMin() != null && dto.getConflictIntervalMin() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "兼项间隔不能为负数");
        }
        if (dto.getAttemptCount() != null && dto.getAttemptCount() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "田赛试跳次数必须大于 0");
        }
        if (dto.getFinalAttemptCount() != null && dto.getFinalAttemptCount() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "田赛决赛试跳次数必须大于 0");
        }
        if (dto.getLaneCount() != null && (dto.getLaneCount() < 1 || dto.getLaneCount() > 12)) {
            throw new BizException(ResultCode.PARAM_ERROR, "道数需在 1 ~ 12 之间");
        }
        if (dto.getDurationMin() != null && dto.getDurationMin() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目预计时长必须大于 0");
        }
        if (dto.getDrawGroupSize() != null && dto.getDrawGroupSize() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "分组每组人数必须大于 0");
        }
    }

    // ------------------------------------------------------------------
    // 三元解析：项目级 → 届次级 → 系统兜底默认，同时回写值及其来源标记
    // ------------------------------------------------------------------

    /** Integer 字段解析（项目级非空 → PROJECT；否则届次级非空 → MEET；否则默认值 → DEFAULT） */
    private void pickInt(EventRuleConfigVO vo, Integer projectValue, Integer meetValue, Integer defaultValue,
                         BiConsumer<EventRuleConfigVO, Integer> valueSetter,
                         BiConsumer<EventRuleConfigVO, String> sourceSetter) {
        if (projectValue != null) {
            valueSetter.accept(vo, projectValue);
            sourceSetter.accept(vo, SOURCE_PROJECT);
            return;
        }
        if (meetValue != null) {
            valueSetter.accept(vo, meetValue);
            sourceSetter.accept(vo, SOURCE_MEET);
            return;
        }
        valueSetter.accept(vo, defaultValue);
        sourceSetter.accept(vo, SOURCE_DEFAULT);
    }

    /** String（含 JSON 文本）字段解析：空白视为未配置 */
    private void pickString(EventRuleConfigVO vo, String projectValue, String meetValue, String defaultValue,
                            BiConsumer<EventRuleConfigVO, String> valueSetter,
                            BiConsumer<EventRuleConfigVO, String> sourceSetter) {
        if (hasText(projectValue)) {
            valueSetter.accept(vo, projectValue);
            sourceSetter.accept(vo, SOURCE_PROJECT);
            return;
        }
        if (hasText(meetValue)) {
            valueSetter.accept(vo, meetValue);
            sourceSetter.accept(vo, SOURCE_MEET);
            return;
        }
        valueSetter.accept(vo, defaultValue);
        sourceSetter.accept(vo, SOURCE_DEFAULT);
    }

    /** BigDecimal 字段解析 */
    private void pickDecimal(EventRuleConfigVO vo, BigDecimal projectValue, BigDecimal meetValue,
                             BigDecimal defaultValue,
                             BiConsumer<EventRuleConfigVO, BigDecimal> valueSetter,
                             BiConsumer<EventRuleConfigVO, String> sourceSetter) {
        if (projectValue != null) {
            valueSetter.accept(vo, projectValue);
            sourceSetter.accept(vo, SOURCE_PROJECT);
            return;
        }
        if (meetValue != null) {
            valueSetter.accept(vo, meetValue);
            sourceSetter.accept(vo, SOURCE_MEET);
            return;
        }
        valueSetter.accept(vo, defaultValue);
        sourceSetter.accept(vo, SOURCE_DEFAULT);
    }

    /** 非空白判定 */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
