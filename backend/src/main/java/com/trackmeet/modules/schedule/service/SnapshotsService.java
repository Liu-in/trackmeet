package com.trackmeet.modules.schedule.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.schedule.dto.SnapshotSaveDTO;
import com.trackmeet.modules.schedule.entity.ScheduleSnapshot;

/**
 * 编排快照服务：把当届「日程 + 分组 + 组内人员 + 道次 + 出场顺序」序列化为快照，并支持回滚。
 *
 * @author TrackMeet
 */
public interface SnapshotsService extends IService<ScheduleSnapshot> {

    /**
     * 保存编排快照。
     *
     * @param dto 入参（meetId / snapshotName / snapshotType / scopeType / batchNo / remark）
     * @return 快照主键
     */
    Long saveSnapshot(SnapshotSaveDTO dto);

    /**
     * 回滚到指定快照：清理当届未锁定编排数据后按快照重建，锁定数据保持不动。
     *
     * @param id 快照 ID
     */
    void restore(Long id);
}
