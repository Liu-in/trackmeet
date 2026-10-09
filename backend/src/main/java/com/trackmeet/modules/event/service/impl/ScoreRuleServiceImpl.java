package com.trackmeet.modules.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.dto.ScoreRuleItemDTO;
import com.trackmeet.modules.event.dto.ScoreRuleSaveDTO;
import com.trackmeet.modules.event.entity.ScoreRule;
import com.trackmeet.modules.event.entity.ScoreRuleItem;
import com.trackmeet.modules.event.mapper.ScoreRuleItemMapper;
import com.trackmeet.modules.event.mapper.ScoreRuleMapper;
import com.trackmeet.modules.event.service.ScoreRuleService;
import com.trackmeet.modules.event.vo.ScoreRuleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 名次分方案服务实现：方案 CRUD 与名次分明细批量维护。
 * <p>
 * 明细保存采用「先按 {@code rule_id} 物理删除再批量插入」，并回写 {@code score_rule.rank_count}；
 * 明细默认分值与并列计分方式全部来自配置，代码不内置 9-7-6-5 之类的分值数组
 * （内置默认方案由初始化数据写入 {@code score_rule} / {@code score_rule_item}）。
 *
 * @author TrackMeet
 */
@Service
public class ScoreRuleServiceImpl extends ServiceImpl<ScoreRuleMapper, ScoreRule> implements ScoreRuleService {

    /** 明细批量插入的单批上限 */
    private static final int BATCH_SIZE = 500;

    /** 兜底默认：rule_code 列长度上限（VARCHAR(64)） */
    private static final int CODE_MAX_LENGTH = 64;

    /** 逻辑删除时唯一键后缀前缀 */
    private static final String DELETED_SUFFIX = "_d";

    private final ScoreRuleItemMapper scoreRuleItemMapper;

    public ScoreRuleServiceImpl(ScoreRuleItemMapper scoreRuleItemMapper) {
        this.scoreRuleItemMapper = scoreRuleItemMapper;
    }

