package com.trackmeet.modules.event.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.event.dto.EventRoundRuleSaveDTO;
import com.trackmeet.modules.event.dto.EventRuleConfigSaveDTO;
import com.trackmeet.modules.event.service.EventRoundService;
import com.trackmeet.modules.event.service.EventRuleService;
import com.trackmeet.modules.event.vo.EventRoundRuleVO;
import com.trackmeet.modules.event.vo.EventRoundVO;
import com.trackmeet.modules.event.vo.EventRuleConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则配置接口：轮次级规则（晋级 / 录取）与项目级规则（含来源标注、恢复继承）。
 * <p>
 * 权限编码对照：读取 {@code event:rule:list}、写入与恢复继承 {@code event:rule:edit}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class EventRuleController {

    private final EventRoundService eventRoundService;
    private final EventRuleService eventRuleService;

    /**
     * 轮次级规则详情。
     * <p>
     * 复用轮次详情结构，返回 {@code rule} 字段（未配置时为 {@code null}）；
     * 前端可直接读取 {@code data.rule}。
     */
    @GetMapping("/rounds/{id}/rule")
    @PreAuthorize("@perm.has('event:rule:list')")
    public R<EventRoundRuleVO> roundRule(@PathVariable Long id) {
        EventRoundVO round = eventRoundService.getRoundDetail(id);
        return R.ok(round == null ? null : round.getRule());
    }

    /**
     * 保存轮次级规则与录取名次（round_id 唯一，先查后插 / 更新；advanceJson 必须为合法 JSON）。
     */
    @PutMapping("/rounds/{id}/rule")
    @PreAuthorize("@perm.has('event:rule:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "保存轮次级规则", targetType = "event_round")
    public R<Long> saveRoundRule(@PathVariable Long id, @Valid @RequestBody EventRoundRuleSaveDTO dto) {
        return R.ok(eventRoundService.saveRoundRule(id, dto));
    }

    /**
     * 项目级规则（未配置字段回退届次默认并在 VO 中标注来源 PROJECT / MEET / DEFAULT）。
     */
    @GetMapping("/events/{id}/rule-config")
    @PreAuthorize("@perm.has('event:rule:list')")
    public R<EventRuleConfigVO> ruleConfig(@PathVariable Long id) {
        return R.ok(eventRuleService.getRuleConfig(id));
    }

    /**
     * 保存项目级规则（仅写入非空字段，空值语义为「继承上一层」）。
     */
    @PutMapping("/events/{id}/rule-config")
    @PreAuthorize("@perm.has('event:rule:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "保存项目级规则", targetType = "event")
    public R<Long> saveRuleConfig(@PathVariable Long id, @Valid @RequestBody EventRuleConfigSaveDTO dto) {
        return R.ok(eventRuleService.saveRuleConfig(id, dto));
    }

    /**
     * 恢复继承：删除项目级规则行，使全部配置回退到届次级 / 系统默认。
     */
    @PostMapping("/events/{id}/rule-config/reset")
    @PreAuthorize("@perm.has('event:rule:edit')")
    @OpLog(module = "EVENT", action = "RESET", actionName = "恢复规则继承", targetType = "event")
    public R<Void> resetRuleConfig(@PathVariable Long id) {
        eventRuleService.resetRuleConfig(id);
        return R.ok();
    }
}
