package com.trackmeet.modules.result.controller;

import com.trackmeet.common.result.R;
import com.trackmeet.modules.result.service.ResultService;
import com.trackmeet.modules.result.vo.ResultAuditVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 成绩审核留痕查询接口（只读）。
 * <p>
 * 留痕由成绩写操作自动产生（见 {@code ResultService} 与 {@code ResultAuditService}）：
 * 动作码为 {@code SUBMIT} / {@code APPROVE} / {@code REJECT} / {@code MODIFY} / {@code REVOKE}，
 * 每条记录带 {@code before_json} / {@code after_json} 快照（含各次试跳）、原因、操作人与操作时间。
 * <p>
 * 权限编码：{@code result:audit:list}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/results/audit")
@RequiredArgsConstructor
@Validated
public class ResultAuditController {

    private final ResultService resultService;

    /**
     * 查询某条成绩的留痕链（操作时间倒序，最新在前）。
     *
     * @param resultId 成绩 ID
     * @return 留痕视图列表
     */
    @GetMapping("/{resultId}")
    @PreAuthorize("@perm.has('result:audit:list')")
    public R<List<ResultAuditVO>> listByResult(@PathVariable("resultId") Long resultId) {
        return R.ok(resultService.listAudits(resultId));
    }

    /**
     * 按成绩 ID 查询留痕（等价于 {@code GET /api/results/audit/{resultId}}，便于前端以查询参数调用）。
     *
     * @param resultId 成绩 ID
     * @return 留痕视图列表
     */
    @GetMapping
    @PreAuthorize("@perm.has('result:audit:list')")
    public R<List<ResultAuditVO>> list(@RequestParam("resultId") Long resultId) {
        return R.ok(resultService.listAudits(resultId));
    }
}
