package com.trackmeet.modules.meet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.meet.entity.MeetGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组别 Mapper：单表 CRUD 由 MyBatis-Plus 提供。
 *
 * @author TrackMeet
 */
@Mapper
public interface MeetGroupMapper extends BaseMapper<MeetGroup> {
}
