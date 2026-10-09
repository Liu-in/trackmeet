package com.trackmeet.modules.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.dto.MeetRuleConfigSaveDTO;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import com.trackmeet.modules.event.mapper.MeetRuleConfigMapper;
import com.trackmeet.modules.event.service.MeetRuleConfigService;
import com.trackmeet.modules.event.vo.MeetRuleConfigVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 届次级默认规则服务实现。
 * <p>
 * <b>系统兜底默认值清单（仅当 {@code meet_rule_config} 与 {@code event_rule_config} 两处都为空时使用）</b>：
 * <ul>
 *     <li>录取名次 {@code admitRank} = 8（R-01）</li>
 *     <li>兼项最小间隔 {@code conflictIntervalMin} = 30 分钟（R-07）</li>
 *     <li>道次顺序：8 道 {@code [4,5,3,6,2,7,1,8]}、6 道 {@code [3,4,2,5,1,6]}，按 {@code defaultLaneCount} 选择（R-08）</li>
 *     <li>默认道数 {@code defaultLaneCount} = 8、道次分配方式 {@code laneAssignMode} = SEED</li>
 *     <li>道次顺序 JSON 列兜底默认 {@code defaultLaneOrderJson} = {@code [4,5,3,6,2,7,1,8]}</li>
 *     <li>风速上限 {@code {"applicable":true,"maxLegal":2.0}}，即 +2.0 m/s（R-13）</li>
 *     <li>田赛试跳 / 试掷次数 {@code fieldAttemptCount} = 3（R-14）</li>
 *     <li>平局规则 {@code ["BEST_SECONDARY","FARTHEST_SUCCESSFUL_ROUND","TIE"]}（R-16）</li>
 *     <li>计时精度 {@code timePrecision} = HUNDREDTH（R-20）</li>
 *     <li>接力双倍系数 {@code relayDoubleFactor} = 2.00（R-06）</li>
 *     <li>奖牌榜排序 {@code medalSort} = GOLD_FIRST（R-22）</li>
 *     <li>破纪录加分是否计入团体分 {@code bonusInTeamScore} = 1 计入（R-21）</li>
 * </ul>
 * 这些常量只做「兜底默认」，任何一层配置表有值即以配置为准，代码中不存在其它业务阈值分支。
 *
 * @author TrackMeet
 */
