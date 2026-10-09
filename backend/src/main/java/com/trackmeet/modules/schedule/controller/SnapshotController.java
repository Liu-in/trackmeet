package com.trackmeet.modules.schedule.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.schedule.dto.SnapshotSaveDTO;
import com.trackmeet.modules.schedule.service.SnapshotsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编排快照接口：保存与回滚。
 * <p>
 * 接口与权限编码对照：保存与回滚均使用 {@code schedule:snapshot}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/schedule/snapshot")
@RequiredArgsConstructor
@Validated
public class SnapshotController {

    private final SnapshotsService snapshotsService;

    /**
     * 保存快照（body：{@code {meetId, snapshotName}}）。
     * <p>
     * 把当届 {@code schedule + heat + heat_member + lane + field_order} 序列化为 JSON
     * 存 {@code schedule_snapshot.snapshot_json}。
     */
    @PostMapping("")
    @PreAuthorize("@perm.has('schedule:snapshot')")
    @OpLog(module = "SCHEDULE", action = "SNAPSHOT", actionName = "保存编排快照", targetType = "snapshot")
    public R<Long> save(@Valid @RequestBody SnapshotSaveDTO dto) {
        return R.ok(snapshotsService.saveSnapshot(dto));
    }

    /**
     * 回滚到指定快照（未锁定编排数据按快照重建，锁定数据保持不动）。
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("@perm.has('schedule:snapshot')")
    @OpLog(module = "SCHEDULE", action = "RESTORE", actionName = "回滚编排快照", targetType = "snapshot")
    public R<Void> restore(@PathVariable Long id) {
        snapshotsService.restore(id);
        return R.ok();
    }
}
