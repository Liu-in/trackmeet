package com.trackmeet.modules.meet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.meet.entity.Meet;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运动会（届次）Mapper：单表 CRUD 由 MyBatis-Plus 提供。
 *
 * @author TrackMeet
 */
@Mapper
public interface MeetMapper extends BaseMapper<Meet> {
}
