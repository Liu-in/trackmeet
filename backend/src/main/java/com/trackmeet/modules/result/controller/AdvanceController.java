package com.trackmeet.modules.result.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.result.dto.AdvanceGenerateDTO;
import com.trackmeet.modules.result.dto.AdvanceUpdateDTO;
import com.trackmeet.modules.result.service.AdvanceService;
import com.trackmeet.modules.result.vo.AdvanceRowVO;
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
 * 晋级名单接口：自动晋级、查询、人工调整与确认。
 * <p>
 * 自动晋级调用成绩引擎 {@code com.trackmeet.engine.advance.AdvanceCalculator#calculate}：
 * 「每组前 N 名（按组次分组）+ 其余最好成绩递补至目标人数」；
 * 生成采用先物理删除后插入的幂等重建，{@code advance_type} 为 {@code GROUP_TOP} / {@code BEST_LOSER}，
 * 人工调整后置 {@code MANUAL}，{@code is_confirmed = 0} 待确认生效。
 * <p>
 * 权限编码：查询 {@code result:list}，生成与确认 {@code result:advance}，人工调整 {@code result:advance:edit}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/results/advance")
@RequiredArgsConstructor
@Validated
public class AdvanceController {

    private final AdvanceService advanceService;

    /**
     * 自动晋级：按来源轮次成绩生成目标轮次名单（幂等重建）。
     * <p>
     * 缺省值取 {@code event_round_rule} 的 {@code group_top_n} / {@code best_loser_count} /
     * {@code target_count} / {@code exclude_illegal_wind}。
     */
    @PostMapping("/generate")
    @PreAuthorize("@perm.has('result:advance')")
    @OpLog(module = "RESULT", action = "ADVANCE", actionName = "自动生成晋级名单", targetType = "advance")
    public R<List<AdvanceRowVO>> generate(@Valid @RequestBody AdvanceGenerateDTO dto) {
        return R.ok(advanceService.generateAdvance(dto));
    }

    /**
     * 晋级名单查询（按项目 / 目标轮次 / 来源轮次过滤，三者至少给一个）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('result:list')")
    public R<List<AdvanceRowVO>> list(@RequestParam(value = "eventId", required = false) Long eventId,
                                      @RequestParam(value = "toRoundId", required = false) Long toRoundId,
                                      @RequestParam(value = "fromRoundId", required = false) Long fromRoundId) {
        return R.ok(advanceService.listAdvance(eventId, toRoundId, fromRoundId));
    }

    /**
     * 手动调整晋级名单：置 {@code advance_type = MANUAL} 并重排顺序号。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('result:advance:edit')")
    @OpLog(module = "RESULT", action = "ADVANCE", actionName = "手动调整晋级名单", targetType = "advance")
    public R<AdvanceRowVO> update(@PathVariable("id") Long id, @Valid @RequestBody AdvanceUpdateDTO dto) {
        return R.ok(advanceService.updateAdvance(id, dto));
    }

    /**
     * 确认晋级名单生效（按项目 × 目标轮次批量置 {@code is_confirmed = 1}）。
     */
    @PutMapping("/confirm")
    @PreAuthorize("@perm.has('result:advance')")
    @OpLog(module = "RESULT", action = "ADVANCE", actionName = "确认晋级名单", targetType = "advance")
    public R<List<AdvanceRowVO>> confirm(@RequestBody Map<String, Long> body) {
        if (body == null) {
            return R.fail(com.trackmeet.common.result.ResultCode.PARAM_ERROR, "项目与目标轮次不能为空");
        }
        return R.ok(advanceService.confirmAdvance(body.get("eventId"), body.get("toRoundId")));
    }
}
