package com.trackmeet.modules.result.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.result.dto.ResultApproveDTO;
import com.trackmeet.modules.result.dto.ResultAttemptDTO;
import com.trackmeet.modules.result.dto.ResultBatchSaveDTO;
import com.trackmeet.modules.result.dto.ResultQuery;
import com.trackmeet.modules.result.dto.ResultRejectDTO;
import com.trackmeet.modules.result.dto.ResultSaveDTO;
import com.trackmeet.modules.result.dto.ResultStatusDTO;
import com.trackmeet.modules.result.dto.ResultWindDTO;
import com.trackmeet.modules.result.service.ResultService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 成绩录入与审核接口。
 * <p>
 * 权限编码（均已在初始化数据中定义）：
 * <ul>
 *     <li>{@code result:list} 成绩分页 / 录入表 / 留痕查询</li>
 *     <li>{@code result:add} 录入、批量录入、设备导入</li>
 *     <li>{@code result:edit} 修改成绩</li>
 *     <li>{@code result:attempt:add} 田赛试跳试掷新增与修改</li>
 *     <li>{@code result:status} 特殊状态标记</li>
 *     <li>{@code result:wind} 风速录入</li>
 *     <li>{@code result:submit} 提交审核</li>
 *     <li>{@code result:approve} 审核通过</li>
 *     <li>{@code result:reject} 审核驳回</li>
 *     <li>{@code result:revoke} 撤销审核</li>
 *     <li>{@code result:audit:list} 成绩留痕</li>
 * </ul>
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
@Validated
public class ResultController {

    private final ResultService resultService;

    /**
     * 成绩分页列表。
     * <p>
     * 条件：eventId、roundId、heatId、unitId、auditStatus、status、keyword（成绩原文 / 号码 / 姓名）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('result:list')")
    public R<PageResult<com.trackmeet.modules.result.vo.ResultVO>> page(@Valid ResultQuery query) {
        return R.ok(resultService.pageResult(query));
    }

    /**
     * 成绩录入表：按组次、道次（田赛按出场顺序）排列，含号码、姓名、单位、种子成绩、已有成绩、状态。
     * <p>
     * 参数二选一：{@code scheduleId}，或 {@code eventId} + {@code roundId}。
     */
    @GetMapping("/entry-sheet")
    @PreAuthorize("@perm.has('result:list')")
    public R<List<com.trackmeet.modules.result.vo.EntrySheetRowVO>> entrySheet(
            @RequestParam(value = "scheduleId", required = false) Long scheduleId,
            @RequestParam(value = "eventId", required = false) Long eventId,
            @RequestParam(value = "roundId", required = false) Long roundId) {
        return R.ok(resultService.entrySheet(scheduleId, eventId, roundId));
    }

    /**
     * 录入成绩（径赛 / 田赛 / 全能，可同批带试跳明细）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('result:add')")
    @OpLog(module = "RESULT", action = "ADD", actionName = "录入成绩", targetType = "result")
    public R<Long> add(@Valid @RequestBody ResultSaveDTO dto) {
        return R.ok(resultService.createResult(dto));
    }

    /**
     * 批量录入（一组一次提交）。
     */
    @PostMapping("/batch")
    @PreAuthorize("@perm.has('result:add')")
    @OpLog(module = "RESULT", action = "ADD", actionName = "批量录入成绩", targetType = "result")
    public R<List<Long>> batch(@Valid @RequestBody ResultBatchSaveDTO dto) {
        return R.ok(resultService.batchSaveResult(dto));
    }

    /**
     * 修改成绩（已审核锁定的成绩抛 {@code RESULT_LOCKED}，须先撤销审核）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('result:edit')")
    @OpLog(module = "RESULT", action = "EDIT", actionName = "修改成绩", targetType = "result")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody ResultSaveDTO dto) {
        resultService.updateResult(id, dto);
        return R.ok();
    }

    /**
     * 田赛单次试跳 / 试掷录入。
     */
    @PostMapping("/attempt")
    @PreAuthorize("@perm.has('result:attempt:add')")
    @OpLog(module = "RESULT", action = "EDIT", actionName = "录入试跳试掷", targetType = "result")
    public R<Long> addAttempt(@Valid @RequestBody ResultAttemptDTO dto) {
        return R.ok(resultService.saveAttempt(dto));
    }

