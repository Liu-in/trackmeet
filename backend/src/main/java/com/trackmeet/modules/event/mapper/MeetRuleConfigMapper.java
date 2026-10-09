package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 届次级默认规则 Mapper：{@code meet_id} 上唯一（{@code uk_meet_id}），与届次一对一。
 *
 * @author TrackMeet
 */
@Mapper
public interface MeetRuleConfigMapper extends BaseMapper<MeetRuleConfig> {
}
