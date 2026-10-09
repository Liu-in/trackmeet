package com.trackmeet.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.system.dto.OperationLogQuery;
import com.trackmeet.modules.system.entity.SysOperationLog;

/**
 * 操作日志服务。
 *
 * @author TrackMeet
 */
public interface SysOperationLogService extends IService<SysOperationLog> {

    /**
     * 操作日志分页列表（按 operate_time 倒序）。
     */
    PageResult<SysOperationLog> pageLogs(OperationLogQuery query);

    /**
     * 操作日志详情（含 before/after 快照）。
     */
    SysOperationLog getLog(Long id);

    /**
     * 删除单条操作日志（逻辑删除）。
     */
    void deleteLog(Long id);
}