    /**
     * 田赛单次试跳 / 试掷修改。
     */
    @PutMapping("/attempt/{id}")
    @PreAuthorize("@perm.has('result:attempt:add')")
    @OpLog(module = "RESULT", action = "EDIT", actionName = "修改试跳试掷", targetType = "result")
    public R<Long> editAttempt(@PathVariable("id") Long id, @Valid @RequestBody ResultAttemptDTO dto) {
        return R.ok(resultService.updateAttempt(id, dto));
    }

    /**
     * 批量标记特殊状态：DNS / DNF / DSQ / NM / PASS / SCRATCH（DSQ 必须填犯规类型）。
     */
    @PostMapping("/status")
    @PreAuthorize("@perm.has('result:status')")
    @OpLog(module = "RESULT", action = "EDIT", actionName = "标记成绩特殊状态", targetType = "result")
    public R<Integer> status(@Valid @RequestBody ResultStatusDTO dto) {
        return R.ok(resultService.updateStatus(dto));
    }

    /**
     * 风速录入（组级或单人）：写风速读数并回填成绩的风速与合法性。
     */
    @PostMapping("/wind")
    @PreAuthorize("@perm.has('result:wind')")
    @OpLog(module = "RESULT", action = "WIND", actionName = "录入风速", targetType = "result")
    public R<Integer> wind(@Valid @RequestBody ResultWindDTO dto) {
        return R.ok(resultService.updateWind(dto));
    }

    /**
     * 提交审核（DRAFT / REJECTED → SUBMITTED）。
     */
    @PostMapping("/submit")
    @PreAuthorize("@perm.has('result:submit')")
    @OpLog(module = "RESULT", action = "EDIT", actionName = "提交成绩审核", targetType = "result")
    public R<Integer> submit(@RequestBody Map<String, List<Long>> body) {
        return R.ok(resultService.submit(body == null ? null : body.get("ids")));
    }

    /**
     * 成绩审核通过（批量）：{@code audit_status = APPROVED}、{@code is_locked = 1}，并自动生成名次。
     */
    @PostMapping("/approve")
    @PreAuthorize("@perm.has('result:approve')")
    @OpLog(module = "RESULT", action = "APPROVE", actionName = "成绩审核通过", targetType = "result")
    public R<Integer> approve(@Valid @RequestBody ResultApproveDTO dto) {
        return R.ok(resultService.approve(dto));
    }

    /**
     * 审核驳回（附原因，写入成绩与留痕）。
     */
    @PostMapping("/reject")
    @PreAuthorize("@perm.has('result:reject')")
    @OpLog(module = "RESULT", action = "REJECT", actionName = "成绩审核驳回", targetType = "result")
    public R<Integer> reject(@Valid @RequestBody ResultRejectDTO dto) {
        return R.ok(resultService.reject(dto));
    }

    /**
     * 撤销审核（回到 SUBMITTED、解锁，并清理本轮名次）。
     */
    @PostMapping("/{id}/revoke-approval")
    @PreAuthorize("@perm.has('result:revoke')")
    @OpLog(module = "RESULT", action = "REVOKE", actionName = "撤销成绩审核", targetType = "result")
    public R<Void> revokeApproval(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> body) {
        resultService.revokeApproval(id, body == null ? null : body.get("reason"));
        return R.ok();
    }

    /**
     * 成绩修改留痕（谁 / 何时 / 改了什么）。
     */
    @GetMapping("/{id}/audits")
    @PreAuthorize("@perm.has('result:audit:list')")
    public R<List<com.trackmeet.modules.result.vo.ResultAuditVO>> audits(@PathVariable("id") Long id) {
        return R.ok(resultService.listAudits(id));
    }

    /**
     * 设备成绩文件导入（CSV / Excel）。
     * <p>
     * 当前为接口占位：调用时返回 {@code IMPORT_FORMAT_ERROR} 并提示使用录入接口，
     * 导入批次留档与设备文件映射将在后续迭代补齐。
     */
    @PostMapping("/import")
    @PreAuthorize("@perm.has('result:add')")
    @OpLog(module = "RESULT", action = "ADD", actionName = "导入设备成绩", targetType = "result")
    public R<Integer> importResults(@RequestParam("scheduleId") Long scheduleId,
                                   @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return R.ok(resultService.importResults(scheduleId, overwrite));
    }
}
