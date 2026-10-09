package com.trackmeet.modules.athlete.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.athlete.entity.Athlete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运动员 Mapper：本阶段仅需 MyBatis-Plus 通用 CRUD，无自定义 SQL。
 *
 * @author TrackMeet
 */
@Mapper
public interface AthleteMapper extends BaseMapper<Athlete> {
}
