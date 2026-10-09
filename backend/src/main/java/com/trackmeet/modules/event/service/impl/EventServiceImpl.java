package com.trackmeet.modules.event.service.impl;

import com.trackmeet.modules.event.mapper.EventRoundMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.dto.EventBatchGenerateDTO;
import com.trackmeet.modules.event.dto.EventQuery;
import com.trackmeet.modules.event.dto.EventSaveDTO;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.mapper.EventMapper;
import com.trackmeet.modules.event.service.EventRoundService;
import com.trackmeet.modules.event.service.EventRuleService;
import com.trackmeet.modules.event.service.EventService;
import com.trackmeet.modules.event.service.MeetRuleConfigService;
import com.trackmeet.modules.event.vo.EventBatchGenerateResultVO;
import com.trackmeet.modules.event.vo.EventDetailVO;
import com.trackmeet.modules.event.vo.EventVO;
import com.trackmeet.modules.meet.entity.MeetGroup;
import com.trackmeet.modules.meet.mapper.MeetGroupMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目服务实现：项目 CRUD、详情聚合与批量生成标准项目。
 * <p>
 * 业务阈值全部来自配置表（{@code event_schedule_rule} / {@code event_rule_config} / {@code meet_rule_config}），
 * 本类仅保留「批量生成模板」这一与具体学校无关的标准项目清单（100 米 / 200 米 / … / 4×100 米接力）。
 *
 * @author TrackMeet
 */
@Service
public class EventServiceImpl extends ServiceImpl<EventMapper, Event> implements EventService {

    /** 性别：男子 */
    private static final String GENDER_MALE = "MALE";
    /** 性别：女子 */
    private static final String GENDER_FEMALE = "FEMALE";
    /** 性别：混合 */
    private static final String GENDER_MIXED = "MIXED";

    /** 兜底默认：event_code 列长度上限（VARCHAR(64)） */
    private static final int CODE_MAX_LENGTH = 64;

    /** 逻辑删除时唯一键后缀前缀 */
    private static final String DELETED_SUFFIX = "_d";

    /** 性别中文后缀（用于项目名称拼接，如「100米(男子)」） */
    private static final Map<String, String> GENDER_LABEL = Map.of(
            GENDER_MALE, "男子",
            GENDER_FEMALE, "女子",
            GENDER_MIXED, "混合");

    /**
     * 标准项目模板：项目名称 → 类型 / 子类型 / 接力标记 / 接力人数 / 预计时长 / 单位 / 风速适用。
     * 时长与顺序的更权威默认值来自全局表 {@code event_schedule_rule}（按类型 × 子类型），此处为模板内置初值。
     */
    private static final Map<String, EventTemplate> TEMPLATES = buildTemplates();

    private final MeetGroupMapper meetGroupMapper;
    private final EventRoundMapper eventRoundMapper;
    private final EventRoundService eventRoundService;
    private final EventRuleService eventRuleService;
    private final MeetRuleConfigService meetRuleConfigService;

    /**
     * 构造注入。
     * <p>
     * {@code eventRoundService} / {@code eventRuleService} 标注 {@code @Lazy}：
     * 这两个实现（以及 {@code EventRuleServiceImpl}）需要回调 {@link EventService} 做项目存在性校验，
     * 而本实现又需要它们提供轮次列表与规则视图，属于双向依赖；
     * 由 Spring 为它们注入延迟代理打破构造期循环，运行期首次调用时才真正解析目标 Bean。
     */
    public EventServiceImpl(MeetGroupMapper meetGroupMapper,
                            @Lazy EventRoundService eventRoundService,
                            @Lazy EventRuleService eventRuleService,
                            MeetRuleConfigService meetRuleConfigService) {
        this.meetGroupMapper = meetGroupMapper;
        this.eventRoundService = eventRoundService;
        this.eventRuleService = eventRuleService;
        this.meetRuleConfigService = meetRuleConfigService;
    }

