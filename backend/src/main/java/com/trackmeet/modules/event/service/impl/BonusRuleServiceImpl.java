package com.trackmeet.modules.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.event.dto.BonusRuleSaveDTO;
import com.trackmeet.modules.event.entity.BonusRule;
import com.trackmeet.modules.event.mapper.BonusRuleMapper;
import com.trackmeet.modules.event.service.BonusRuleService;
import com.trackmeet.modules.event.vo.BonusRuleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 加分规则服务实现：破纪录 / 接力双倍 / 破平局三类加分规则的 CRUD。
 * <p>
 * 加分值与倍数（如破赛会纪录 +9、接力双倍 2.00）全部来自配置表，代码只做类型与取值范围校验，
 * 不内置任何加分阈值。
 *
 * @author TrackMeet
 */
@Service
public class BonusRuleServiceImpl extends ServiceImpl<BonusRuleMapper, BonusRule> implements BonusRuleService {

    @Override
    public PageResult<BonusRuleVO> pageRule(Long meetId, String bonusType, String status, String keyword,
                                           long page, long size) {
        LambdaQueryWrapper<BonusRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(meetId != null, BonusRule::getMeetId, meetId)
                .eq(StringUtils.hasText(bonusType), BonusRule::getBonusType, bonusType)
                .eq(StringUtils.hasText(status), BonusRule::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(BonusRule::getRuleName, kw).or().like(BonusRule::getRuleCode, kw));
        }
        wrapper.orderByAsc(BonusRule::getPriority).orderByAsc(BonusRule::getId);
        Page<BonusRule> result = this.page(new Page<>(page < 1 ? 1 : page, size < 1 ? 20 : size), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVo).collect(Collectors.toList()),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public BonusRuleVO getRuleDetail(Long id) {
        return toVo(getEntity(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(BonusRuleSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "加分规则参数不能为空");
        }
        Long meetId = resolveMeetId(dto.getMeetId());
        checkRuleCode(dto.getRuleCode());
        validate(dto);
        BonusRule entity = new BonusRule();
        entity.setMeetId(meetId);
        applySaveDto(entity, dto);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(BonusRule.STATUS_ENABLED);
        }
        if (entity.getIsStackable() == null) {
            entity.setIsStackable(0);
        }
        if (entity.getIsInTeamScore() == null) {
            entity.setIsInTeamScore(1);
        }
        checkMeetScopedUnique(meetId, entity.getRuleCode(), null);
        this.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(Long id, BonusRuleSaveDTO dto) {
        BonusRule exists = getEntity(id);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "加分规则参数不能为空");
        }
        if (dto.getMeetId() != null && !Objects.equals(dto.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改加分规则");
        }
        checkRuleCode(dto.getRuleCode());
        validate(dto);
        checkMeetScopedUnique(exists.getMeetId(), dto.getRuleCode(), id);
        BonusRule patch = new BonusRule();
        patch.setId(id);
        patch.setVersion(exists.getVersion());
        applySaveDto(patch, dto);
        if (!this.updateById(patch)) {
            throw new BizException(ResultCode.CONFLICT, "加分规则已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        getEntity(id);
        this.removeById(id);
    }

    @Override
    public BonusRule getEntity(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "加分规则 ID 不能为空");
        }
        BonusRule rule = this.getById(id);
        if (rule == null) {
            throw new BizException(ResultCode.NOT_FOUND, "加分规则不存在");
        }
        return rule;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 入参业务校验：加分类型与取值配合、倍数为正、生效轮次码合法。
     */
    private void validate(BonusRuleSaveDTO dto) {
        if (BonusRule.TYPE_RECORD.equals(dto.getBonusType())) {
            if (dto.getBonusPoint() == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "破纪录加分规则必须填写加分值");
            }
            if (!StringUtils.hasText(dto.getRecordType())) {
                throw new BizException(ResultCode.PARAM_ERROR, "破纪录加分规则必须填写纪录层级");
            }
        }
        if (BonusRule.TYPE_RELAY.equals(dto.getBonusType()) && dto.getFactor() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "接力双倍规则必须填写倍数");
        }
        if (dto.getBonusPoint() != null && dto.getBonusPoint().signum() < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "加分值不能为负数");
        }
        if (dto.getFactor() != null && dto.getFactor().signum() <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "倍数必须大于 0");
        }
        if (StringUtils.hasText(dto.getEffectiveRoundCodes())) {
            for (String code : dto.getEffectiveRoundCodes().split(",")) {
                String trimmed = code.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!"QUALIFY".equals(trimmed) && !"PRE".equals(trimmed) && !"R1".equals(trimmed)
                        && !"R2".equals(trimmed) && !"SEMI".equals(trimmed) && !"FINAL".equals(trimmed)) {
                    throw new BizException(ResultCode.PARAM_ERROR, "生效轮次码取值不正确：" + trimmed);
                }
            }
        }
    }

    /**
     * DTO → 实体赋值（不含 meetId 与 id）。
     */
    private void applySaveDto(BonusRule entity, BonusRuleSaveDTO dto) {
        entity.setRuleName(dto.getRuleName() == null ? null : dto.getRuleName().trim());
        entity.setRuleCode(dto.getRuleCode() == null ? null : dto.getRuleCode().trim());
        entity.setBonusType(dto.getBonusType());
        entity.setRecordType(dto.getRecordType());
        entity.setBonusPoint(dto.getBonusPoint());
        entity.setIsStackable(dto.getIsStackable());
        entity.setFactor(dto.getFactor());
        entity.setEventId(dto.getEventId());
        entity.setGroupId(dto.getGroupId());
        entity.setApplyEventType(dto.getApplyEventType());
        entity.setApplyGender(dto.getApplyGender());
        entity.setEffectiveRoundCodes(dto.getEffectiveRoundCodes());
        entity.setIsInTeamScore(dto.getIsInTeamScore());
        entity.setPriority(dto.getPriority());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
    }

    /**
     * 实体 → VO。
     */
    private BonusRuleVO toVo(BonusRule rule) {
        BonusRuleVO vo = new BonusRuleVO();
        vo.setId(rule.getId());
        vo.setMeetId(rule.getMeetId());
        vo.setRuleName(rule.getRuleName());
        vo.setRuleCode(rule.getRuleCode());
        vo.setBonusType(rule.getBonusType());
        vo.setRecordType(rule.getRecordType());
        vo.setBonusPoint(rule.getBonusPoint());
        vo.setIsStackable(rule.getIsStackable());
        vo.setFactor(rule.getFactor());
        vo.setEventId(rule.getEventId());
        vo.setGroupId(rule.getGroupId());
        vo.setApplyEventType(rule.getApplyEventType());
        vo.setApplyGender(rule.getApplyGender());
        vo.setEffectiveRoundCodes(rule.getEffectiveRoundCodes());
        vo.setIsInTeamScore(rule.getIsInTeamScore());
        vo.setPriority(rule.getPriority());
        vo.setStatus(rule.getStatus());
        vo.setRemark(rule.getRemark());
        return vo;
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
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的加分规则");
        }
        return resolved;
    }

    /**
     * UK(meet_id, rule_code) 校验。
     */
    private void checkMeetScopedUnique(Long meetId, String ruleCode, Long excludeId) {
        LambdaQueryWrapper<BonusRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BonusRule::getMeetId, meetId)
                .eq(BonusRule::getRuleCode, ruleCode)
                .ne(excludeId != null, BonusRule::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该届次下规则编码已存在：" + ruleCode);
        }
    }

    private void checkRuleCode(String ruleCode) {
        if (!StringUtils.hasText(ruleCode)) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则编码不能为空");
        }
        if (ruleCode.trim().length() > 64) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则编码长度不能超过 64");
        }
    }
}
