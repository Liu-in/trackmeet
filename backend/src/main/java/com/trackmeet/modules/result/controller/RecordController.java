package com.trackmeet.modules.result.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.result.dto.RecordConfirmDTO;
import com.trackmeet.modules.result.dto.RecordQuery;
import com.trackmeet.modules.result.dto.RecordSaveDTO;
import com.trackmeet.modules.result.service.RecordService;
import com.trackmeet.modules.result.vo.RecordVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 纪录接口：纪录库维护（{@code /api/records}）与破纪录认定（{@code /api/results/record/confirm}）。
 * <p>
 * 破纪录认定回写 {@code result.is_record_confirmed} / {@code record_id} / {@code record_type}；
 * 超风速成绩（{@code is_wind_legal = 0}）不允许认定，抛 {@code WIND_ILLEGAL}
 * （风速超限，纪录不予认定），但保留 {@code is_record_candidate} 候选位。
 * <p>
 * 权限编码：{@code record:list} / {@code record:add} / {@code record:edit} / {@code record:delete}
 * / {@code result:record}（认定）。
 *
 * @author TrackMeet
 */
@RestController
@RequiredArgsConstructor
@Validated
public class RecordController {

    private final RecordService recordService;

    /**
     * 破纪录认定 / 取消认定。
     */
    @PostMapping("/api/results/record/confirm")
    @PreAuthorize("@perm.has('result:record')")
    @OpLog(module = "RESULT", action = "RECORD", actionName = "破纪录认定", targetType = "record")
    public R<Void> confirm(@Valid @RequestBody RecordConfirmDTO dto) {
        recordService.confirmRecord(dto);
        return R.ok();
    }

    /**
     * 破纪录候选扫描：与现行纪录对标并回写候选标记。
     *
     * @param eventId 项目 ID
     * @param roundId 轮次 ID（可空）
     * @return 命中的候选条数
     */
    @PostMapping("/api/results/record/scan")
    @PreAuthorize("@perm.has('result:record')")
    @OpLog(module = "RESULT", action = "RECORD", actionName = "扫描破纪录候选", targetType = "record")
    public R<Integer> scan(@RequestParam("eventId") Long eventId,
                           @RequestParam(value = "roundId", required = false) Long roundId) {
        return R.ok(recordService.scanRecordCandidates(eventId, roundId));
    }

    /**
     * 破纪录候选查询（与现行纪录对标的成绩列表）。
     */
    @GetMapping("/api/results/record/candidates")
    @PreAuthorize("@perm.has('result:list')")
    public R<List<com.trackmeet.modules.result.vo.ResultVO>> candidates(
            @RequestParam("eventId") Long eventId,
            @RequestParam(value = "roundId", required = false) Long roundId) {
        return R.ok(recordService.listRecordCandidates(eventId, roundId));
    }

    /**
     * 纪录分页列表（条件：eventId / groupId / recordType / gender）。
     */
    @GetMapping("/api/records")
    @PreAuthorize("@perm.has('record:list')")
    public R<PageResult<RecordVO>> page(@Valid RecordQuery query) {
        return R.ok(recordService.pageRecord(query));
    }

    /**
     * 新增纪录。
     */
    @PostMapping("/api/records")
    @PreAuthorize("@perm.has('record:add')")
    @OpLog(module = "RESULT", action = "RECORD", actionName = "新增纪录", targetType = "record")
    public R<Long> add(@Valid @RequestBody RecordSaveDTO dto) {
        return R.ok(recordService.createRecord(dto));
    }

    /**
     * 修改纪录。
     */
    @PutMapping("/api/records/{id}")
    @PreAuthorize("@perm.has('record:edit')")
    @OpLog(module = "RESULT", action = "RECORD", actionName = "修改纪录", targetType = "record")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody RecordSaveDTO dto) {
        recordService.updateRecord(id, dto);
        return R.ok();
    }

    /**
     * 删除纪录（被成绩引用时拒绝删除）。
     */
    @DeleteMapping("/api/records/{id}")
    @PreAuthorize("@perm.has('record:delete')")
    @OpLog(module = "RESULT", action = "RECORD", actionName = "删除纪录", targetType = "record")
    public R<Void> remove(@PathVariable("id") Long id) {
        recordService.removeRecord(id);
        return R.ok();
    }
}
