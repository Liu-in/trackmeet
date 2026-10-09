package com.trackmeet.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.system.dto.OperationLogQuery;
import com.trackmeet.modules.system.entity.SysOperationLog;
import com.trackmeet.modules.system.mapper.SysOperationLogMapper;
import com.trackmeet.modules.system.service.SysOperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 操作日志服务实现：只读审计 + 单条删除。
 *
 * @author TrackMeet
 */
@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog>
        implements SysOperationLogService {

    @Override
    public PageResult<SysOperationLog> pageLogs(OperationLogQuery query) {
        OperationLogQuery condition = query == null ? new OperationLogQuery() : query;
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(condition.getKeyword()), SysOperationLog::getUsername, condition.getKeyword())
                .eq(StringUtils.hasText(condition.getModule()), SysOperationLog::getModule, condition.getModule())
                .eq(StringUtils.hasText(condition.getAction()), SysOperationLog::getAction, condition.getAction())
                .eq(StringUtils.hasText(condition.getResult()), SysOperationLog::getResult, condition.getResult())
                .ge(condition.getBeginTime() != null, SysOperationLog::getOperateTime, condition.getBeginTime())
                .le(condition.getEndTime() != null, SysOperationLog::getOperateTime, condition.getEndTime())
                .orderByDesc(SysOperationLog::getOperateTime)
                .orderByDesc(SysOperationLog::getId);

        Page<SysOperationLog> pageParam = condition.toPage();
        return PageResult.of(page(pageParam, wrapper));
    }

    @Override
    public SysOperationLog getLog(Long id) {
        SysOperationLog log = getById(id);
        if (log == null) {
            throw new BizException(ResultCode.NOT_FOUND, "操作日志不存在");
        }
        return log;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLog(Long id) {
        SysOperationLog log = getById(id);
        if (log == null) {
            throw new BizException(ResultCode.NOT_FOUND, "操作日志不存在");
        }
        removeById(id);
    }
}