    @Override
    public PageResult<EventVO> pageEvent(EventQuery query) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getMeetId() != null, Event::getMeetId, query.getMeetId())
                .eq(query.getGroupId() != null, Event::getGroupId, query.getGroupId())
                .eq(StringUtils.hasText(query.getEventType()), Event::getEventType, query.getEventType())
                .eq(StringUtils.hasText(query.getSubType()), Event::getSubType, query.getSubType())
                .eq(StringUtils.hasText(query.getGender()), Event::getGender, query.getGender())
                .eq(StringUtils.hasText(query.getStatus()), Event::getStatus, query.getStatus());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Event::getEventName, keyword).or().like(Event::getEventCode, keyword));
        }
        wrapper.orderByAsc(Event::getSortNo).orderByAsc(Event::getId);

        Page<Event> page = this.page(query.toPage(), wrapper);
        List<EventVO> records = toVoList(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public EventDetailVO getDetail(Long id) {
        Event event = getEntity(id);
        EventDetailVO detail = new EventDetailVO();
        detail.setEvent(toVoList(Collections.singletonList(event)).get(0));
        detail.setRounds(eventRoundService.listByEventId(id));
        detail.setRuleConfig(eventRuleService.getRuleConfig(id));
        detail.setMeetRuleConfig(meetRuleConfigService.getEffective(event.getMeetId()));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEvent(EventSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目参数不能为空");
        }
        Long meetId = resolveMeetId(dto.getMeetId());
        checkEventName(dto.getEventName());
        checkEventCode(dto.getEventCode());
        checkRelayRange(dto.getIsRelay(), dto.getRelayMin(), dto.getRelayMax());
        checkNumericRange(dto);

        Event entity = new Event();
        entity.setMeetId(meetId);
        applySaveDto(entity, dto);
        // 默认值：status=ENABLED、round_count=1（DDL 默认值同样如此）
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(Event.STATUS_ENABLED);
        }
        if (entity.getRoundCount() == null) {
            entity.setRoundCount(1);
        }
        if (entity.getIsRelay() == null) {
            entity.setIsRelay(0);
        }
        if (entity.getIsRelayDouble() == null) {
            entity.setIsRelayDouble(0);
        }
        if (entity.getWindApplicable() == null) {
            entity.setWindApplicable(0);
        }
        if (entity.getIsTeamScore() == null) {
            entity.setIsTeamScore(1);
        }
        checkMeetScopedUnique(meetId, entity.getEventCode(), null);
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEvent(Long id, EventSaveDTO dto) {
        Event exists = getEntity(id);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目参数不能为空");
        }
        if (dto.getMeetId() != null && !Objects.equals(dto.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改项目");
        }
        checkEventName(dto.getEventName());
        checkEventCode(dto.getEventCode());
        checkRelayRange(dto.getIsRelay(), dto.getRelayMin(), dto.getRelayMax());
        checkNumericRange(dto);
        checkMeetScopedUnique(exists.getMeetId(), dto.getEventCode(), id);

        Event entity = new Event();
        entity.setId(id);
        entity.setVersion(exists.getVersion());
        entity.setMeetId(exists.getMeetId());
        applySaveDto(entity, dto);
        if (!this.updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "项目已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(Long id) {
        Event event = getEntity(id);
        long registrationCount = baseMapper.countActiveRegistration(event.getMeetId(), id);
        if (registrationCount > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该项目已存在报名（" + registrationCount + " 条），禁止删除；请先取消或清理报名");
        }
        long scheduleCount = baseMapper.countScheduleReference(event.getMeetId(), id);
        if (scheduleCount > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该项目已被日程引用（" + scheduleCount + " 条），禁止删除；请先移除对应日程");
        }
        // 通过引用校验后，级联清理项目级规则与轮次级规则（均为物理删除，避免残留孤儿规则）
        baseMapper.deleteRuleConfigPhysically(event.getMeetId(), id);
        eventRoundMapper.deleteRoundRuleByEvent(event.getMeetId(), id);
        // 逻辑删除前改写 event_code 释放届次内唯一键（uk_event_meet_code），项目编码可在后续复用
        Event patch = new Event();
        patch.setId(id);
        patch.setEventCode(buildDeletedCode(event.getEventCode(), id));
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "项目已被其他用户修改，请刷新后重试");
        }
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventBatchGenerateResultVO batchGenerate(EventBatchGenerateDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "批量生成参数不能为空");
        }
        Long meetId = resolveMeetId(dto.getMeetId());
        String gender = dto.getGender();
        if (!GENDER_LABEL.containsKey(gender)) {
            throw new BizException(ResultCode.PARAM_ERROR, "性别码只能是 MALE/FEMALE/MIXED");
        }
        List<Long> groupIds = dto.getGroupIds() == null
                ? Collections.emptyList()
                : dto.getGroupIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "组别列表不能为空");
        }
        if (dto.getTemplates() == null || dto.getTemplates().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目模板列表不能为空");
        }

        // 一次性取出届次内已存在的「名称 + 组别 + 性别」组合，避免逐个查询
        List<Event> existing = this.list(new LambdaQueryWrapper<Event>()
                .select(Event::getId, Event::getEventName, Event::getGroupId, Event::getGender)
                .eq(Event::getMeetId, meetId));
        Set<String> existsKeys = existing.stream()
                .map(e -> buildUniqueKey(e.getEventName(), e.getGroupId(), e.getGender()))
                .collect(Collectors.toSet());

        EventBatchGenerateResultVO result = new EventBatchGenerateResultVO();
        List<Event> toSave = new ArrayList<>();
        int sortNo = nextSortNo(meetId);
        for (String templateCode : dto.getTemplates()) {
            EventTemplate template = resolveTemplate(templateCode);
            for (Long groupId : groupIds) {
                String eventName = buildEventName(template.name, gender);
                String uniqueKey = buildUniqueKey(eventName, groupId, gender);
                if (existsKeys.contains(uniqueKey)) {
                    result.addSkipped(eventName);
                    continue;
                }
                Event entity = new Event();
                entity.setMeetId(meetId);
                entity.setGroupId(groupId);
                entity.setGender(gender);
                entity.setEventName(eventName);
                entity.setEventCode(buildEventCode(template.code, gender));
                entity.setEventType(template.eventType);
                entity.setSubType(template.subType);
                entity.setIsRelay(template.relay ? 1 : 0);
                entity.setRelayMin(template.relay ? template.relayMin : null);
                entity.setRelayMax(template.relay ? template.relayMax : null);
                entity.setIsRelayDouble(template.relay ? 1 : 0);
                entity.setWindApplicable(template.windApplicable ? 1 : 0);
                entity.setIsTeamScore(1);
                entity.setDurationMin(template.durationMin);
                entity.setUnitOfMeasure(template.unitOfMeasure);
                entity.setRoundCount(1);
                entity.setSortNo(sortNo++);
                entity.setStatus(Event.STATUS_ENABLED);
                toSave.add(entity);
                existsKeys.add(uniqueKey);
                result.addCreated(eventName);
            }
        }
        if (!toSave.isEmpty()) {
            this.saveBatch(toSave);
        }
        return result;
    }

    @Override
    public Event getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目 ID 不能为空");
        }
        Event event = this.getById(id);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目不存在");
        }
        return event;
    }

    @Override
    public Long getMeetIdByEventId(Long id) {
        return getEntity(id).getMeetId();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 实体列表 → VO 列表，并冗余填充组别名称。
     */
    private List<EventVO> toVoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> groupIds = events.stream()
                .map(Event::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> groupNameMap = new HashMap<>();
        if (!groupIds.isEmpty()) {
            List<MeetGroup> groups = meetGroupMapper.selectBatchIds(groupIds);
            for (MeetGroup group : groups) {
                groupNameMap.put(group.getId(), group.getGroupName());
            }
        }
        return events.stream().map(event -> {
            EventVO vo = new EventVO();
            vo.setId(event.getId());
            vo.setMeetId(event.getMeetId());
            vo.setEventName(event.getEventName());
            vo.setEventCode(event.getEventCode());
            vo.setEventType(event.getEventType());
            vo.setSubType(event.getSubType());
            vo.setGroupId(event.getGroupId());
            vo.setGroupName(event.getGroupId() == null ? null : groupNameMap.get(event.getGroupId()));
            vo.setGender(event.getGender());
            vo.setIsRelay(event.getIsRelay());
            vo.setRelayMin(event.getRelayMin());
            vo.setRelayMax(event.getRelayMax());
            vo.setMaxPerUnit(event.getMaxPerUnit());
            vo.setDurationMin(event.getDurationMin());
            vo.setRoundCount(event.getRoundCount());
            vo.setWindApplicable(event.getWindApplicable());
            vo.setIsTeamScore(event.getIsTeamScore());
            vo.setStatus(event.getStatus());
            vo.setSortNo(event.getSortNo());
            vo.setRemark(event.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * DTO → 实体赋值（不含 meetId 与 id）。
     */
    private void applySaveDto(Event entity, EventSaveDTO dto) {
        entity.setEventName(dto.getEventName() == null ? null : dto.getEventName().trim());
        entity.setEventCode(dto.getEventCode() == null ? null : dto.getEventCode().trim());
        entity.setEventType(dto.getEventType());
        entity.setSubType(dto.getSubType());
        entity.setGroupId(dto.getGroupId());
        entity.setGender(dto.getGender());
        entity.setRoundCount(dto.getRoundCount());
        entity.setIsRelay(dto.getIsRelay());
        entity.setRelayMin(dto.getRelayMin());
        entity.setRelayMax(dto.getRelayMax());
        entity.setMaxPerUnit(dto.getMaxPerUnit());
        entity.setIsRelayDouble(dto.getIsRelayDouble());
        entity.setWindApplicable(dto.getWindApplicable());
        entity.setIsTeamScore(dto.getIsTeamScore());
        entity.setDurationMin(dto.getDurationMin());
        entity.setScoreRuleId(dto.getScoreRuleId());
        entity.setUnitOfMeasure(dto.getUnitOfMeasure());
        entity.setResultPrecision(dto.getResultPrecision());
        entity.setSortNo(dto.getSortNo());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
    }

    /**
     * 解析并校验届次 ID：入参优先，其次取当前届次上下文；与上下文不一致按越权处理。
     */
    private Long resolveMeetId(Long meetId) {
        Long contextMeetId = MeetContext.get();
        Long resolved = meetId != null ? meetId : contextMeetId;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次 ID 不能为空");
        }
        if (contextMeetId != null && !Objects.equals(contextMeetId, resolved)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的项目");
        }
        return resolved;
    }

    /**
     * UK(meet_id, event_code) 校验。
     */
    private void checkMeetScopedUnique(Long meetId, String eventCode, Long excludeId) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Event::getMeetId, meetId)
                .eq(Event::getEventCode, eventCode)
                .ne(excludeId != null, Event::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该届次下项目编码已存在：" + eventCode);
        }
    }

    private void checkEventName(String eventName) {
        if (!StringUtils.hasText(eventName)) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目名称不能为空");
        }
        if (eventName.trim().length() > 100) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目名称长度不能超过 100");
        }
    }

    private void checkEventCode(String eventCode) {
        if (!StringUtils.hasText(eventCode)) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目编码不能为空");
        }
        if (eventCode.trim().length() > 64) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目编码长度不能超过 64");
        }
    }

    private void checkRelayRange(Integer isRelay, Integer relayMin, Integer relayMax) {
        if (isRelay != null && isRelay == 1) {
            int min = relayMin == null ? 4 : relayMin;
            int max = relayMax == null ? 4 : relayMax;
            if (min < 1 || max < 1) {
                throw new BizException(ResultCode.PARAM_ERROR, "接力人数必须大于 0");
            }
            if (min > max) {
                throw new BizException(ResultCode.PARAM_ERROR, "接力最少人数不能大于最多人数");
            }
        }
    }

    private void checkNumericRange(EventSaveDTO dto) {
        if (dto.getDurationMin() != null && dto.getDurationMin() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目预计时长必须大于 0");
        }
        if (dto.getMaxPerUnit() != null && dto.getMaxPerUnit() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "每单位限报人数不能为负数");
        }
        if (dto.getRoundCount() != null && dto.getRoundCount() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "轮次数量不能为负数");
        }
        if (dto.getResultPrecision() != null && dto.getResultPrecision() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩小数位不能为负数");
        }
    }

    /**
     * 未指定排序号时按现有项目数量顺延。
     */
    private int nextSortNo(Long meetId) {
        long count = this.count(new LambdaQueryWrapper<Event>().eq(Event::getMeetId, meetId));
        return (int) count + 1;
    }

    /**
     * 模板去重键：项目名称 + 组别 + 性别。
     */
    private String buildUniqueKey(String eventName, Long groupId, String gender) {
        return eventName + "#" + groupId + "#" + gender;
    }

    /**
     * 生成改写后的唯一键：原值 + _d{id}，超长时截断原值以保证不超出列长度。
     * <p>
     * 与 {@code MeetGroupServiceImpl} 的「逻辑删除时改写唯一键」策略保持一致。
     *
     * @param code 原项目编码
     * @param id   项目 ID
     * @return 改写后的项目编码
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

    /**
     * 项目显示名：模板名 + 性别后缀。
     */
    private String buildEventName(String templateName, String gender) {
        return templateName + "(" + GENDER_LABEL.get(gender) + ")";
    }

    /**
     * 项目编码：模板编码_性别码（大写），届次内唯一。
     */
    private String buildEventCode(String templateCode, String gender) {
        return templateCode + "_" + gender;
    }

    /**
     * 解析模板：支持中文模板名（100米…）与等价的英文模板码（100M…），大小写与空白容错。
     */
    private EventTemplate resolveTemplate(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目模板不能为空");
        }
        EventTemplate template = TEMPLATES.get(templateCode.trim());
        if (template == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的项目模板：" + templateCode
                    + "，可选值：" + String.join("/", TEMPLATES.keySet()));
        }
        return template;
    }

    /**
     * 构建标准项目模板表（中文名与英文码双键指向同一模板）。
     * <p>
     * 第 6、7 位参数为接力人数下限 / 上限（非接力项目传 {@code null}）；
     * 本清单是「标准项目目录」，不含任何成绩或晋级阈值。
     */
    private static Map<String, EventTemplate> buildTemplates() {
        Map<String, EventTemplate> templates = new LinkedHashMap<>();
        put(templates, "100米", "100M", "TRACK", "SPRINT", false, null, null, 15, "s", true);
        put(templates, "200米", "200M", "TRACK", "SPRINT", false, null, null, 15, "s", true);
        put(templates, "400米", "400M", "TRACK", "SPRINT", false, null, null, 20, "s", true);
        put(templates, "800米", "800M", "TRACK", "MIDDLE", false, null, null, 20, "s", true);
        put(templates, "1500米", "1500M", "TRACK", "MIDDLE", false, null, null, 30, "s", true);
        put(templates, "跳远", "LONG_JUMP", "FIELD", "JUMP", false, null, null, 30, "m", true);
        put(templates, "跳高", "HIGH_JUMP", "FIELD", "JUMP", false, null, null, 40, "m", true);
        put(templates, "铅球", "SHOT_PUT", "FIELD", "THROW", false, null, null, 30, "m", false);
        put(templates, "4×100米接力", "RELAY_4X100", "RELAY", "RELAY", true, 4, 4, 30, "s", false);
        return Collections.unmodifiableMap(templates);
    }

    /**
     * 模板登记（中文名 + 英文码双键）。
     */
    private static void put(Map<String, EventTemplate> templates, String name, String code,
                            String eventType, String subType, boolean relay,
                            Integer relayMin, Integer relayMax,
                            int durationMin, String unitOfMeasure, boolean windApplicable) {
        EventTemplate template = new EventTemplate(name, code, eventType, subType, relay,
                relayMin, relayMax, durationMin, unitOfMeasure, windApplicable);
        templates.put(name, template);
        templates.put(code, template);
    }

    /** 标准项目模板 */
    private static final class EventTemplate {

        /** 项目模板名称（中文，如 100米） */
        private final String name;
        /** 项目模板编码（英文，如 100M） */
        private final String code;
        /** 项目类型 */
        private final String eventType;
        /** 项目子类型 */
        private final String subType;
        /** 是否接力 */
        private final boolean relay;
        /** 接力最少人数（非接力项目为 null） */
        private final Integer relayMin;
        /** 接力最多人数（非接力项目为 null） */
        private final Integer relayMax;
        /** 预计时长（分钟） */
        private final int durationMin;
        /** 计量单位 */
        private final String unitOfMeasure;
        /** 是否记录风速 */
        private final boolean windApplicable;

        private EventTemplate(String name, String code, String eventType, String subType,
                             boolean relay, Integer relayMin, Integer relayMax,
                             int durationMin, String unitOfMeasure, boolean windApplicable) {
            this.name = name;
            this.code = code;
            this.eventType = eventType;
            this.subType = subType;
            this.relay = relay;
            this.relayMin = relayMin;
            this.relayMax = relayMax;
            this.durationMin = durationMin;
            this.unitOfMeasure = unitOfMeasure;
            this.windApplicable = windApplicable;
        }
    }
}
