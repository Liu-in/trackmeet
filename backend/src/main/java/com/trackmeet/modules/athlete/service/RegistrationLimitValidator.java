package com.trackmeet.modules.athlete.service;

import com.trackmeet.modules.athlete.entity.Athlete;
import com.trackmeet.modules.athlete.vo.EventBriefVO;
import com.trackmeet.modules.athlete.vo.RegistrationCheckItemVO;
import com.trackmeet.modules.athlete.vo.RegistrationCheckResultVO;
import com.trackmeet.modules.meet.entity.MeetGroup;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报名限制校验引擎（纯校验逻辑：只读输入、只出校验项，不落库）。
 * <p>
 * 输入：{@link CheckContext}（报名请求 + 已加载的规则/项目/运动员/组别/既有报名计数）；
 * 输出：{@link RegistrationCheckItemVO} 列表（ERROR 阻断、WARN 提示）。
 * <p>
 * 逐条实现 9 条规则：
 * <ol>
 *   <li>报名期：届次状态必须为 REG_OPEN，否则 {@code REG_CLOSED}；</li>
 *   <li>主体：运动员存在、属同一届次、资格 QUALIFIED（否则 ERROR）；</li>
 *   <li>组别与性别：项目组别须等于运动员组别；项目性别 MALE/FEMALE 须与运动员一致（MIXED 不限）；</li>
 *   <li>重复报名：个人 = 届次+项目+运动员；接力 = 届次+项目+单位+队序号，命中非 CANCELLED/REJECTED 记录则 {@code REG_DUPLICATE}；</li>
 *   <li>每人限报项数：个人项目数 &gt; athlete_max_events、接力项目数 &gt; relay_max_per_athlete 则 {@code REG_LIMIT_EXCEEDED}；</li>
 *   <li>每单位每项限报人数：单位×项目已报数 ≥ 生效上限则 {@code REG_LIMIT_EXCEEDED}（项目级优先、届次级兜底）；</li>
 *   <li>接力人数：relay_min ~ relay_max（默认 4/4），且同一运动员不得在同一接力项目重复出现；</li>
 *   <li>年龄/出生日期：超出 meet_group.birth_date_start ~ birth_date_end 记 WARN，不阻断；</li>
 *   <li>校验结果由调用方写入 {@code registration.check_result_json}（见服务层快照构建）。</li>
 * </ol>
 * 所有阈值均来自配置（meet_rule_config / event 列），引擎内不硬编码业务阈值。
 *
 * @author TrackMeet
 */
@Component
public class RegistrationLimitValidator {

    /** 级别：错误（阻断报名） */
    public static final String LEVEL_ERROR = RegistrationCheckItemVO.LEVEL_ERROR;
    /** 级别：警告（提示，不阻断） */
    public static final String LEVEL_WARN = RegistrationCheckItemVO.LEVEL_WARN;

    /** 问题码：报名未开放/已截止 */
    public static final String CODE_REG_CLOSED = "REG_CLOSED";
    /** 问题码：项目不存在 */
    public static final String CODE_EVENT_NOT_FOUND = "EVENT_NOT_FOUND";
    /** 问题码：运动员不存在 */
    public static final String CODE_ATHLETE_NOT_FOUND = "ATHLETE_NOT_FOUND";
    /** 问题码：资格未通过 */
    public static final String CODE_QUALIFICATION = "ATHLETE_QUALIFICATION";
    /** 问题码：组别不符 */
    public static final String CODE_GROUP_MISMATCH = "GROUP_MISMATCH";
    /** 问题码：性别不符 */
    public static final String CODE_GENDER_MISMATCH = "GENDER_MISMATCH";
    /** 问题码：重复报名 */
    public static final String CODE_REG_DUPLICATE = "REG_DUPLICATE";
    /** 问题码：超出限报（超项/超人数） */
    public static final String CODE_REG_LIMIT_EXCEEDED = "REG_LIMIT_EXCEEDED";
    /** 问题码：接力人数不符 */
    public static final String CODE_RELAY_MEMBER_COUNT = "RELAY_MEMBER_COUNT";
    /** 问题码：接力队员重复 */
    public static final String CODE_RELAY_MEMBER_DUPLICATE = "RELAY_MEMBER_DUPLICATE";
    /** 问题码：出生日期不在组别区间（WARN） */
    public static final String CODE_AGE_RANGE = "AGE_RANGE";
    /** 问题码：参数错误 */
    public static final String CODE_PARAM_ERROR = "PARAM_ERROR";

