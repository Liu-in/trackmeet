package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.ScheduleSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编排快照 Mapper：单表 CRUD 由 MyBatis-Plus 提供。
 *
 * @author TrackMeet
 */
@Mapper
public interface ScheduleSnapshotMapper extends BaseMapper<ScheduleSnapshot> {
}
