package com.trackmeet.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.system.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 *
 * @author TrackMeet
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