    @Override
    public PageResult<ScoreRuleVO> pageRule(Long meetId, String status, String keyword, long page, long size) {
        LambdaQueryWrapper<ScoreRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(meetId != null, ScoreRule::getMeetId, meetId)
                .eq(StringUtils.hasText(status), ScoreRule::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ScoreRule::getRuleName, kw).or().like(ScoreRule::getRuleCode, kw));
        }
        wrapper.orderByDesc(ScoreRule::getIsDefault).orderByAsc(ScoreRule::getId);
        Page<ScoreRule> result = this.page(new Page<>(page < 1 ? 1 : page, size < 1 ? 20 : size), wrapper);
        List<ScoreRuleVO> records = result.getRecords().stream()
                .map(this::toVo)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ScoreRuleVO getRuleDetail(Long id) {
        ScoreRule rule = getEntity(id);
        ScoreRuleVO vo = toVo(rule);
        vo.setItems(listItems(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(ScoreRuleSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "方案参数不能为空");
        }
        Long meetId = resolveMeetId(dto.getMeetId());
        checkRuleCode(dto.getRuleCode());
        ScoreRule entity = new ScoreRule();
        entity.setMeetId(meetId);
        applySaveDto(entity, dto);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(ScoreRule.STATUS_ENABLED);
        }
        if (entity.getIsDefault() == null) {
            entity.setIsDefault(0);
        }
        if (entity.getIsTeamScore() == null) {
            entity.setIsTeamScore(1);
        }
        checkMeetScopedUnique(meetId, entity.getRuleCode(), null);
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(Long id, ScoreRuleSaveDTO dto) {
        ScoreRule exists = getEntity(id);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "方案参数不能为空");
        }
        if (dto.getMeetId() != null && !Objects.equals(dto.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改名次分方案");
        }
        checkRuleCode(dto.getRuleCode());
        checkMeetScopedUnique(exists.getMeetId(), dto.getRuleCode(), id);
        ScoreRule patch = new ScoreRule();
        patch.setId(id);
        patch.setVersion(exists.getVersion());
        applySaveDto(patch, dto);
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "名次分方案已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        ScoreRule rule = getEntity(id);
        if (rule.getIsDefault() != null && rule.getIsDefault() == 1) {
            throw new BizException(ResultCode.CONFLICT, "届次默认名次分方案禁止删除，请先切换默认方案");
        }
        long meetReferences = baseMapper.countMeetRuleReference(rule.getMeetId(), id);
        if (meetReferences > 0) {
            throw new BizException(ResultCode.CONFLICT, "该方案已被届次默认规则引用，禁止删除");
        }
        long eventReferences = baseMapper.countEventReference(rule.getMeetId(), id);
        if (eventReferences > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "该方案已被 " + eventReferences + " 个项目绑定，禁止删除");
        }
        // 同时删除明细（物理删除，避免与唯一键 uk_scoreitem_rule_rank 冲突）
        scoreRuleItemMapper.deleteByRuleId(rule.getMeetId(), id);
        // 逻辑删除前改写 rule_code 释放届次内唯一键（uk_scorerule_meet_code），编码可在后续复用
        ScoreRule patch = new ScoreRule();
        patch.setId(id);
        patch.setRuleCode(buildDeletedCode(rule.getRuleCode(), id, CODE_MAX_LENGTH, DELETED_SUFFIX));
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "名次分方案已被其他用户修改，请刷新后重试");
        }
        this.removeById(id);
    }

    @Override
    public List<ScoreRuleItemDTO> listItems(Long ruleId) {
        getEntity(ruleId);
        List<ScoreRuleItem> items = scoreRuleItemMapper.selectList(new LambdaQueryWrapper<ScoreRuleItem>()
                .eq(ScoreRuleItem::getRuleId, ruleId)
                .orderByAsc(ScoreRuleItem::getRankNo));
        return items.stream().map(this::toItemDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long ruleId, List<ScoreRuleItemDTO> items) {
        ScoreRule rule = getEntity(ruleId);
        List<ScoreRuleItemDTO> source = items == null ? new ArrayList<>() : items;
        validateItems(source);

        // 先物理删除旧明细，再批量插入（保证 (rule_id, rank_no) 唯一且无残留）
        scoreRuleItemMapper.deleteByRuleId(rule.getMeetId(), ruleId);
        if (!source.isEmpty()) {
            List<ScoreRuleItem> entities = new ArrayList<>(source.size());
            int index = 0;
            for (ScoreRuleItemDTO dto : source) {
                index++;
                ScoreRuleItem entity = new ScoreRuleItem();
                entity.setMeetId(rule.getMeetId());
                entity.setRuleId(ruleId);
                entity.setRankNo(dto.getRankNo());
                entity.setRankFrom(dto.getRankFrom());
                entity.setRankTo(dto.getRankTo());
                entity.setRankLabel(dto.getRankLabel());
                entity.setPointValue(dto.getPointValue());
                entity.setBonusPoint(dto.getBonusPoint());
                entity.setTiePolicy(dto.getTiePolicy());
                entity.setIsTeamScore(dto.getIsTeamScore());
                entity.setSortNo(dto.getSortNo() == null ? index : dto.getSortNo());
                entity.setRemark(dto.getRemark());
                entities.add(entity);
            }
            for (int from = 0; from < entities.size(); from += BATCH_SIZE) {
                int to = Math.min(from + BATCH_SIZE, entities.size());
                for (ScoreRuleItem entity : entities.subList(from, to)) {
                    scoreRuleItemMapper.insert(entity);
                }
            }
        }
        // 回写名次数量（明细行数）
        ScoreRule patch = new ScoreRule();
        patch.setId(ruleId);
        patch.setRankCount(source.size());
        patch.setVersion(rule.getVersion());
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "名次分方案已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    public ScoreRule getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "名次分方案 ID 不能为空");
        }
        ScoreRule rule = this.getById(id);
        if (rule == null) {
            throw new BizException(ResultCode.NOT_FOUND, "名次分方案不存在");
        }
        return rule;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 明细入参校验：ranK_no 唯一、区间合法、分值非负。
     */
    private void validateItems(List<ScoreRuleItemDTO> items) {
        Set<Integer> rankNos = new LinkedHashSet<>();
        for (ScoreRuleItemDTO dto : items) {
            if (dto == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "名次分明细不能为空行");
            }
            if (dto.getRankNo() == null || dto.getRankNo() < 1) {
                throw new BizException(ResultCode.PARAM_ERROR, "名次序号必须大于 0");
            }
            if (!rankNos.add(dto.getRankNo())) {
                throw new BizException(ResultCode.CONFLICT, "名次序号重复：" + dto.getRankNo());
            }
            if (dto.getRankFrom() != null && dto.getRankTo() != null && dto.getRankFrom() > dto.getRankTo()) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "名次区间起不能大于区间止：第 " + dto.getRankNo() + " 行");
            }
            if (dto.getPointValue() != null && dto.getPointValue().signum() < 0) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "分值不能为负数：第 " + dto.getRankNo() + " 行");
            }
            if (dto.getBonusPoint() != null && dto.getBonusPoint().signum() < 0) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "附加分不能为负数：第 " + dto.getRankNo() + " 行");
            }
        }
    }

    /**
     * DTO → 实体赋值（不含 meetId 与 id）。
     */
    private void applySaveDto(ScoreRule entity, ScoreRuleSaveDTO dto) {
        entity.setRuleName(dto.getRuleName() == null ? null : dto.getRuleName().trim());
        entity.setRuleCode(dto.getRuleCode() == null ? null : dto.getRuleCode().trim());
        entity.setTiePolicy(dto.getTiePolicy());
        entity.setScoreTarget(dto.getScoreTarget());
        entity.setRankCount(dto.getRankCount());
        entity.setPointBase(dto.getPointBase());
        entity.setIsDefault(dto.getIsDefault());
        entity.setApplyEventType(dto.getApplyEventType());
        entity.setApplyGender(dto.getApplyGender());
        entity.setGroupId(dto.getGroupId());
        entity.setEffectiveRoundCode(dto.getEffectiveRoundCode());
        entity.setIsTeamScore(dto.getIsTeamScore());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
    }

    /**
     * 实体 → VO。
     */
    private ScoreRuleVO toVo(ScoreRule rule) {
        ScoreRuleVO vo = new ScoreRuleVO();
        vo.setId(rule.getId());
        vo.setMeetId(rule.getMeetId());
        vo.setRuleName(rule.getRuleName());
        vo.setRuleCode(rule.getRuleCode());
        vo.setTiePolicy(rule.getTiePolicy());
        vo.setIsDefault(rule.getIsDefault());
        vo.setApplyEventType(rule.getApplyEventType());
        vo.setApplyGender(rule.getApplyGender());
        vo.setStatus(rule.getStatus());
        vo.setScoreTarget(rule.getScoreTarget());
        vo.setRankCount(rule.getRankCount());
        vo.setPointBase(rule.getPointBase());
        vo.setGroupId(rule.getGroupId());
        vo.setEffectiveRoundCode(rule.getEffectiveRoundCode());
        vo.setIsTeamScore(rule.getIsTeamScore());
        vo.setRemark(rule.getRemark());
        return vo;
    }

    /**
     * 明细实体 → DTO。
     */
    private ScoreRuleItemDTO toItemDto(ScoreRuleItem item) {
        ScoreRuleItemDTO dto = new ScoreRuleItemDTO();
        dto.setRankNo(item.getRankNo());
        dto.setRankFrom(item.getRankFrom());
        dto.setRankTo(item.getRankTo());
        dto.setRankLabel(item.getRankLabel());
        dto.setPointValue(item.getPointValue());
        dto.setBonusPoint(item.getBonusPoint());
        dto.setTiePolicy(item.getTiePolicy());
        dto.setIsTeamScore(item.getIsTeamScore());
        dto.setSortNo(item.getSortNo());
        dto.setRemark(item.getRemark());
        return dto;
    }

    /**
     * 解析并校验届次 ID。
     */
    private Long resolveMeetId(Long meetId) {
        Long contextMeetId = MeetContext.get();
        Long resolved = meetId != null ? meetId : contextMeetId;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次 ID 不能为空");
        }
        if (contextMeetId != null && !Objects.equals(contextMeetId, resolved)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的名次分方案");
        }
        return resolved;
    }

    /**
     * UK(meet_id, rule_code) 校验。
     */
    private void checkMeetScopedUnique(Long meetId, String ruleCode, Long excludeId) {
        LambdaQueryWrapper<ScoreRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScoreRule::getMeetId, meetId)
                .eq(ScoreRule::getRuleCode, ruleCode)
                .ne(excludeId != null, ScoreRule::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该届次下方案编码已存在：" + ruleCode);
        }
    }

    private void checkRuleCode(String ruleCode) {
        if (!StringUtils.hasText(ruleCode)) {
            throw new BizException(ResultCode.PARAM_ERROR, "方案编码不能为空");
        }
        if (ruleCode.trim().length() > 64) {
            throw new BizException(ResultCode.PARAM_ERROR, "方案编码长度不能超过 64");
        }
    }

    /**
     * 生成改写后的唯一键：原值 + _d{id}，超长时截断原值以保证不超出列长度。
     * <p>
     * 与 {@code MeetGroupServiceImpl} 的「逻辑删除时改写唯一键」策略保持一致，
     * 使编码可在后续新增中复用、历史数据仍可追溯。
     *
     * @param code          原编码
     * @param id            记录主键
     * @param maxLength     列长度上限
     * @param suffixPrefix  后缀前缀
     * @return 改写后的编码
     */
    private String buildDeletedCode(String code, Long id, int maxLength, String suffixPrefix) {
        String suffix = suffixPrefix + id;
        String base = code == null ? "" : code;
        int maxBaseLength = maxLength - suffix.length();
        if (base.length() > maxBaseLength) {
            base = base.substring(0, Math.max(maxBaseLength, 0));
        }
        return base + suffix;
    }
}
