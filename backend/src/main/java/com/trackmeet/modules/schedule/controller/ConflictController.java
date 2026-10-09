package com.trackmeet.modules.schedule.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.schedule.dto.ConflictQuery;
import com.trackmeet.modules.schedule.dto.ConflictResolveDTO;
import com.trackmeet.modules.schedule.service.ConflictsService;
import com.trackmeet.modules.schedule.vo.ConflictVO;
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

import java.util.List;

/**
 * 冲突检测接口：检测、分页清单与处理。
 * <p>
 * 接口与权限编码对照：检测 {@code schedule:conflict:detect}、清单 {@code schedule:conflict:list}、
 * 处理 {@code schedule:conflict:resolve}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/schedule/conflicts")
@RequiredArgsConstructor
@Validated
public class ConflictController {

    private final ConflictsService conflictsService;

    /**
     * 执行冲突检测（body：{@code {meetId, sessionId, eventId}}）。
     * <p>
     * 先清理该范围内 {@code resolved=0} 的旧记录，再插入新记录，并回填
     * {@code schedule.conflict_flag / conflict_count}。
     */
    @PostMapping("/detect")
    @PreAuthorize("@perm.has('schedule:conflict:detect')")
    @OpLog(module = "SCHEDULE", action = "DETECT", actionName = "执行冲突检测", targetType = "conflict")
    public R<List<ConflictVO>> detect(@RequestBody ConflictQuery query) {
        return R.ok(conflictsService.detect(query));
    }

    /**
     * 冲突分页清单（conflictType / level / resolved / sessionId / eventId / scheduleId）。
     */
    @GetMapping("")
    @PreAuthorize("@perm.has('schedule:conflict:list')")
    public R<PageResult<ConflictVO>> page(ConflictQuery query) {
        return R.ok(conflictsService.pageConflict(query));
    }

    /**
     * 标记冲突已处理（body：{@code {resolveRemark}}）。
     */
    @PutMapping("/{id}/resolve")
    @PreAuthorize("@perm.has('schedule:conflict:resolve')")
    @OpLog(module = "SCHEDULE", action = "RESOLVE", actionName = "处理冲突", targetType = "conflict")
    public R<Void> resolve(@PathVariable Long id, @RequestBody ConflictResolveDTO dto) {
        conflictsService.resolve(id, dto);
        return R.ok();
    }
}
