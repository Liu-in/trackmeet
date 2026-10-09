package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.ScheduleSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 竞赛单元 Mapper：单表 CRUD 由 MyBatis-Plus 提供，查询自动带 meet_id（多租户拦截器）与 deleted。
 *
 * @author TrackMeet
 */
@Mapper
public interface ScheduleSessionMapper extends BaseMapper<ScheduleSession> {
}
