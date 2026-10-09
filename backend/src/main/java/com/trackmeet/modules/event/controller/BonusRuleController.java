package com.trackmeet.modules.event.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.event.dto.BonusRuleSaveDTO;
import com.trackmeet.modules.event.service.BonusRuleService;
import com.trackmeet.modules.event.vo.BonusRuleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 加分规则接口。
 * <p>
 * 权限编码对照：查询 {@code rule:score:list}、写入（新增 / 修改 / 删除）{@code rule:bonus:edit}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/bonus-rules")
@RequiredArgsConstructor
@Validated
public class BonusRuleController {

    private final BonusRuleService bonusRuleService;

    /**
     * 加分规则分页列表（可按 bonusType 过滤）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('rule:score:list')")
    public R<PageResult<BonusRuleVO>> page(@RequestParam(required = false) Long meetId,
                                           @RequestParam(required = false) String bonusType,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return R.ok(bonusRuleService.pageRule(meetId, bonusType, status, keyword, page, size));
    }

    /**
     * 加分规则详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('rule:score:list')")
    public R<BonusRuleVO> detail(@PathVariable Long id) {
        return R.ok(bonusRuleService.getRuleDetail(id));
    }

    /**
     * 新增加分规则（rule_code 届次内唯一；bonus_type：RECORD 破纪录 / RELAY 接力双倍 / BREAK_TIE 破平局）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('rule:bonus:edit')")
    @OpLog(module = "EVENT", action = "ADD", actionName = "新增加分规则", targetType = "bonus_rule")
    public R<Long> add(@Valid @RequestBody BonusRuleSaveDTO dto) {
        return R.ok(bonusRuleService.createRule(dto));
    }

    /**
     * 修改加分规则。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('rule:bonus:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "修改加分规则", targetType = "bonus_rule")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody BonusRuleSaveDTO dto) {
        bonusRuleService.updateRule(id, dto);
        return R.ok();
    }

    /**
     * 删除加分规则。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('rule:bonus:edit')")
    @OpLog(module = "EVENT", action = "DELETE", actionName = "删除加分规则", targetType = "bonus_rule")
    public R<Void> remove(@PathVariable Long id) {
        bonusRuleService.deleteRule(id);
        return R.ok();
    }
}