    /** 届次报名开放状态 */
    private static final String MEET_STATUS_REG_OPEN = "REG_OPEN";
    /** 资格状态：合格 */
    private static final String QUALIFICATION_QUALIFIED = "QUALIFIED";
    /** 性别：混合（不限） */
    private static final String GENDER_MIXED = "MIXED";
    /** 接力人数默认下限（event.relay_min 缺失时） */
    private static final int DEFAULT_RELAY_MIN = 4;
    /** 接力人数默认上限（event.relay_max 缺失时） */
    private static final int DEFAULT_RELAY_MAX = 4;

    /**
     * 执行校验。
     *
     * @param context 校验上下文
     * @return 校验项列表（按请求顺序、按规则顺序）
     */
    public List<RegistrationCheckItemVO> validate(CheckContext context) {
        return doValidate(context, null);
    }

    /**
     * 执行校验并汇总通过/未通过条数（用于 POST /check 不落库校验接口）。
     *
     * @param context 校验上下文
     * @return 校验结果（passCount 为无 ERROR 的请求数，failCount 为存在 ERROR 的请求数）
     */
    public RegistrationCheckResultVO validateResult(CheckContext context) {
        Set<Integer> failIndexes = new HashSet<>();
        List<RegistrationCheckItemVO> items = doValidate(context, failIndexes);
        int total = (context == null || context.getRequests() == null) ? 0 : context.getRequests().size();
        RegistrationCheckResultVO result = new RegistrationCheckResultVO();
        result.setItems(items);
        result.setFailCount(failIndexes.size());
        result.setPassCount(Math.max(total - failIndexes.size(), 0));
        return result;
    }

    /**
     * 是否存在 ERROR 级校验项。
     */
    public boolean hasError(List<RegistrationCheckItemVO> items) {
        return firstError(items) != null;
    }

