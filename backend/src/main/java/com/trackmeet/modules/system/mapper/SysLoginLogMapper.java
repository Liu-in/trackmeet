package com.trackmeet.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。
 *
 * @author TrackMeet
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
