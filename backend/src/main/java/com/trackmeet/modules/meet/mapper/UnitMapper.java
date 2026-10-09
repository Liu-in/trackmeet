package com.trackmeet.modules.meet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.meet.entity.Unit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 参赛单位 Mapper：单表 CRUD 由 MyBatis-Plus 提供。
 *
 * @author TrackMeet
 */
@Mapper
public interface UnitMapper extends BaseMapper<Unit> {
}
