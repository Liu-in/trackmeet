package com.trackmeet.modules.event.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.event.dto.ScoreRuleItemDTO;
import com.trackmeet.modules.event.dto.ScoreRuleSaveDTO;
import com.trackmeet.modules.event.service.ScoreRuleService;
import com.trackmeet.modules.event.vo.ScoreRuleVO;
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

import java.util.List;

/**
 * 名次分方案接口。
 * <p>
 * 权限编码对照：查询与明细 {@code rule:score:list}、新增 {@code rule:score:add}、
 * 修改与明细保存 {@code rule:score:edit}、删除 {@code rule:score:delete}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/score-rules")
@RequiredArgsConstructor
@Validated
public class ScoreRuleController {

    private final ScoreRuleService scoreRuleService;

    /**
     * 名次分方案分页列表（可按 meetId、status 过滤）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('rule:score:list')")
    public R<PageResult<ScoreRuleVO>> page(@RequestParam(required = false) Long meetId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return R.ok(scoreRuleService.pageRule(meetId, status, keyword, page, size));
    }

    /**
     * 方案详情（含明细，按 rank_no 升序）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('rule:score:list')")
    public R<ScoreRuleVO> detail(@PathVariable Long id) {
        return R.ok(scoreRuleService.getRuleDetail(id));
    }

    /**
     * 新增方案（rule_code 届次内唯一）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('rule:score:add')")
    @OpLog(module = "EVENT", action = "ADD", actionName = "新增名次分方案", targetType = "score_rule")
    public R<Long> add(@Valid @RequestBody ScoreRuleSaveDTO dto) {
        return R.ok(scoreRuleService.createRule(dto));
    }

    /**
     * 修改方案。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('rule:score:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "修改名次分方案", targetType = "score_rule")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody ScoreRuleSaveDTO dto) {
        scoreRuleService.updateRule(id, dto);
        return R.ok();
    }

    /**
     * 删除方案（is_default=1 的默认方案禁删；同时删除明细）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('rule:score:delete')")
    @OpLog(module = "EVENT", action = "DELETE", actionName = "删除名次分方案", targetType = "score_rule")
    public R<Void> remove(@PathVariable Long id) {
        scoreRuleService.deleteRule(id);
        return R.ok();
    }

    /**
     * 方案明细列表。
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("@perm.has('rule:score:list')")
    public R<List<ScoreRuleItemDTO>> items(@PathVariable Long id) {
        return R.ok(scoreRuleService.listItems(id));
    }

    /**
     * 批量保存方案明细（先按 rule_id 物理删除再批量插入，并回写 rank_count）。
     */
    @PutMapping("/{id}/items")
    @PreAuthorize("@perm.has('rule:score:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "保存名次分明细", targetType = "score_rule")
    public R<Void> saveItems(@PathVariable Long id,
                             @Valid @RequestBody List<ScoreRuleItemDTO> items) {
        scoreRuleService.saveItems(id, items);
        return R.ok();
    }
}