    /**
     * 第一条 ERROR 级校验项，没有则返回 null。
     */
    public RegistrationCheckItemVO firstError(List<RegistrationCheckItemVO> items) {
        if (items == null) {
            return null;
        }
        for (RegistrationCheckItemVO item : items) {
            if (item != null && LEVEL_ERROR.equals(item.getLevel())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 第一条 ERROR 级校验项的问题码。
     */
    public String firstErrorCode(List<RegistrationCheckItemVO> items) {
        RegistrationCheckItemVO item = firstError(items);
        return item == null ? null : item.getCode();
    }

    /**
     * 第一条 ERROR 级校验项的中文描述。
     */
    public String firstErrorMessage(List<RegistrationCheckItemVO> items) {
        RegistrationCheckItemVO item = firstError(items);
        return item == null ? null : item.getMessage();
    }

    // ------------------------------------------------------------------
    // 键构造：服务层组装既有数据与引擎判定必须使用同一套键
    // ------------------------------------------------------------------

    /** 个人重复报名键：项目 + 运动员 */
    public static String individualKey(Long eventId, Long athleteId) {
        return "I:" + eventId + ":" + athleteId;
    }

    /** 接力重复报名键：项目 + 单位 + 队序号 */
    public static String relayKey(Long eventId, Long unitId, Integer relayTeamSeq) {
        return "R:" + eventId + ":" + unitId + ":" + relayTeamSeq;
    }

    /** 单位 × 项目计数键 */
    public static String unitEventKey(Long unitId, Long eventId) {
        return unitId + ":" + eventId;
    }

    /** 接力队员占位键：项目 + 运动员 */
    public static String relayMemberKey(Long eventId, Long athleteId) {
        return "M:" + eventId + ":" + athleteId;
    }

    // ------------------------------------------------------------------
    // 校验主流程
    // ------------------------------------------------------------------

    private List<RegistrationCheckItemVO> doValidate(CheckContext context, Set<Integer> failIndexes) {
        List<RegistrationCheckItemVO> items = new ArrayList<>();
        if (context == null || context.getRequests() == null || context.getRequests().isEmpty()) {
            return items;
        }
        List<CheckRequest> requests = context.getRequests();
        boolean windowOpen = MEET_STATUS_REG_OPEN.equals(context.getMeetStatus());

        // 同一批次内的累计量（用于批次内相互影响：超项、超人数、重复、队员重复）
        Map<String, Integer> batchUnitEventCount = new HashMap<>();
        Map<Long, Integer> batchIndividualCount = new HashMap<>();
        Map<Long, Integer> batchRelayCount = new HashMap<>();
        Set<String> batchKeys = new HashSet<>();
        Set<String> batchRelayMemberKeys = new HashSet<>();

        for (int rowIndex = 0; rowIndex < requests.size(); rowIndex++) {
            CheckRequest request = requests.get(rowIndex);
            int errorBefore = countError(items);
            validateOne(context, request, rowIndex, windowOpen, batchUnitEventCount, batchIndividualCount,
                    batchRelayCount, batchKeys, batchRelayMemberKeys, items);
            if (failIndexes != null && countError(items) > errorBefore) {
                failIndexes.add(rowIndex);
            }
        }
        return items;
    }

    private void validateOne(CheckContext context, CheckRequest request, int rowIndex, boolean windowOpen,
                             Map<String, Integer> batchUnitEventCount, Map<Long, Integer> batchIndividualCount,
                             Map<Long, Integer> batchRelayCount, Set<String> batchKeys,
                             Set<String> batchRelayMemberKeys, List<RegistrationCheckItemVO> items) {
        Long eventId = request.getEventId();
        EventBriefVO event = (eventId == null) ? null : context.getEventMap().get(eventId);
        String eventName = (event == null) ? null : event.getEventName();
        boolean relay = isRelayRequest(request, event);

        // 规则 1：报名期校验
        if (!windowOpen) {
            addItem(items, rowIndex, request.getAthleteId(), nameOf(context, request.getAthleteId()),
                    eventId, eventName, LEVEL_ERROR, CODE_REG_CLOSED,
                    "当前届次状态为 " + textOf(context.getMeetStatus()) + "，报名未开放（需为 REG_OPEN）");
            return;
        }

        // 规则 2：主体校验（项目存在、运动员存在/同届次/资格合格）
        if (event == null) {
            addItem(items, rowIndex, request.getAthleteId(), nameOf(context, request.getAthleteId()),
                    eventId, eventName, LEVEL_ERROR, CODE_EVENT_NOT_FOUND, "报名项目不存在或不属于当前届次");
            return;
        }

        List<Athlete> participants = new ArrayList<>();
        if (relay) {
            List<Long> memberIds = request.getMemberAthleteIds();
            if (memberIds == null || memberIds.isEmpty()) {
                addItem(items, rowIndex, null, null, eventId, eventName, LEVEL_ERROR, CODE_PARAM_ERROR,
                        "接力报名必须提供接力队员（members）");
                return;
            }
            for (Long memberId : memberIds) {
                Athlete member = athleteById(context, memberId);
                if (member == null) {
                    addItem(items, rowIndex, memberId, null, eventId, eventName, LEVEL_ERROR, CODE_ATHLETE_NOT_FOUND,
                            "接力队员不存在（运动员 ID：" + memberId + "）");
                    continue;
                }
                if (context.getMeetId() != null && !context.getMeetId().equals(member.getMeetId())) {
                    addItem(items, rowIndex, member.getId(), member.getAthleteName(), eventId, eventName,
                            LEVEL_ERROR, CODE_ATHLETE_NOT_FOUND, "接力队员不属于当前届次");
                    continue;
                }
                if (!QUALIFICATION_QUALIFIED.equals(member.getQualificationStatus())) {
                    addItem(items, rowIndex, member.getId(), member.getAthleteName(), eventId, eventName,
                            LEVEL_ERROR, CODE_QUALIFICATION,
                            "接力队员资格未通过（当前：" + qualificationText(member.getQualificationStatus()) + "）");
                }
                participants.add(member);
            }
            if (participants.isEmpty()) {
                return;
            }
        } else {
            Long athleteId = request.getAthleteId();
            if (athleteId == null) {
                addItem(items, rowIndex, null, null, eventId, eventName, LEVEL_ERROR, CODE_PARAM_ERROR,
                        "个人项目报名必须指定运动员");
                return;
            }
            Athlete athlete = athleteById(context, athleteId);
            if (athlete == null) {
                addItem(items, rowIndex, athleteId, null, eventId, eventName, LEVEL_ERROR, CODE_ATHLETE_NOT_FOUND,
                        "运动员不存在（运动员 ID：" + athleteId + "）");
                return;
            }
            if (context.getMeetId() != null && !context.getMeetId().equals(athlete.getMeetId())) {
                addItem(items, rowIndex, athlete.getId(), athlete.getAthleteName(), eventId, eventName,
                        LEVEL_ERROR, CODE_ATHLETE_NOT_FOUND, "运动员不属于当前届次");
                return;
            }
            if (!QUALIFICATION_QUALIFIED.equals(athlete.getQualificationStatus())) {
                addItem(items, rowIndex, athlete.getId(), athlete.getAthleteName(), eventId, eventName,
                        LEVEL_ERROR, CODE_QUALIFICATION,
                        "运动员资格未通过（当前：" + qualificationText(athlete.getQualificationStatus()) + "）");
            }
            participants.add(athlete);
        }

        // 规则 3：组别与性别；规则 8：出生日期区间（WARN）
        for (Athlete participant : participants) {
            if (event.getGroupId() != null && !event.getGroupId().equals(participant.getGroupId())) {
                addItem(items, rowIndex, participant.getId(), participant.getAthleteName(), eventId, eventName,
                        LEVEL_ERROR, CODE_GROUP_MISMATCH,
                        "运动员所属组别（" + groupNameOf(context, participant.getGroupId()) + "）与项目组别（"
                                + groupNameOf(context, event.getGroupId()) + "）不一致");
            }
            if (event.getGender() != null && !GENDER_MIXED.equals(event.getGender())
                    && !event.getGender().equals(participant.getGender())) {
                addItem(items, rowIndex, participant.getId(), participant.getAthleteName(), eventId, eventName,
                        LEVEL_ERROR, CODE_GENDER_MISMATCH,
                        "项目适用性别为 " + genderText(event.getGender()) + "，与运动员性别 "
                                + genderText(participant.getGender()) + " 不一致");
            }
            MeetGroup group = participant.getGroupId() == null ? null : context.getGroupMap().get(participant.getGroupId());
            if (group != null && participant.getBirthDate() != null
                    && outOfBirthRange(participant.getBirthDate(), group)) {
                addItem(items, rowIndex, participant.getId(), participant.getAthleteName(), eventId, eventName,
                        LEVEL_WARN, CODE_AGE_RANGE,
                        "运动员出生日期 " + participant.getBirthDate() + " 不在组别「" + textOf(group.getGroupName())
                                + "」要求的 [" + dateText(group.getBirthDateStart()) + " ~ "
                                + dateText(group.getBirthDateEnd()) + "] 区间内");
            }
        }

        Long unitId = request.getUnitId() != null ? request.getUnitId() : participants.get(0).getUnitId();
        if (unitId == null) {
            // registration.unit_id 为 NOT NULL 列，无法确定单位时直接阻断，避免落库时报数据库约束错误
            addItem(items, rowIndex, null, null, eventId, eventName, LEVEL_ERROR, CODE_PARAM_ERROR,
                    "无法确定报名单位：请显式传入 unitId 或在运动员档案中维护所属单位");
            return;
        }

        // 规则 4：重复报名
        if (relay) {
            Integer teamSeq = request.getRelayTeamSeq() == null ? Integer.valueOf(1) : request.getRelayTeamSeq();
            String key = relayKey(eventId, unitId, teamSeq);
            String existsStatus = context.getExistingKeys().get(key);
            if (existsStatus != null) {
                addItem(items, rowIndex, null, null, eventId, eventName, LEVEL_ERROR, CODE_REG_DUPLICATE,
                        "该单位本项目第 " + teamSeq + " 接力队已报名（当前状态：" + statusText(existsStatus) + "）");
            } else if (!batchKeys.add(key)) {
                addItem(items, rowIndex, null, null, eventId, eventName, LEVEL_ERROR, CODE_REG_DUPLICATE,
                        "同一批次内重复提交该单位本项目的第 " + teamSeq + " 接力队");
            }
        } else {
            Long athleteId = request.getAthleteId();
            String key = individualKey(eventId, athleteId);
            String existsStatus = context.getExistingKeys().get(key);
            if (existsStatus != null) {
                addItem(items, rowIndex, athleteId, nameOf(context, athleteId), eventId, eventName, LEVEL_ERROR,
                        CODE_REG_DUPLICATE, "该运动员已报名本项目（当前状态：" + statusText(existsStatus) + "）");
            } else if (!batchKeys.add(key)) {
                addItem(items, rowIndex, athleteId, nameOf(context, athleteId), eventId, eventName, LEVEL_ERROR,
                        CODE_REG_DUPLICATE, "同一批次内重复提交该运动员的本项目报名");
            }
        }

        // 规则 5：每人限报项数（个人项数 / 接力项数各自独立上限）
        Integer maxIndividual = context.getAthleteMaxEvents();
        Integer maxRelay = context.getRelayMaxPerAthlete();
        if (relay) {
            for (Athlete participant : participants) {
                int exists = countOf(context.getAthleteRelayCount(), participant.getId());
                int pending = countOf(batchRelayCount, participant.getId());
                int limit = maxRelay == null ? 0 : maxRelay;
                if (limit > 0 && exists + pending + 1 > limit) {
                    addItem(items, rowIndex, participant.getId(), participant.getAthleteName(), eventId, eventName,
                            LEVEL_ERROR, CODE_REG_LIMIT_EXCEEDED,
                            "接力项目已报 " + (exists + pending) + " 项，上限 " + limit + " 项");
                } else {
                    batchRelayCount.merge(participant.getId(), 1, Integer::sum);
                }
            }
        } else {
            Long athleteId = request.getAthleteId();
            int exists = countOf(context.getAthleteIndividualCount(), athleteId);
            int pending = countOf(batchIndividualCount, athleteId);
            int limit = maxIndividual == null ? 0 : maxIndividual;
            if (limit > 0 && exists + pending + 1 > limit) {
                addItem(items, rowIndex, athleteId, nameOf(context, athleteId), eventId, eventName, LEVEL_ERROR,
                        CODE_REG_LIMIT_EXCEEDED,
                        "个人项目已报 " + (exists + pending) + " 项，上限 " + limit + " 项");
            } else {
                batchIndividualCount.merge(athleteId, 1, Integer::sum);
            }
        }

        // 规则 6：每单位每项限报人数（项目级优先，届次级兜底）
        int limitPerUnit = effectiveMaxPerUnit(context, event);
        if (limitPerUnit > 0 && unitId != null) {
            String key = unitEventKey(unitId, eventId);
            int exists = countOf(context.getUnitEventCount(), key);
            int pending = countOf(batchUnitEventCount, key);
            if (exists + pending >= limitPerUnit) {
                addItem(items, rowIndex, null, null, eventId, eventName, LEVEL_ERROR, CODE_REG_LIMIT_EXCEEDED,
                        "本单位本项目已报 " + (exists + pending) + " 个，每单位每项限报 " + limitPerUnit + " 个");
            } else {
                batchUnitEventCount.merge(key, 1, Integer::sum);
            }
        }

        // 规则 7：接力人数上下限 + 同一接力项目内队员不得重复
        if (relay) {
            List<Long> memberIds = request.getMemberAthleteIds();
            int min = (event.getRelayMin() != null && event.getRelayMin() > 0) ? event.getRelayMin() : DEFAULT_RELAY_MIN;
            int max = (event.getRelayMax() != null && event.getRelayMax() > 0) ? event.getRelayMax() : DEFAULT_RELAY_MAX;
            int size = memberIds.size();
            if (size < min || size > max) {
                addItem(items, rowIndex, null, null, eventId, eventName, LEVEL_ERROR, CODE_RELAY_MEMBER_COUNT,
                        "接力队员人数需在 " + min + " ~ " + max + " 人之间，当前 " + size + " 人");
            }
            Set<Long> seen = new LinkedHashSet<>();
            for (Long memberId : memberIds) {
                Athlete member = athleteById(context, memberId);
                String memberName = member == null ? null : member.getAthleteName();
                if (!seen.add(memberId)) {
                    addItem(items, rowIndex, memberId, memberName, eventId, eventName, LEVEL_ERROR,
                            CODE_RELAY_MEMBER_DUPLICATE,
                            "同一接力队内队员重复（运动员 ID：" + memberId + "）");
                    continue;
                }
                String key = relayMemberKey(eventId, memberId);
                Long existsRegistrationId = context.getRelayMemberIndex().get(key);
                boolean self = existsRegistrationId != null && request.getExcludeRegistrationId() != null
                        && existsRegistrationId.equals(request.getExcludeRegistrationId());
                if (existsRegistrationId != null && !self) {
                    addItem(items, rowIndex, memberId, memberName, eventId, eventName, LEVEL_ERROR,
                            CODE_RELAY_MEMBER_DUPLICATE,
                            "该运动员已参加本项目的其他接力队（报名 ID：" + existsRegistrationId + "）");
                } else if (!batchRelayMemberKeys.add(key)) {
                    addItem(items, rowIndex, memberId, memberName, eventId, eventName, LEVEL_ERROR,
                            CODE_RELAY_MEMBER_DUPLICATE,
                            "同一批次内该运动员被多个接力队重复使用（运动员 ID：" + memberId + "）");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 规则取值与工具方法
    // ------------------------------------------------------------------

    /**
     * 每单位每项限报人数生效值：项目级 {@code event.max_per_unit} 优先，届次级 {@code meet_rule_config.max_per_unit} 兜底；
     * 0 或空视为不限。
     */
    public int effectiveMaxPerUnit(CheckContext context, EventBriefVO event) {
        if (event != null && event.getMaxPerUnit() != null && event.getMaxPerUnit() > 0) {
            return event.getMaxPerUnit();
        }
        Integer meetMax = context == null ? null : context.getMaxPerUnit();
        return (meetMax == null || meetMax <= 0) ? 0 : meetMax;
    }

    private boolean isRelayRequest(CheckRequest request, EventBriefVO event) {
        if (event != null && Integer.valueOf(1).equals(event.getIsRelay())) {
            return true;
        }
        return request.getIsRelay() != null && request.getIsRelay() == 1;
    }

    private Athlete athleteById(CheckContext context, Long athleteId) {
        if (athleteId == null) {
            return null;
        }
        return context.getAthleteMap().get(athleteId);
    }

    private String nameOf(CheckContext context, Long athleteId) {
        Athlete athlete = athleteById(context, athleteId);
        return athlete == null ? null : athlete.getAthleteName();
    }

    private String groupNameOf(CheckContext context, Long groupId) {
        if (groupId == null) {
            return "未设置";
        }
        MeetGroup group = context.getGroupMap().get(groupId);
        return group == null ? ("组别#" + groupId) : group.getGroupName();
    }

    private boolean outOfBirthRange(LocalDate birthDate, MeetGroup group) {
        if (group.getBirthDateStart() != null && birthDate.isBefore(group.getBirthDateStart())) {
            return true;
        }
        return group.getBirthDateEnd() != null && birthDate.isAfter(group.getBirthDateEnd());
    }

    private int countError(List<RegistrationCheckItemVO> items) {
        int count = 0;
        for (RegistrationCheckItemVO item : items) {
            if (item != null && LEVEL_ERROR.equals(item.getLevel())) {
                count++;
            }
        }
        return count;
    }

    private int countOf(Map<Long, Integer> map, Long key) {
        if (map == null || key == null) {
            return 0;
        }
        Integer value = map.get(key);
        return value == null ? 0 : value;
    }

    private int countOf(Map<String, Integer> map, String key) {
        if (map == null || key == null) {
            return 0;
        }
        Integer value = map.get(key);
        return value == null ? 0 : value;
    }

    private void addItem(List<RegistrationCheckItemVO> items, int rowIndex, Long athleteId, String athleteName,
                         Long eventId, String eventName, String level, String code, String message) {
        RegistrationCheckItemVO item = new RegistrationCheckItemVO();
        item.setRowIndex(rowIndex);
        item.setAthleteId(athleteId);
        item.setAthleteName(athleteName);
        item.setEventId(eventId);
        item.setEventName(eventName);
        item.setLevel(level);
        item.setCode(code);
        item.setMessage(message);
        items.add(item);
    }

    private String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "PENDING":
                return "待审";
            case "APPROVED":
                return "已通过";
            case "REJECTED":
                return "已退回";
            case "CANCELLED":
                return "已取消";
            default:
                return status;
        }
    }

    private String qualificationText(String qualificationStatus) {
        if (qualificationStatus == null) {
            return "未设置";
        }
        switch (qualificationStatus) {
            case "PENDING":
                return "待审";
            case "QUALIFIED":
                return "合格";
            case "DISQUALIFIED":
                return "不合格";
            default:
                return qualificationStatus;
        }
    }

    private String genderText(String gender) {
        if (gender == null) {
            return "未设置";
        }
        switch (gender) {
            case "MALE":
                return "男";
            case "FEMALE":
                return "女";
            case "MIXED":
                return "混合";
            default:
                return gender;
        }
    }

    private String dateText(LocalDate date) {
        return date == null ? "不限" : date.toString();
    }

    private String textOf(String value) {
        return value == null ? "未知" : value;
    }

    /**
     * 单条报名请求（与 {@code RegistrationSaveDTO} 一一对应，剥离掉与校验无关的种子成绩字段）。
     */
    @Data
    public static class CheckRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 项目 ID */
        private Long eventId;

        /** 运动员 ID（个人项目） */
        private Long athleteId;

        /** 报名单位 ID */
        private Long unitId;

        /** 报名组别 ID */
        private Long groupId;

        /** 是否接力：0/1 */
        private Integer isRelay;

        /** 接力队序号 */
        private Integer relayTeamSeq;

        /** 接力队员运动员 ID（按棒次顺序） */
        private List<Long> memberAthleteIds = new ArrayList<>();

        /** 修改场景下需要排除自身的报名 ID */
        private Long excludeRegistrationId;
    }

    /**
     * 校验上下文：请求 + 规则 + 已加载的项目/运动员/组别 + 既有报名计数（全部由服务层装配，本引擎只读）。
     */
    @Data
    public static class CheckContext implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 届次 ID */
        private Long meetId;

        /** 届次状态（规则 1） */
        private String meetStatus;

        /** 每人限报项数（meet_rule_config.athlete_max_events，0/空不限） */
        private Integer athleteMaxEvents;

        /** 每人限报接力项数（meet_rule_config.relay_max_per_athlete，0/空不限） */
        private Integer relayMaxPerAthlete;

        /** 每单位每项限报人数（meet_rule_config.max_per_unit，项目级优先） */
        private Integer maxPerUnit;

        /** 限值来源：MEET/EVENT */
        private String ruleSource;

        /** 项目投影：eventId -> 项目 */
        private Map<Long, EventBriefVO> eventMap = new HashMap<>();

        /** 运动员：athleteId -> 运动员 */
        private Map<Long, Athlete> athleteMap = new HashMap<>();

        /** 组别：groupId -> 组别 */
        private Map<Long, MeetGroup> groupMap = new HashMap<>();

        /** 既有重复报名键 -> 状态（已排除 CANCELLED/REJECTED 与待修改记录自身） */
        private Map<String, String> existingKeys = new HashMap<>();

        /** 单位 × 项目 有效报名计数（待审 + 已通过） */
        private Map<String, Integer> unitEventCount = new HashMap<>();

        /** 运动员个人项目计数 */
        private Map<Long, Integer> athleteIndividualCount = new HashMap<>();

        /** 运动员接力项目计数 */
        private Map<Long, Integer> athleteRelayCount = new HashMap<>();

        /** 接力队员占位索引：项目+运动员 -> 报名 ID */
        private Map<String, Long> relayMemberIndex = new HashMap<>();

        /** 待校验请求 */
        private List<CheckRequest> requests = new ArrayList<>();
    }
}
