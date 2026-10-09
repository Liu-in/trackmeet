package com.trackmeet.modules.athlete.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.athlete.dto.RegistrationApproveDTO;
import com.trackmeet.modules.athlete.dto.RegistrationBatchDTO;
import com.trackmeet.modules.athlete.dto.RegistrationCheckDTO;
import com.trackmeet.modules.athlete.dto.RegistrationQuery;
import com.trackmeet.modules.athlete.dto.RegistrationRejectDTO;
import com.trackmeet.modules.athlete.dto.RegistrationSaveDTO;
import com.trackmeet.modules.athlete.entity.Athlete;
import com.trackmeet.modules.athlete.entity.AthleteEventCount;
import com.trackmeet.modules.athlete.entity.Registration;
import com.trackmeet.modules.athlete.entity.RegistrationLimit;
import com.trackmeet.modules.athlete.entity.RelayMember;
import com.trackmeet.modules.athlete.mapper.AthleteEventCountMapper;
import com.trackmeet.modules.athlete.mapper.AthleteMapper;
import com.trackmeet.modules.athlete.mapper.RegistrationLimitMapper;
import com.trackmeet.modules.athlete.mapper.RegistrationMapper;
import com.trackmeet.modules.athlete.mapper.RelayMemberMapper;
import com.trackmeet.modules.athlete.service.RegistrationLimitValidator;
import com.trackmeet.modules.athlete.service.RegistrationService;
import com.trackmeet.modules.athlete.vo.EventBriefVO;
import com.trackmeet.modules.athlete.vo.RegistrationCheckItemVO;
import com.trackmeet.modules.athlete.vo.RegistrationCheckResultVO;
import com.trackmeet.modules.athlete.vo.RegistrationStatVO;
import com.trackmeet.modules.athlete.vo.RegistrationVO;
import com.trackmeet.modules.athlete.vo.RegistrationWarningVO;
import com.trackmeet.modules.meet.entity.Meet;
import com.trackmeet.modules.meet.entity.MeetGroup;
import com.trackmeet.modules.meet.mapper.MeetGroupMapper;
import com.trackmeet.modules.meet.service.MeetService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报名业务实现。
 * <p>
 * 关键设计：
 * <ol>
 *   <li>写入前统一走 {@link RegistrationLimitValidator}（纯校验），存在 ERROR 直接抛业务异常并取第一条 ERROR 作为消息；</li>
 *   <li>届次隔离：MyBatis-Plus 多租户拦截器按 {@code MeetContext} 追加 meet_id，服务层再做一次一致性校验（不一致抛 FORBIDDEN）；</li>
 *   <li>计数器 {@code registration_limit} / {@code athlete_event_count} 为派生数据，
 *       刷新采用「先物理删除本键行、再按实时统计插入」的幂等写法（逻辑删除会残留唯一键占位行）；</li>
 *   <li>取消报名时释放唯一键（个人置空 athlete_id、接力置空 relay_team_seq），
 *       以符合「CANCELLED 不算重复报名」的规则并允许重新报名。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl extends ServiceImpl<RegistrationMapper, Registration>
        implements RegistrationService {

    /** 校验快照时间格式 */
    private static final DateTimeFormatter CHECK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 退回原因接口约定长度上限 */
    private static final int REJECT_REASON_MAX_LENGTH = 200;

    /** 退回原因落库列长度（registration.reject_reason VARCHAR(100)） */
    private static final int REJECT_REASON_COLUMN_LENGTH = 100;

    /** 报名不足预警线：已通过报名数低于该值即预警（FR-ATH-12，低于 3 人/队） */
    private static final int MIN_APPROVED_ALERT = 3;

    /** 未校验问题码（校验结果清单中标识尚无快照的报名） */
    private static final String CODE_UNCHECKED = "UNCHECKED";

    /** 统计维度：按单位 */
    private static final String DIMENSION_UNIT = "UNIT";
    /** 统计维度：按项目 */
    private static final String DIMENSION_EVENT = "EVENT";
    /** 统计维度：按组别 */
    private static final String DIMENSION_GROUP = "GROUP";

    private final RegistrationLimitValidator limitValidator;
    private final RelayMemberMapper relayMemberMapper;
    private final RegistrationLimitMapper registrationLimitMapper;
    private final AthleteEventCountMapper athleteEventCountMapper;
    private final AthleteMapper athleteMapper;
    private final MeetGroupMapper meetGroupMapper;
    private final MeetService meetService;
    private final ObjectMapper objectMapper;

    // ==================================================================
    // 查询
    // ==================================================================

    @Override
    public PageResult<RegistrationVO> pageRegistration(RegistrationQuery query) {
        RegistrationQuery condition = (query == null) ? new RegistrationQuery() : query;
        Long meetId = requireMeetId(condition.getMeetId());
        Page<RegistrationVO> pageParam = condition.toPage();
        IPage<RegistrationVO> page = baseMapper.selectVoPage(pageParam, condition, meetId);
        List<RegistrationVO> records = page.getRecords();
        fillStatusNames(records);
        fillCheckStatus(records);
        fillRelayMembers(records);
        return PageResult.of(page);
    }

    @Override
    public RegistrationVO getRegistrationDetail(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名 ID 不能为空");
        }
        Registration entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        // 未显式传入届次时以记录自身届次为准，同时校验与上下文一致（不一致抛 FORBIDDEN）
        Long meetId = requireMeetId(entity.getMeetId());
        RegistrationVO vo = baseMapper.selectVoById(id, meetId);
        if (vo == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        List<RegistrationVO> records = new ArrayList<>();
        records.add(vo);
        fillStatusNames(records);
        fillCheckStatus(records);
        fillRelayMembers(records);
        return vo;
    }

    @Override
    public RegistrationCheckResultVO getCheckResult(RegistrationQuery query) {
        RegistrationQuery condition = (query == null) ? new RegistrationQuery() : query;
        Long meetId = requireMeetId(condition.getMeetId());

        LambdaQueryWrapper<Registration> wrapper = Wrappers.<Registration>lambdaQuery()
                .eq(Registration::getMeetId, meetId)
                .eq(condition.getEventId() != null, Registration::getEventId, condition.getEventId())
                .eq(condition.getUnitId() != null, Registration::getUnitId, condition.getUnitId())
                .eq(condition.getGroupId() != null, Registration::getGroupId, condition.getGroupId())
                .eq(condition.getAthleteId() != null, Registration::getAthleteId, condition.getAthleteId())
                .eq(StringUtils.hasText(condition.getStatus()), Registration::getStatus, condition.getStatus())
                .eq(condition.getIsRelay() != null, Registration::getIsRelay, condition.getIsRelay())
                .ne(Registration::getStatus, Registration.STATUS_CANCELLED)
                .orderByDesc(Registration::getSubmitTime)
                .orderByDesc(Registration::getId);
        List<Registration> registrations = list(wrapper);

        RegistrationCheckResultVO result = new RegistrationCheckResultVO();
        if (registrations.isEmpty()) {
            return result;
        }

        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Long> athleteIds = new LinkedHashSet<>();
        for (Registration registration : registrations) {
            if (registration.getEventId() != null) {
                eventIds.add(registration.getEventId());
            }
            if (registration.getAthleteId() != null) {
                athleteIds.add(registration.getAthleteId());
            }
        }
        Map<Long, String> eventNames = loadEventNames(meetId, eventIds);
        Map<Long, String> athleteNames = loadAthleteNames(athleteIds);

        List<RegistrationCheckItemVO> items = new ArrayList<>();
        int passCount = 0;
        int failCount = 0;
        for (Registration registration : registrations) {
            List<RegistrationCheckItemVO> saved = parseCheckItems(registration.getCheckResultJson());
            if (saved.isEmpty()) {
                RegistrationCheckItemVO item = new RegistrationCheckItemVO();
                item.setAthleteId(registration.getAthleteId());
                item.setAthleteName(athleteNames.get(registration.getAthleteId()));
                item.setEventId(registration.getEventId());
                item.setEventName(eventNames.get(registration.getEventId()));
                item.setLevel(RegistrationCheckItemVO.LEVEL_WARN);
                item.setCode(CODE_UNCHECKED);
                item.setMessage("该报名尚未执行限报校验");
                items.add(item);
                failCount++;
                continue;
            }
            boolean hasError = false;
            for (RegistrationCheckItemVO item : saved) {
                if (item.getEventId() == null) {
                    item.setEventId(registration.getEventId());
                }
                if (item.getEventName() == null) {
                    item.setEventName(eventNames.get(item.getEventId()));
                }
                if (item.getAthleteName() == null && item.getAthleteId() != null) {
                    item.setAthleteName(athleteNames.get(item.getAthleteId()));
                }
                if (RegistrationCheckItemVO.LEVEL_ERROR.equals(item.getLevel())) {
                    hasError = true;
                }
            }
            items.addAll(saved);
            if (hasError) {
                failCount++;
            } else {
                passCount++;
            }
        }
        result.setItems(items);
        result.setPassCount(passCount);
        result.setFailCount(failCount);
        return result;
    }

    // ==================================================================
    // 写入
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRegistration(RegistrationSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名信息不能为空");
        }
        Long meetId = requireMeetId(dto.getMeetId());
        List<RegistrationSaveDTO> items = Collections.singletonList(dto);
        normalize(meetId, items, null);

        RegistrationLimitValidator.CheckContext context = buildCheckContext(meetId, items, null);
        List<RegistrationCheckItemVO> checkItems = limitValidator.validate(context);
        if (limitValidator.hasError(checkItems)) {
            throw new BizException(mapResultCode(limitValidator.firstErrorCode(checkItems)),
                    limitValidator.firstErrorMessage(checkItems));
        }

        Registration registration = saveOne(meetId, dto, checkItems);
        refreshCounters(meetId, registration.getUnitId(), registration.getEventId());
        return registration.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchRegistration(RegistrationBatchDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "批量报名信息不能为空");
        }
        Long meetId = requireMeetId(dto.getMeetId());
        if (dto.getEventId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名项目不能为空");
        }
        if (dto.getAthleteIds() == null || dto.getAthleteIds().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员列表不能为空");
        }
        EventBriefVO event = baseMapper.selectEventBrief(dto.getEventId(), meetId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名项目不存在或不属于当前届次");
        }
        boolean relay = (dto.getIsRelay() != null && dto.getIsRelay() == 1)
                || Integer.valueOf(1).equals(event.getIsRelay());

        List<RegistrationSaveDTO> items = new ArrayList<>();
        if (relay) {
            RegistrationSaveDTO item = new RegistrationSaveDTO();
            item.setMeetId(meetId);
            item.setEventId(dto.getEventId());
            item.setUnitId(dto.getUnitId());
            item.setIsRelay(1);
            item.setRelayName(dto.getRelayName());
            item.setRemark(dto.getRemark());
            List<RelayMember> members = new ArrayList<>();
            int legNo = 0;
            for (Long athleteId : dto.getAthleteIds()) {
                if (athleteId == null) {
                    continue;
                }
                RelayMember member = new RelayMember();
                member.setAthleteId(athleteId);
                member.setLegNo(++legNo);
                members.add(member);
            }
            item.setMembers(members);
            items.add(item);
        } else {
            for (Long athleteId : dto.getAthleteIds()) {
                if (athleteId == null) {
                    continue;
                }
                RegistrationSaveDTO item = new RegistrationSaveDTO();
                item.setMeetId(meetId);
                item.setEventId(dto.getEventId());
                item.setAthleteId(athleteId);
                item.setUnitId(dto.getUnitId());
                item.setIsRelay(0);
                item.setRemark(dto.getRemark());
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员列表不能为空");
        }
        normalize(meetId, items, dto.getUnitId());

        if (relay) {
            RegistrationSaveDTO item = items.get(0);
            if (item.getRelayTeamSeq() == null && item.getUnitId() != null) {
                Integer maxSeq = baseMapper.selectMaxRelayTeamSeq(meetId, item.getUnitId(), dto.getEventId());
                item.setRelayTeamSeq(maxSeq == null ? 1 : maxSeq + 1);
            }
        }

        RegistrationLimitValidator.CheckContext context = buildCheckContext(meetId, items, null);
        List<RegistrationCheckItemVO> checkItems = limitValidator.validate(context);
        if (limitValidator.hasError(checkItems)) {
            throw new BizException(mapResultCode(limitValidator.firstErrorCode(checkItems)),
                    limitValidator.firstErrorMessage(checkItems));
        }

        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            Registration registration = saveOne(meetId, items.get(index), filterItems(checkItems, index));
            ids.add(registration.getId());
            refreshCounters(meetId, registration.getUnitId(), registration.getEventId());
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRegistration(Long id, RegistrationSaveDTO dto) {
        if (id == null || dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名 ID 与报名信息不能为空");
        }
        Registration exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        Long meetId = requireMeetId(dto.getMeetId() != null ? dto.getMeetId() : exists.getMeetId());
        if (!meetId.equals(exists.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权修改其他届次的报名");
        }
        if (Registration.STATUS_CANCELLED.equals(exists.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "已取消的报名不允许修改");
        }

        boolean relay = Integer.valueOf(1).equals(exists.getIsRelay());
        dto.setMeetId(meetId);
        dto.setEventId(exists.getEventId());
        dto.setIsRelay(relay ? 1 : 0);
        dto.setAthleteId(exists.getAthleteId());
        if (dto.getUnitId() == null) {
            dto.setUnitId(exists.getUnitId());
        }
        if (dto.getGroupId() == null) {
            dto.setGroupId(exists.getGroupId());
        }
        if (dto.getRelayTeamSeq() == null) {
            dto.setRelayTeamSeq(exists.getRelayTeamSeq());
        }

        List<Long> newMemberIds = relay ? resolveMemberIds(dto) : new ArrayList<>();
        boolean changeMembers = relay && !newMemberIds.isEmpty();

        Set<Long> affectedAthleteIds = new LinkedHashSet<>();
        List<RelayMember> oldMembers = new ArrayList<>();
        if (relay) {
            oldMembers = relayMemberMapper.selectList(Wrappers.<RelayMember>lambdaQuery()
                    .eq(RelayMember::getRegistrationId, id));
            for (RelayMember member : oldMembers) {
                if (member.getAthleteId() != null) {
                    affectedAthleteIds.add(member.getAthleteId());
                }
            }
        }
        affectedAthleteIds.addAll(newMemberIds);

        if (relay) {
            // 未提交 members 时沿用既有队员参与校验（只改种子成绩的场景不应被「接力队员缺失」阻断）
            List<RelayMember> members = new ArrayList<>();
            if (changeMembers) {
                for (Long memberId : newMemberIds) {
                    RelayMember member = new RelayMember();
                    member.setAthleteId(memberId);
                    members.add(member);
                }
            } else {
                for (RelayMember old : oldMembers) {
                    RelayMember member = new RelayMember();
                    member.setAthleteId(old.getAthleteId());
                    member.setLegNo(old.getLegNo());
                    members.add(member);
                }
            }
            dto.setMembers(members);
        }

        RegistrationLimitValidator.CheckContext context = buildCheckContext(meetId, Collections.singletonList(dto), id);
        List<RegistrationCheckItemVO> checkItems = limitValidator.validate(context);
        if (limitValidator.hasError(checkItems)) {
            throw new BizException(mapResultCode(limitValidator.firstErrorCode(checkItems)),
                    limitValidator.firstErrorMessage(checkItems));
        }

        applyEditableFields(id, meetId, dto, relay, checkItems);
        if (changeMembers) {
            relayMemberMapper.deletePhysicalByRegistrationId(meetId, id);
            insertRelayMembers(meetId, id, dto.getUnitId(), dto.getGroupId(), dto);
        }
        refreshCounters(meetId, exists.getUnitId(), exists.getEventId(), affectedAthleteIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRegistration(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名 ID 不能为空");
        }
        Registration exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        Long meetId = requireMeetId(exists.getMeetId());
        if (Registration.STATUS_CANCELLED.equals(exists.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "该报名已取消");
        }

        Set<Long> affectedAthleteIds = new LinkedHashSet<>();
        List<RelayMember> members = relayMemberMapper.selectList(Wrappers.<RelayMember>lambdaQuery()
                .eq(RelayMember::getRegistrationId, id));
        for (RelayMember member : members) {
            if (member.getAthleteId() != null) {
                affectedAthleteIds.add(member.getAthleteId());
            }
        }

        // 状态置 CANCELLED，并释放唯一键：
        // UK(meet_id, event_id, athlete_id) 与 UK(meet_id, event_id, unit_id, relay_team_seq)
        // 不含 deleted 列，逻辑删除后残留行会阻塞重新报名，故按「改写唯一键」策略置空对应键列。
        LambdaUpdateWrapper<Registration> wrapper = Wrappers.<Registration>lambdaUpdate()
                .eq(Registration::getId, id)
                .set(Registration::getStatus, Registration.STATUS_CANCELLED)
                .set(Registration::getAthleteId, null)
                .set(Registration::getRelayTeamSeq, null)
                .set(Registration::getUpdateTime, LocalDateTime.now());
        Long userId = SecurityUtils.getUserId();
        if (userId != null) {
            wrapper.set(Registration::getUpdateBy, userId);
        }
        update(wrapper);
        relayMemberMapper.deletePhysicalByRegistrationId(meetId, id);
        removeById(id);

        refreshCounters(meetId, exists.getUnitId(), exists.getEventId(), affectedAthleteIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(RegistrationApproveDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "报名 ID 列表不能为空");
        }
        // 先加载目标记录：届次取记录自身（批量内必须同届次），并与上下文交叉校验
        List<Registration> targets = new ArrayList<>();
        Long contextMeetId = MeetContext.get();
        Long meetId = null;
        for (Long id : dto.getIds()) {
            if (id == null) {
                continue;
            }
            Registration exists = getById(id);
            if (exists == null) {
                throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在：" + id);
            }
            if (exists.getMeetId() == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "报名记录缺少届次信息：" + id);
            }
            if (meetId == null) {
                meetId = exists.getMeetId();
            } else if (!meetId.equals(exists.getMeetId())) {
                throw new BizException(ResultCode.CONFLICT, "不允许跨届次批量审核报名");
            }
            if (contextMeetId != null && !contextMeetId.equals(exists.getMeetId())) {
                throw new BizException(ResultCode.FORBIDDEN, "无权审核其他届次的报名");
            }
            if (Registration.STATUS_CANCELLED.equals(exists.getStatus())) {
                throw new BizException(ResultCode.CONFLICT, "已取消的报名不允许审核：" + id);
            }
            targets.add(exists);
        }
        if (targets.isEmpty()) {
            return 0;
        }

        Long userId = SecurityUtils.getUserId();
        LocalDateTime now = LocalDateTime.now();
        Set<String> counterKeys = new LinkedHashSet<>();
        int approvedCount = 0;
        for (Registration exists : targets) {
            if (!Registration.STATUS_APPROVED.equals(exists.getStatus())) {
                LambdaUpdateWrapper<Registration> wrapper = Wrappers.<Registration>lambdaUpdate()
                        .eq(Registration::getId, exists.getId())
                        .set(Registration::getStatus, Registration.STATUS_APPROVED)
                        .set(Registration::getRejectReason, null)
                        .set(Registration::getAuditBy, userId)
                        .set(Registration::getAuditTime, now)
                        .set(Registration::getUpdateTime, now);
                if (userId != null) {
                    wrapper.set(Registration::getUpdateBy, userId);
                }
                update(wrapper);
            }
            approvedCount++;
            counterKeys.add(RegistrationLimitValidator.unitEventKey(exists.getUnitId(), exists.getEventId()));
        }
        for (String key : counterKeys) {
            Long unitId = parseKeyPart(key, 0);
            Long eventId = parseKeyPart(key, 1);
            refreshCounters(meetId, unitId, eventId);
        }
        return approvedCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, RegistrationRejectDTO dto) {
        if (id == null || dto == null || !StringUtils.hasText(dto.getRejectReason())) {
            throw new BizException(ResultCode.PARAM_ERROR, "退回原因不能为空");
        }
        String reason = dto.getRejectReason().trim();
        if (reason.length() > REJECT_REASON_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "退回原因长度不能超过 " + REJECT_REASON_MAX_LENGTH);
        }
        if (reason.length() > REJECT_REASON_COLUMN_LENGTH) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "退回原因长度不能超过 " + REJECT_REASON_COLUMN_LENGTH
                            + "（registration.reject_reason 列长度）");
        }
        Registration exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "报名记录不存在");
        }
        Long meetId = requireMeetId(exists.getMeetId());
        if (Registration.STATUS_CANCELLED.equals(exists.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "已取消的报名不允许审核");
        }
        Long userId = SecurityUtils.getUserId();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Registration> wrapper = Wrappers.<Registration>lambdaUpdate()
                .eq(Registration::getId, id)
                .set(Registration::getStatus, Registration.STATUS_REJECTED)
                .set(Registration::getRejectReason, reason)
                .set(Registration::getAuditBy, userId)
                .set(Registration::getAuditTime, now)
                .set(Registration::getUpdateTime, now);
        if (userId != null) {
            wrapper.set(Registration::getUpdateBy, userId);
        }
        update(wrapper);
        refreshCounters(meetId, exists.getUnitId(), exists.getEventId());
    }

    // ==================================================================
    // 校验
    // ==================================================================

    @Override
    public RegistrationCheckResultVO check(RegistrationCheckDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "待校验的报名明细不能为空");
        }
        Long meetId = requireMeetId(dto.getMeetId());
        normalize(meetId, dto.getItems(), null);
        RegistrationLimitValidator.CheckContext context = buildCheckContext(meetId, dto.getItems(), null);
        return limitValidator.validateResult(context);
    }

    // ==================================================================
    // 统计与预警
    // ==================================================================

    @Override
    public List<RegistrationStatVO> statistics(String dimension) {
        Long meetId = requireMeetId(null);
        String value = StringUtils.hasText(dimension) ? dimension.trim().toUpperCase() : DIMENSION_UNIT;
        switch (value) {
            case DIMENSION_UNIT:
                return baseMapper.selectStatByUnit(meetId);
            case DIMENSION_EVENT:
                return baseMapper.selectStatByEvent(meetId);
            case DIMENSION_GROUP:
                return baseMapper.selectStatByGroup(meetId);
            default:
                throw new BizException(ResultCode.PARAM_ERROR,
                        "统计维度只能是 UNIT、EVENT 或 GROUP，当前：" + dimension);
        }
    }

    @Override
    public List<RegistrationWarningVO> warnings() {
        Long meetId = requireMeetId(null);
        List<RegistrationWarningVO> list = baseMapper.selectWarnings(meetId, MIN_APPROVED_ALERT);
        if (list == null) {
            return new ArrayList<>();
        }
        for (RegistrationWarningVO warning : list) {
            int approved = warning.getApproved() == null ? 0 : warning.getApproved();
            int total = warning.getTotal() == null ? 0 : warning.getTotal();
            warning.setMessage("已通过报名 " + approved + " 人/队（共 " + total + " 条，已取消不计），低于预警线 "
                    + MIN_APPROVED_ALERT + " 人/队");
        }
        return list;
    }

    // ==================================================================
    // 报名截止锁定
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockRegistration() {
        lockRegistration(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockRegistration(Long meetId) {
        Long targetMeetId = requireMeetId(meetId);
        Meet meet = meetService.getById(targetMeetId);
        if (meet == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运动会不存在");
        }
        if (!Meet.STATUS_REG_OPEN.equals(meet.getStatus())) {
            throw new BizException(ResultCode.MEET_STATUS_ERROR,
                    "仅报名中（REG_OPEN）的届次可截止锁定报名，当前状态：" + meet.getStatus());
        }
        meetService.changeStatus(targetMeetId, Meet.STATUS_REG_CLOSED);
    }

    // ==================================================================
    // 计数器刷新
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshCounters(Long meetId, Long unitId, Long eventId) {
        refreshCounters(meetId, unitId, eventId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshCounters(Long meetId, Long unitId, Long eventId, Collection<Long> extraAthleteIds) {
        if (meetId == null || unitId == null || eventId == null) {
            return;
        }
        EventBriefVO event = baseMapper.selectEventBrief(eventId, meetId);
        RegistrationMapper.MeetRules rules = baseMapper.selectMeetRules(meetId);
        Integer eventMax = (event == null) ? null : event.getMaxPerUnit();
        Integer meetMax = (rules == null) ? null : rules.getMaxPerUnit();
        int maxCount = (eventMax != null && eventMax > 0) ? eventMax : ((meetMax == null || meetMax <= 0) ? 0 : meetMax);

        registrationLimitMapper.deletePhysicalByKey(meetId, unitId, eventId);
        RegistrationLimit stat = baseMapper.selectUnitEventStat(meetId, unitId, eventId);
        int registered = (stat == null || stat.getRegisteredCount() == null) ? 0 : stat.getRegisteredCount();
        int relayRegistered = (stat == null || stat.getRelayRegisteredCount() == null)
                ? 0 : stat.getRelayRegisteredCount();

        RegistrationLimit row = new RegistrationLimit();
        row.setMeetId(meetId);
        row.setUnitId(unitId);
        row.setEventId(eventId);
        row.setGroupId(event == null ? null : event.getGroupId());
        row.setRegisteredCount(registered);
        row.setRelayRegisteredCount(relayRegistered);
        row.setMaxCount(maxCount > 0 ? maxCount : null);
        row.setRelayMaxCount(maxCount > 0 ? maxCount : null);
        row.setRemainCount(maxCount > 0 ? Math.max(maxCount - registered, 0) : null);
        row.setIsFull(maxCount > 0 && registered >= maxCount ? 1 : 0);
        row.setOverLimit(maxCount > 0 && registered > maxCount ? 1 : 0);
        row.setLimitSource(maxCount > 0
                ? ((eventMax != null && eventMax > 0) ? RegistrationLimit.LIMIT_SOURCE_EVENT : RegistrationLimit.LIMIT_SOURCE_MEET)
                : null);
        row.setLimitCheckTime(LocalDateTime.now());

        Set<Long> athleteIds = new LinkedHashSet<>();
        List<Long> directAthleteIds = baseMapper.selectAthleteIdsByUnitEvent(meetId, unitId, eventId);
        if (directAthleteIds != null) {
            for (Long athleteId : directAthleteIds) {
                if (athleteId != null) {
                    athleteIds.add(athleteId);
                }
            }
        }
        List<Long> relayAthleteIds = baseMapper.selectRelayAthleteIdsByUnitEvent(meetId, unitId, eventId);
        if (relayAthleteIds != null) {
            for (Long athleteId : relayAthleteIds) {
                if (athleteId != null) {
                    athleteIds.add(athleteId);
                }
            }
        }
        if (extraAthleteIds != null) {
            for (Long athleteId : extraAthleteIds) {
                if (athleteId != null) {
                    athleteIds.add(athleteId);
                }
            }
        }
        row.setAthleteEventCountJson(buildJsonArray(athleteIds));
        registrationLimitMapper.insert(row);

        refreshAthleteCounts(meetId, athleteIds, rules);
    }

    /**
     * 重算指定运动员的项数计数：先物理删除，再按实时统计插入；无有效报名则不保留计数行。
     */
    private void refreshAthleteCounts(Long meetId, Collection<Long> athleteIds, RegistrationMapper.MeetRules rules) {
        if (athleteIds == null || athleteIds.isEmpty()) {
            return;
        }
        Integer maxIndividual = (rules == null) ? null : rules.getAthleteMaxEvents();
        Integer maxRelay = (rules == null) ? null : rules.getRelayMaxPerAthlete();
        for (Long athleteId : athleteIds) {
            AthleteEventCount stat = baseMapper.selectAthleteEventStat(meetId, athleteId);
            athleteEventCountMapper.deletePhysicalByKey(meetId, athleteId);
            if (stat == null) {
                continue;
            }
            int individual = stat.getIndividualEventCount() == null ? 0 : stat.getIndividualEventCount();
            int relay = stat.getRelayEventCount() == null ? 0 : stat.getRelayEventCount();
            int allround = stat.getAllroundEventCount() == null ? 0 : stat.getAllroundEventCount();
            int total = stat.getEventCount() == null ? 0 : stat.getEventCount();
            int maxEvents = (maxIndividual == null || maxIndividual <= 0) ? 0 : maxIndividual;
            boolean overLimit = (maxEvents > 0 && individual > maxEvents)
                    || (maxRelay != null && maxRelay > 0 && relay > maxRelay);

            AthleteEventCount row = new AthleteEventCount();
            row.setMeetId(meetId);
            row.setAthleteId(athleteId);
            row.setUnitId(stat.getUnitId());
            row.setGroupId(stat.getGroupId());
            row.setEventCount(total);
            row.setIndividualEventCount(individual);
            row.setRelayEventCount(relay);
            row.setAllroundEventCount(allround);
            row.setMaxEvents(maxEvents > 0 ? maxEvents : null);
            row.setRemainCount(maxEvents > 0 ? Math.max(maxEvents - individual, 0) : null);
            row.setIsRelayCounted(1);
            row.setOverLimit(overLimit ? 1 : 0);
            row.setEventIdsJson(buildJsonArray(loadAthleteEventIds(meetId, athleteId)));
            row.setCountSource(maxEvents > 0 ? AthleteEventCount.COUNT_SOURCE_MEET : null);
            row.setLastRegisterTime(stat.getLastRegisterTime());
            row.setCheckTime(LocalDateTime.now());
            athleteEventCountMapper.insert(row);
        }
    }

    /**
     * 运动员已报项目 ID 明细（个人项目 + 接力项目）。
     */
    private Set<Long> loadAthleteEventIds(Long meetId, Long athleteId) {
        Set<Long> eventIds = new LinkedHashSet<>();
        List<Long> individual = baseMapper.selectEventIdsByAthlete(meetId, athleteId);
        if (individual != null) {
            eventIds.addAll(individual);
        }
        List<Long> relay = baseMapper.selectRelayEventIdsByAthlete(meetId, athleteId);
        if (relay != null) {
            eventIds.addAll(relay);
        }
        return eventIds;
    }

    // ==================================================================
    // 校验上下文装配
    // ==================================================================

    /**
     * 装配校验上下文：规则 + 项目 + 运动员 + 组别 + 既有报名键与计数（引擎只读，不查库）。
     */
    private RegistrationLimitValidator.CheckContext buildCheckContext(Long meetId, List<RegistrationSaveDTO> items,
                                                                     Long excludeRegistrationId) {
        Meet meet = meetService.getById(meetId);
        if (meet == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运动会不存在");
        }
        RegistrationLimitValidator.CheckContext context = new RegistrationLimitValidator.CheckContext();
        context.setMeetId(meetId);
        context.setMeetStatus(meet.getStatus());

        RegistrationMapper.MeetRules rules = baseMapper.selectMeetRules(meetId);
        if (rules != null) {
            context.setAthleteMaxEvents(rules.getAthleteMaxEvents());
            context.setRelayMaxPerAthlete(rules.getRelayMaxPerAthlete());
            context.setMaxPerUnit(rules.getMaxPerUnit());
            context.setRuleSource(RegistrationLimit.LIMIT_SOURCE_MEET);
        }

        List<RegistrationLimitValidator.CheckRequest> requests = new ArrayList<>();
        Set<Long> eventIds = new LinkedHashSet<>();
        Set<Long> athleteIds = new LinkedHashSet<>();
        for (RegistrationSaveDTO item : items) {
            RegistrationLimitValidator.CheckRequest request = new RegistrationLimitValidator.CheckRequest();
            request.setEventId(item.getEventId());
            request.setAthleteId(item.getAthleteId());
            request.setUnitId(item.getUnitId());
            request.setGroupId(item.getGroupId());
            request.setIsRelay(item.getIsRelay());
            request.setRelayTeamSeq(item.getRelayTeamSeq());
            request.setMemberAthleteIds(resolveMemberIds(item));
            request.setExcludeRegistrationId(excludeRegistrationId);
            requests.add(request);
            if (item.getEventId() != null) {
                eventIds.add(item.getEventId());
            }
            if (item.getAthleteId() != null) {
                athleteIds.add(item.getAthleteId());
            }
            athleteIds.addAll(request.getMemberAthleteIds());
        }
        context.setRequests(requests);

        Map<Long, EventBriefVO> eventMap = new HashMap<>();
        for (Long eventId : eventIds) {
            EventBriefVO event = baseMapper.selectEventBrief(eventId, meetId);
            if (event != null) {
                eventMap.put(eventId, event);
            }
        }
        context.setEventMap(eventMap);

        Map<Long, Athlete> athleteMap = new HashMap<>();
        if (!athleteIds.isEmpty()) {
            List<Athlete> athletes = athleteMapper.selectBatchIds(athleteIds);
            if (athletes != null) {
                for (Athlete athlete : athletes) {
                    athleteMap.put(athlete.getId(), athlete);
                }
            }
        }
        context.setAthleteMap(athleteMap);

        Set<Long> groupIds = new LinkedHashSet<>();
        for (EventBriefVO event : eventMap.values()) {
            if (event.getGroupId() != null) {
                groupIds.add(event.getGroupId());
            }
        }
        for (Athlete athlete : athleteMap.values()) {
            if (athlete.getGroupId() != null) {
                groupIds.add(athlete.getGroupId());
            }
        }
        Map<Long, MeetGroup> groupMap = new HashMap<>();
        if (!groupIds.isEmpty()) {
            List<MeetGroup> groups = meetGroupMapper.selectBatchIds(groupIds);
            if (groups != null) {
                for (MeetGroup group : groups) {
                    if (meetId.equals(group.getMeetId())) {
                        groupMap.put(group.getId(), group);
                    }
                }
            }
        }
        context.setGroupMap(groupMap);

        List<Registration> existing = loadExistingRegistrations(meetId, eventIds, athleteIds, excludeRegistrationId);
        fillExistingIndex(context, existing);
        return context;
    }

    /**
     * 加载与本次报名相关的既有有效报名（待审 + 已通过；含运动员作为接力队员参与的其他接力报名）。
     */
    private List<Registration> loadExistingRegistrations(Long meetId, Set<Long> eventIds, Set<Long> athleteIds,
                                                        Long excludeRegistrationId) {
        List<Registration> existing = new ArrayList<>();
        if (!eventIds.isEmpty() || !athleteIds.isEmpty()) {
            LambdaQueryWrapper<Registration> wrapper = Wrappers.<Registration>lambdaQuery()
                    .eq(Registration::getMeetId, meetId)
                    .in(Registration::getStatus, Registration.STATUS_PENDING, Registration.STATUS_APPROVED);
            if (!eventIds.isEmpty() && !athleteIds.isEmpty()) {
                wrapper.and(inner -> inner.in(Registration::getEventId, eventIds)
                        .or().in(Registration::getAthleteId, athleteIds));
            } else if (!eventIds.isEmpty()) {
                wrapper.in(Registration::getEventId, eventIds);
            } else {
                wrapper.in(Registration::getAthleteId, athleteIds);
            }
            existing.addAll(list(wrapper));
        }

        if (!athleteIds.isEmpty()) {
            Set<Long> knownIds = new LinkedHashSet<>();
            for (Registration registration : existing) {
                if (registration.getId() != null) {
                    knownIds.add(registration.getId());
                }
            }
            List<RelayMember> memberRows = relayMemberMapper.selectList(Wrappers.<RelayMember>lambdaQuery()
                    .eq(RelayMember::getMeetId, meetId)
                    .in(RelayMember::getAthleteId, athleteIds));
            Set<Long> extraIds = new LinkedHashSet<>();
            for (RelayMember member : memberRows) {
                if (member.getRegistrationId() != null && !knownIds.contains(member.getRegistrationId())) {
                    extraIds.add(member.getRegistrationId());
                }
            }
            if (!extraIds.isEmpty()) {
                List<Registration> extra = listByIds(extraIds);
                for (Registration registration : extra) {
                    if (Registration.STATUS_PENDING.equals(registration.getStatus())
                            || Registration.STATUS_APPROVED.equals(registration.getStatus())) {
                        existing.add(registration);
                        knownIds.add(registration.getId());
                    }
                }
            }
        }

        if (excludeRegistrationId != null) {
            List<Registration> filtered = new ArrayList<>();
            for (Registration registration : existing) {
                if (!excludeRegistrationId.equals(registration.getId())) {
                    filtered.add(registration);
                }
            }
            return filtered;
        }
        return existing;
    }

    /**
     * 由既有有效报名填充：重复报名键、单位×项目计数、每人项数计数、接力队员索引。
     */
    private void fillExistingIndex(RegistrationLimitValidator.CheckContext context, List<Registration> existing) {
        List<Long> relayRegistrationIds = new ArrayList<>();
        Map<Long, Registration> relayRegistrationById = new HashMap<>();
        for (Registration registration : existing) {
            Long eventId = registration.getEventId();
            Long unitId = registration.getUnitId();
            boolean relay = Integer.valueOf(1).equals(registration.getIsRelay());
            if (relay) {
                context.getExistingKeys().put(
                        RegistrationLimitValidator.relayKey(eventId, unitId, registration.getRelayTeamSeq()),
                        registration.getStatus());
                if (registration.getId() != null) {
                    relayRegistrationIds.add(registration.getId());
                    relayRegistrationById.put(registration.getId(), registration);
                }
            } else if (registration.getAthleteId() != null) {
                context.getExistingKeys().put(
                        RegistrationLimitValidator.individualKey(eventId, registration.getAthleteId()),
                        registration.getStatus());
                context.getAthleteIndividualCount().merge(registration.getAthleteId(), 1, Integer::sum);
            }
            if (unitId != null && eventId != null) {
                context.getUnitEventCount().merge(RegistrationLimitValidator.unitEventKey(unitId, eventId),
                        1, Integer::sum);
            }
        }

        if (relayRegistrationIds.isEmpty()) {
            return;
        }
        List<RelayMember> members = relayMemberMapper.selectList(Wrappers.<RelayMember>lambdaQuery()
                .in(RelayMember::getRegistrationId, relayRegistrationIds));
        for (RelayMember member : members) {
            if (member.getAthleteId() == null) {
                continue;
            }
            Registration registration = relayRegistrationById.get(member.getRegistrationId());
            if (registration == null) {
                continue;
            }
            context.getRelayMemberIndex().put(
                    RegistrationLimitValidator.relayMemberKey(registration.getEventId(), member.getAthleteId()),
                    registration.getId());
            context.getAthleteRelayCount().merge(member.getAthleteId(), 1, Integer::sum);
        }
    }

    // ==================================================================
    // 落库
    // ==================================================================

    /**
     * 保存单条报名（含接力队员与校验快照）。
     */
    private Registration saveOne(Long meetId, RegistrationSaveDTO dto, List<RegistrationCheckItemVO> snapshotItems) {
        boolean relay = dto.getIsRelay() != null && dto.getIsRelay() == 1;
        Registration entity = new Registration();
        entity.setMeetId(meetId);
        entity.setEventId(dto.getEventId());
        entity.setAthleteId(relay ? null : dto.getAthleteId());
        entity.setUnitId(dto.getUnitId());
        entity.setGroupId(dto.getGroupId());
        entity.setRoundScope(Registration.ROUND_SCOPE_WHOLE);
        entity.setIsRelay(relay ? 1 : 0);
        entity.setRelayName(dto.getRelayName());
        entity.setRelayTeamSeq(relay ? (dto.getRelayTeamSeq() == null ? Integer.valueOf(1) : dto.getRelayTeamSeq()) : null);
        entity.setSeedResult(dto.getSeedResult());
        entity.setSeedValue(dto.getSeedValue());
        entity.setSeedResultUnit(StringUtils.hasText(dto.getSeedUnit()) ? dto.getSeedUnit() : null);
        entity.setSeedSource(StringUtils.hasText(dto.getSeedSource()) ? dto.getSeedSource() : Registration.SEED_SOURCE_MANUAL);
        entity.setStatus(Registration.STATUS_PENDING);
        entity.setCheckResultJson(buildCheckResultJson(snapshotItems));
        entity.setSubmitTime(LocalDateTime.now());
        entity.setRemark(dto.getRemark());
        boolean saved;
        try {
            saved = save(entity);
        } catch (DuplicateKeyException ex) {
            // 并发提交或绕过校验的重复报名：唯一键 UK(meet_id, event_id, athlete_id) / UK(..., relay_team_seq)
            throw new BizException(ResultCode.REG_DUPLICATE, "该报名已存在（唯一键冲突，可能为并发重复提交）");
        }
        if (!saved) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "报名保存失败");
        }
        if (relay) {
            insertRelayMembers(meetId, entity.getId(), entity.getUnitId(), entity.getGroupId(), dto);
        }
        return entity;
    }

    /**
     * 更新报名的可编辑字段（种子成绩、接力队名、备注）与校验快照；null 表示不修改。
     */
    private void applyEditableFields(Long id, Long meetId, RegistrationSaveDTO dto, boolean relay,
                                     List<RegistrationCheckItemVO> snapshotItems) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = SecurityUtils.getUserId();
        LambdaUpdateWrapper<Registration> wrapper = Wrappers.<Registration>lambdaUpdate()
                .eq(Registration::getId, id)
                .eq(Registration::getMeetId, meetId)
                .set(dto.getSeedResult() != null, Registration::getSeedResult, dto.getSeedResult())
                .set(dto.getSeedValue() != null, Registration::getSeedValue, dto.getSeedValue())
                .set(StringUtils.hasText(dto.getSeedUnit()), Registration::getSeedResultUnit, dto.getSeedUnit())
                .set(StringUtils.hasText(dto.getSeedSource()), Registration::getSeedSource, dto.getSeedSource())
                .set(dto.getRemark() != null, Registration::getRemark, dto.getRemark())
                .set(Registration::getCheckResultJson, buildCheckResultJson(snapshotItems))
                .set(Registration::getUpdateTime, now);
        if (relay && dto.getRelayName() != null) {
            wrapper.set(Registration::getRelayName, dto.getRelayName());
        }
        if (relay && dto.getRelayTeamSeq() != null) {
            wrapper.set(Registration::getRelayTeamSeq, dto.getRelayTeamSeq());
        }
        if (userId != null) {
            wrapper.set(Registration::getUpdateBy, userId);
        }
        if (!update(wrapper)) {
            throw new BizException(ResultCode.CONFLICT, "报名数据已被他人修改，请刷新后重试");
        }
    }

    /**
     * 写入接力队员：棒次按 members 数组顺序从 1 归一化重排，并冗余姓名/号码/单位/组别快照。
     */
    private void insertRelayMembers(Long meetId, Long registrationId, Long unitId, Long groupId,
                                    RegistrationSaveDTO dto) {
        List<RelayMember> members = buildRelayMembers(meetId, registrationId, unitId, groupId, dto);
        for (RelayMember member : members) {
            if (relayMemberMapper.insert(member) <= 0) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "接力队员保存失败");
            }
        }
    }

    private List<RelayMember> buildRelayMembers(Long meetId, Long registrationId, Long unitId, Long groupId,
                                                RegistrationSaveDTO dto) {
        List<Long> memberIds = resolveMemberIds(dto);
        List<RelayMember> result = new ArrayList<>();
        if (memberIds.isEmpty()) {
            return result;
        }
        Map<Long, Athlete> athleteMap = new HashMap<>();
        List<Athlete> athletes = athleteMapper.selectBatchIds(new LinkedHashSet<>(memberIds));
        if (athletes != null) {
            for (Athlete athlete : athletes) {
                athleteMap.put(athlete.getId(), athlete);
            }
        }
        int legNo = 0;
        for (Long memberId : memberIds) {
            Athlete athlete = athleteMap.get(memberId);
            RelayMember member = new RelayMember();
            member.setMeetId(meetId);
            member.setRegistrationId(registrationId);
            member.setAthleteId(memberId);
            member.setAthleteName(athlete == null ? null : athlete.getAthleteName());
            member.setLegNo(++legNo);
            member.setUnitId(unitId != null ? unitId : (athlete == null ? null : athlete.getUnitId()));
            member.setGroupId(athlete == null ? groupId : athlete.getGroupId());
            member.setBibNo(athlete == null ? null : athlete.getBibNo());
            member.setIsReserve(0);
            member.setStatus(RelayMember.STATUS_NORMAL);
            result.add(member);
        }
        return result;
    }

    // ==================================================================
    // 请求归一化与快照
    // ==================================================================

    /**
     * 归一化请求：补全届次、接力标记、单位与组别（空值时按运动员/项目兜底）。
     */
    private void normalize(Long meetId, List<RegistrationSaveDTO> items, Long fallbackUnitId) {
        for (RegistrationSaveDTO item : items) {
            item.setMeetId(meetId);
            if (item.getIsRelay() == null) {
                EventBriefVO event = item.getEventId() == null ? null
                        : baseMapper.selectEventBrief(item.getEventId(), meetId);
                item.setIsRelay(event != null && Integer.valueOf(1).equals(event.getIsRelay()) ? 1 : 0);
            }
            boolean relay = item.getIsRelay() != null && item.getIsRelay() == 1;
            if (item.getUnitId() == null && fallbackUnitId != null) {
                item.setUnitId(fallbackUnitId);
            }
            Long primaryAthleteId = relay ? firstMemberId(item) : item.getAthleteId();
            if ((item.getUnitId() == null || item.getGroupId() == null) && primaryAthleteId != null) {
                Athlete athlete = athleteMapper.selectById(primaryAthleteId);
                if (athlete != null) {
                    if (item.getUnitId() == null) {
                        item.setUnitId(athlete.getUnitId());
                    }
                    if (item.getGroupId() == null) {
                        item.setGroupId(athlete.getGroupId());
                    }
                }
            }
            if (item.getGroupId() == null && item.getEventId() != null) {
                EventBriefVO event = baseMapper.selectEventBrief(item.getEventId(), meetId);
                if (event != null) {
                    item.setGroupId(event.getGroupId());
                }
            }
        }
    }

    /**
     * 构建校验快照 JSON：{@code {"checkedAt":"...","items":[{"level":"...","code":"...","message":"..."}]}}。
     */
    private String buildCheckResultJson(List<RegistrationCheckItemVO> items) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("checkedAt", LocalDateTime.now().format(CHECK_TIME_FORMATTER));
        ArrayNode array = root.putArray("items");
        if (items != null) {
            for (RegistrationCheckItemVO item : items) {
                if (item == null) {
                    continue;
                }
                ObjectNode node = array.addObject();
                if (item.getRowIndex() != null) {
                    node.put("rowIndex", item.getRowIndex().intValue());
                }
                if (item.getAthleteId() != null) {
                    node.put("athleteId", item.getAthleteId().longValue());
                }
                if (item.getAthleteName() != null) {
                    node.put("athleteName", item.getAthleteName());
                }
                if (item.getEventId() != null) {
                    node.put("eventId", item.getEventId().longValue());
                }
                if (item.getEventName() != null) {
                    node.put("eventName", item.getEventName());
                }
                node.put("level", item.getLevel());
                node.put("code", item.getCode());
                node.put("message", item.getMessage());
            }
        }
        return root.toString();
    }

    /**
     * 解析已落库的校验快照；快照损坏时返回空清单（由调用方按未校验处理）。
     */
    private List<RegistrationCheckItemVO> parseCheckItems(String json) {
        List<RegistrationCheckItemVO> items = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return items;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode array = root.path("items");
            if (!array.isArray()) {
                return items;
            }
            for (JsonNode node : array) {
                RegistrationCheckItemVO item = new RegistrationCheckItemVO();
                item.setRowIndex(node.hasNonNull("rowIndex") ? node.get("rowIndex").asInt() : null);
                item.setAthleteId(node.hasNonNull("athleteId") ? node.get("athleteId").asLong() : null);
                item.setAthleteName(node.hasNonNull("athleteName") ? node.get("athleteName").asText() : null);
                item.setEventId(node.hasNonNull("eventId") ? node.get("eventId").asLong() : null);
                item.setEventName(node.hasNonNull("eventName") ? node.get("eventName").asText() : null);
                item.setLevel(node.hasNonNull("level") ? node.get("level").asText() : null);
                item.setCode(node.hasNonNull("code") ? node.get("code").asText() : null);
                item.setMessage(node.hasNonNull("message") ? node.get("message").asText() : null);
                items.add(item);
            }
        } catch (JsonProcessingException ex) {
            return new ArrayList<>();
        }
        return items;
    }

    /**
     * 按请求下标过滤校验项（用于把批次校验结果切片写入每条报名的快照）。
     */
    private List<RegistrationCheckItemVO> filterItems(List<RegistrationCheckItemVO> items, int rowIndex) {
        List<RegistrationCheckItemVO> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (RegistrationCheckItemVO item : items) {
            if (item != null && item.getRowIndex() != null && item.getRowIndex() == rowIndex) {
                result.add(item);
            }
        }
        return result;
    }

    // ==================================================================
    // 列表填充与工具
    // ==================================================================

    private void fillStatusNames(List<RegistrationVO> records) {
        if (records == null) {
            return;
        }
        for (RegistrationVO vo : records) {
            vo.setStatusName(statusName(vo.getStatus()));
        }
    }

    private void fillCheckStatus(List<RegistrationVO> records) {
        if (records == null) {
            return;
        }
        for (RegistrationVO vo : records) {
            List<RegistrationCheckItemVO> items = parseCheckItems(vo.getCheckResultJson());
            if (items.isEmpty()) {
                vo.setCheckStatus(RegistrationVO.CHECK_STATUS_UNCHECKED);
                continue;
            }
            String firstError = null;
            String firstWarn = null;
            for (RegistrationCheckItemVO item : items) {
                if (RegistrationCheckItemVO.LEVEL_ERROR.equals(item.getLevel())) {
                    if (firstError == null) {
                        firstError = item.getMessage();
                    }
                } else if (firstWarn == null) {
                    firstWarn = item.getMessage();
                }
            }
            if (firstError != null) {
                vo.setCheckStatus(RegistrationVO.CHECK_STATUS_FAIL);
                vo.setCheckMessage(firstError);
            } else {
                vo.setCheckStatus(RegistrationVO.CHECK_STATUS_PASS);
                vo.setCheckMessage(firstWarn);
            }
        }
    }

    private void fillRelayMembers(List<RegistrationVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> registrationIds = new ArrayList<>();
        for (RegistrationVO vo : records) {
            if (Integer.valueOf(1).equals(vo.getIsRelay()) && vo.getId() != null) {
                registrationIds.add(vo.getId());
            }
        }
        if (registrationIds.isEmpty()) {
            return;
        }
        List<RelayMember> members = relayMemberMapper.selectList(Wrappers.<RelayMember>lambdaQuery()
                .in(RelayMember::getRegistrationId, registrationIds)
                .orderByAsc(RelayMember::getLegNo));
        Map<Long, List<RelayMember>> grouped = new HashMap<>();
        for (RelayMember member : members) {
            grouped.computeIfAbsent(member.getRegistrationId(), key -> new ArrayList<>()).add(member);
        }
        for (RegistrationVO vo : records) {
            List<RelayMember> list = grouped.get(vo.getId());
            if (list == null) {
                continue;
            }
            vo.setMembers(list);
            StringBuilder names = new StringBuilder();
            for (RelayMember member : list) {
                if (!StringUtils.hasText(member.getAthleteName())) {
                    continue;
                }
                if (names.length() > 0) {
                    names.append(',');
                }
                names.append(member.getAthleteName());
            }
            vo.setMemberNames(names.length() == 0 ? null : names.toString());
        }
    }

    private Map<Long, String> loadEventNames(Long meetId, Collection<Long> eventIds) {
        Map<Long, String> map = new HashMap<>();
        if (eventIds == null || eventIds.isEmpty()) {
            return map;
        }
        List<EventBriefVO> events = baseMapper.selectEventBriefs(new LinkedHashSet<>(eventIds), meetId);
        if (events != null) {
            for (EventBriefVO event : events) {
                map.put(event.getId(), event.getEventName());
            }
        }
        return map;
    }

    private Map<Long, String> loadAthleteNames(Collection<Long> athleteIds) {
        Map<Long, String> map = new HashMap<>();
        if (athleteIds == null || athleteIds.isEmpty()) {
            return map;
        }
        List<Athlete> athletes = athleteMapper.selectBatchIds(new LinkedHashSet<>(athleteIds));
        if (athletes != null) {
            for (Athlete athlete : athletes) {
                map.put(athlete.getId(), athlete.getAthleteName());
            }
        }
        return map;
    }

    /**
     * 解析并校验届次 ID：空时取上下文，仍为空抛参数错误；与上下文不一致抛 FORBIDDEN。
     */
    private Long requireMeetId(Long candidate) {
        Long contextMeetId = MeetContext.get();
        Long meetId = candidate != null ? candidate : contextMeetId;
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空（请传入 meetId 或请求头 X-Meet-Id）");
        }
        if (contextMeetId != null && !contextMeetId.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        return meetId;
    }

    private List<Long> resolveMemberIds(RegistrationSaveDTO dto) {
        List<Long> ids = new ArrayList<>();
        if (dto == null || dto.getMembers() == null) {
            return ids;
        }
        for (RelayMember member : dto.getMembers()) {
            if (member != null && member.getAthleteId() != null && !ids.contains(member.getAthleteId())) {
                ids.add(member.getAthleteId());
            }
        }
        return ids;
    }

    private Long firstMemberId(RegistrationSaveDTO dto) {
        List<Long> ids = resolveMemberIds(dto);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 校验问题码 -> 业务错误码。
     */
    private ResultCode mapResultCode(String checkCode) {
        if (checkCode == null) {
            return ResultCode.REG_LIMIT_EXCEEDED;
        }
        switch (checkCode) {
            case RegistrationLimitValidator.CODE_REG_DUPLICATE:
                return ResultCode.REG_DUPLICATE;
            case RegistrationLimitValidator.CODE_REG_CLOSED:
                return ResultCode.REG_CLOSED;
            case RegistrationLimitValidator.CODE_EVENT_NOT_FOUND:
            case RegistrationLimitValidator.CODE_ATHLETE_NOT_FOUND:
                return ResultCode.NOT_FOUND;
            case RegistrationLimitValidator.CODE_PARAM_ERROR:
                return ResultCode.PARAM_ERROR;
            default:
                return ResultCode.REG_LIMIT_EXCEEDED;
        }
    }

    private String statusName(String status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case Registration.STATUS_PENDING:
                return "待审";
            case Registration.STATUS_APPROVED:
                return "已通过";
            case Registration.STATUS_REJECTED:
                return "已退回";
            case Registration.STATUS_CANCELLED:
                return "已取消";
            default:
                return status;
        }
    }

    /**
     * 简易 JSON 数组序列化（仅用于 ID 明细快照，避免 checked 异常）。
     */
    private String buildJsonArray(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Long value : values) {
            if (value == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append(value);
            first = false;
        }
        return builder.append(']').toString();
    }

    /**
     * 解析 {@code unitId:eventId} 计数键。
     */
    private Long parseKeyPart(String key, int index) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String[] parts = key.split(":");
        if (index >= parts.length) {
            return null;
        }
        try {
            return Long.valueOf(parts[index]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