@Service
public class MeetRuleConfigServiceImpl extends ServiceImpl<MeetRuleConfigMapper, MeetRuleConfig>
        implements MeetRuleConfigService {

    /** 兜底默认：录取名次（R-01） */
    private static final int DEFAULT_ADMIT_RANK = 8;
    /** 兜底默认：兼项最小间隔分钟数（R-07） */
    private static final int DEFAULT_CONFLICT_INTERVAL_MIN = 30;
    /** 兜底默认：裁判连场最小间隔分钟数 */
    private static final int DEFAULT_REFEREE_INTERVAL_MIN = 30;
    /** 兜底默认：每人限报项数（R-11） */
    private static final int DEFAULT_ATHLETE_MAX_EVENTS = 2;
    /** 兜底默认：每人限报接力项数 */
    private static final int DEFAULT_RELAY_MAX_PER_ATHLETE = 2;
    /** 兜底默认：每单位每项限报人数（R-10） */
    private static final int DEFAULT_MAX_PER_UNIT = 3;
    /** 兜底默认：每单位报名总人数上限 */
    private static final int DEFAULT_MAX_ATHLETE_PER_UNIT = 0;
    /** 兜底默认：接力双倍系数（R-06） */
    private static final BigDecimal DEFAULT_RELAY_DOUBLE_FACTOR = new BigDecimal("2.00");
    /** 兜底默认：田赛试跳 / 试掷次数（R-14） */
    private static final int DEFAULT_FIELD_ATTEMPT_COUNT = 3;
    /** 兜底默认：默认道数（R-08） */
    private static final int DEFAULT_LANE_COUNT = 8;
    /** 兜底默认：8 道道次顺序（R-08） */
    private static final String DEFAULT_LANE_ORDER_8 = "[4,5,3,6,2,7,1,8]";
    /** 兜底默认：6 道道次顺序（R-08） */
    private static final String DEFAULT_LANE_ORDER_6 = "[3,4,2,5,1,6]";
    /** 兜底默认：道次分配方式 */
    private static final String DEFAULT_LANE_ASSIGN_MODE = "SEED";
    /** 兜底默认：风速规则（R-13，合法风速上限 +2.0 m/s） */
    private static final String DEFAULT_WIND_JSON = "{\"applicable\":true,\"maxLegal\":2.0}";
    /** 兜底默认：平局规则（R-16，次优成绩 → 最远成功轮次 → 并列） */
    private static final String DEFAULT_TIE_BREAK_JSON =
            "[\"BEST_SECONDARY\",\"FARTHEST_SUCCESSFUL_ROUND\",\"TIE\"]";
    /** 兜底默认：计时精度（R-20） */
    private static final String DEFAULT_TIME_PRECISION = "HUNDREDTH";
    /** 兜底默认：奖牌榜排序（R-22） */
    private static final String DEFAULT_MEDAL_SORT = "GOLD_FIRST";
    /** 兜底默认：破纪录加分计入团体分（R-21） */
    private static final int DEFAULT_BONUS_IN_TEAM_SCORE = 1;

    @Override
    public MeetRuleConfig getRaw(Long meetId) {
        if (meetId == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<MeetRuleConfig>()
                .eq(MeetRuleConfig::getMeetId, meetId)
                .last("LIMIT 1"), false);
    }

    @Override
    public MeetRuleConfigVO getEffective(Long meetId) {
        MeetRuleConfig resolved = resolveEffective(meetId);
        MeetRuleConfigVO vo = new MeetRuleConfigVO();
        vo.setId(resolved.getId());
        vo.setMeetId(resolved.getMeetId());
        vo.setAdmitRank(resolved.getAdmitRank());
        vo.setScoreRuleId(resolved.getScoreRuleId());
        vo.setAthleteMaxEvents(resolved.getAthleteMaxEvents());
        vo.setRelayMaxPerAthlete(resolved.getRelayMaxPerAthlete());
        vo.setMaxPerUnit(resolved.getMaxPerUnit());
        vo.setMaxAthletePerUnit(resolved.getMaxAthletePerUnit());
        vo.setConflictIntervalMin(resolved.getConflictIntervalMin());
        vo.setRefereeIntervalMin(resolved.getRefereeIntervalMin());
        vo.setRelayDoubleFactor(resolved.getRelayDoubleFactor());
        vo.setBonusInTeamScore(resolved.getBonusInTeamScore());
        vo.setMedalSort(resolved.getMedalSort());
        vo.setTimePrecision(resolved.getTimePrecision());
        vo.setFieldAttemptCount(resolved.getFieldAttemptCount());
        vo.setDefaultLaneCount(resolved.getDefaultLaneCount());
        vo.setDefaultLaneOrderJson(resolved.getDefaultLaneOrderJson());
        vo.setLaneAssignMode(resolved.getLaneAssignMode());
        vo.setWindJson(resolved.getWindJson());
        vo.setTieBreakJson(resolved.getTieBreakJson());
        vo.setExtraJson(resolved.getExtraJson());
        return vo;
    }

    @Override
    public MeetRuleConfig resolveEffective(Long meetId) {
        MeetRuleConfig raw = getRaw(meetId);
        MeetRuleConfig effective = new MeetRuleConfig();
        effective.setId(raw == null ? null : raw.getId());
        effective.setMeetId(meetId);
        effective.setAdmitRank(orDefault(raw == null ? null : raw.getAdmitRank(), DEFAULT_ADMIT_RANK));
        effective.setScoreRuleId(raw == null ? null : raw.getScoreRuleId());
        effective.setAthleteMaxEvents(
                orDefault(raw == null ? null : raw.getAthleteMaxEvents(), DEFAULT_ATHLETE_MAX_EVENTS));
        effective.setRelayMaxPerAthlete(
                orDefault(raw == null ? null : raw.getRelayMaxPerAthlete(), DEFAULT_RELAY_MAX_PER_ATHLETE));
        effective.setMaxPerUnit(orDefault(raw == null ? null : raw.getMaxPerUnit(), DEFAULT_MAX_PER_UNIT));
        effective.setMaxAthletePerUnit(
                orDefault(raw == null ? null : raw.getMaxAthletePerUnit(), DEFAULT_MAX_ATHLETE_PER_UNIT));
        effective.setConflictIntervalMin(
                orDefault(raw == null ? null : raw.getConflictIntervalMin(), DEFAULT_CONFLICT_INTERVAL_MIN));
        effective.setRefereeIntervalMin(
                orDefault(raw == null ? null : raw.getRefereeIntervalMin(), DEFAULT_REFEREE_INTERVAL_MIN));
        effective.setRelayDoubleFactor(orDefault(
                raw == null ? null : raw.getRelayDoubleFactor(), DEFAULT_RELAY_DOUBLE_FACTOR));
        effective.setBonusInTeamScore(orDefault(
                raw == null ? null : raw.getBonusInTeamScore(), DEFAULT_BONUS_IN_TEAM_SCORE));
        effective.setMedalSort(orDefault(raw == null ? null : raw.getMedalSort(), DEFAULT_MEDAL_SORT));
        effective.setTimePrecision(orDefault(raw == null ? null : raw.getTimePrecision(), DEFAULT_TIME_PRECISION));

        Integer laneCount = orDefault(raw == null ? null : raw.getDefaultLaneCount(), DEFAULT_LANE_COUNT);
        effective.setDefaultLaneCount(laneCount);
        String laneOrder = raw == null ? null : raw.getDefaultLaneOrderJson();
        if (!RuleJsonValidator.isValid(laneOrder)) {
            // 未配置道次顺序时按道数选择内置顺序：8 道 [4,5,3,6,2,7,1,8]、6 道 [3,4,2,5,1,6]
            laneOrder = laneOrderByCount(laneCount);
        }
        effective.setDefaultLaneOrderJson(laneOrder);
        effective.setLaneAssignMode(
                orDefault(raw == null ? null : raw.getLaneAssignMode(), DEFAULT_LANE_ASSIGN_MODE));
        effective.setWindJson(
                orDefaultValidJson(raw == null ? null : raw.getWindJson(), DEFAULT_WIND_JSON));
        effective.setTieBreakJson(
                orDefaultValidJson(raw == null ? null : raw.getTieBreakJson(), DEFAULT_TIE_BREAK_JSON));
        effective.setFieldAttemptCount(orDefault(
                raw == null ? null : raw.getFieldAttemptCount(), DEFAULT_FIELD_ATTEMPT_COUNT));
        effective.setExtraJson(raw == null ? null : raw.getExtraJson());
        effective.setRemark(raw == null ? null : raw.getRemark());
        return effective;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveByMeetId(Long meetId, MeetRuleConfigSaveDTO dto) {
        Long resolvedMeetId = resolveMeetId(meetId);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则参数不能为空");
        }
        validate(dto);
        MeetRuleConfig entity = new MeetRuleConfig();
        entity.setMeetId(resolvedMeetId);
        entity.setAdmitRank(dto.getAdmitRank());
        entity.setScoreRuleId(dto.getScoreRuleId());
        entity.setAthleteMaxEvents(dto.getAthleteMaxEvents());
        entity.setRelayMaxPerAthlete(dto.getRelayMaxPerAthlete());
        entity.setMaxPerUnit(dto.getMaxPerUnit());
        entity.setMaxAthletePerUnit(dto.getMaxAthletePerUnit());
        entity.setConflictIntervalMin(dto.getConflictIntervalMin());
        entity.setRefereeIntervalMin(dto.getRefereeIntervalMin());
        entity.setRelayDoubleFactor(dto.getRelayDoubleFactor());
        entity.setBonusInTeamScore(dto.getBonusInTeamScore());
        entity.setMedalSort(dto.getMedalSort());
        entity.setTimePrecision(dto.getTimePrecision());
        entity.setFieldAttemptCount(dto.getFieldAttemptCount());
        entity.setDefaultLaneCount(dto.getDefaultLaneCount());
        entity.setDefaultLaneOrderJson(dto.getDefaultLaneOrderJson());
        entity.setLaneAssignMode(dto.getLaneAssignMode());
        entity.setWindJson(dto.getWindJson());
        entity.setTieBreakJson(dto.getTieBreakJson());
        entity.setExtraJson(dto.getExtraJson());
        entity.setRemark(dto.getRemark());

        // 唯一键 uk_meet_id：不存在则插入、存在则更新（非空更新策略，null 字段保持原值）
        MeetRuleConfig exists = getRaw(resolvedMeetId);
        if (exists == null) {
            this.save(entity);
            return entity.getId();
        }
        entity.setId(exists.getId());
        entity.setVersion(exists.getVersion());
        if (!this.updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "届次规则已被其他用户修改，请刷新后重试");
        }
        return exists.getId();
    }

    /**
     * 按道数选择内置道次顺序（兜底默认）。
     *
     * @param laneCount 道数
     * @return 道次顺序 JSON 文本
     */
    private String laneOrderByCount(Integer laneCount) {
        int count = laneCount == null ? DEFAULT_LANE_COUNT : laneCount;
        return count == 6 ? DEFAULT_LANE_ORDER_6 : DEFAULT_LANE_ORDER_8;
    }

    /**
     * 非空兜底取值。
     */
    private static <T> T orDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * JSON 文本兜底：为空或非法时使用默认 JSON。
     */
    private static String orDefaultValidJson(String value, String defaultJson) {
        return RuleJsonValidator.isValid(value) ? value : defaultJson;
    }

    /**
     * 校验并补全届次 ID：入参与上下文的届次必须一致，避免跨届次写入。
     */
    private Long resolveMeetId(Long meetId) {
        Long contextMeetId = MeetContext.get();
        Long resolved = meetId != null ? meetId : contextMeetId;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次 ID 不能为空");
        }
        if (contextMeetId != null && !Objects.equals(contextMeetId, resolved)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的规则");
        }
        return resolved;
    }

    /**
     * 入参业务校验：JSON 列合法性 + 数值范围。
     */
    private void validate(MeetRuleConfigSaveDTO dto) {
        RuleJsonValidator.validate(dto.getDefaultLaneOrderJson(), "默认道次顺序");
        RuleJsonValidator.validate(dto.getWindJson(), "风速规则");
        RuleJsonValidator.validate(dto.getTieBreakJson(), "平局规则");
        RuleJsonValidator.validate(dto.getExtraJson(), "扩展规则");
        if (dto.getAdmitRank() != null && dto.getAdmitRank() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "录取名次必须大于 0");
        }
        if (dto.getConflictIntervalMin() != null && dto.getConflictIntervalMin() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "兼项间隔不能为负数");
        }
        if (dto.getRefereeIntervalMin() != null && dto.getRefereeIntervalMin() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "裁判连场间隔不能为负数");
        }
        if (dto.getDefaultLaneCount() != null && (dto.getDefaultLaneCount() < 1 || dto.getDefaultLaneCount() > 12)) {
            throw new BizException(ResultCode.PARAM_ERROR, "默认道数需在 1 ~ 12 之间");
        }
        if (dto.getFieldAttemptCount() != null && dto.getFieldAttemptCount() < 1) {
            throw new BizException(ResultCode.PARAM_ERROR, "田赛试跳次数必须大于 0");
        }
        if (dto.getRelayDoubleFactor() != null && dto.getRelayDoubleFactor().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "接力双倍系数不能为负数");
        }
    }
}
