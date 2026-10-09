package com.trackmeet.modules.system.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.system.dto.OperationLogQuery;
import com.trackmeet.modules.system.entity.SysOperationLog;
import com.trackmeet.modules.system.service.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/system/logs/operation")
@RequiredArgsConstructor
@Validated
public class SysOperationLogController {

    private final SysOperationLogService sysOperationLogService;

    /**
     * 操作日志分页列表（按 operate_time 倒序）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('system:log:operation')")
    public R<PageResult<SysOperationLog>> page(OperationLogQuery query) {
        return R.ok(sysOperationLogService.pageLogs(query));
    }

    /**
     * 操作日志详情（含 before/after 快照）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('system:log:operation')")
    public R<SysOperationLog> detail(@PathVariable("id") Long id) {
        return R.ok(sysOperationLogService.getLog(id));
    }

    /**
     * 删除单条操作日志。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('system:log:operation')")
    @OpLog(module = "SYSTEM", action = "DELETE", actionName = "删除操作日志", targetType = "operationLog")
    public R<Void> delete(@PathVariable("id") Long id) {
        sysOperationLogService.deleteLog(id);
        return R.ok();
    }
}
