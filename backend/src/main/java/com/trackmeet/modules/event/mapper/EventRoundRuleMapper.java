package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.EventRoundRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 轮次级规则 Mapper：{@code round_id} 上唯一（{@code uk_roundrule_round}），保存时先查后插 / 更新。
 *
 * @author TrackMeet
 */
@Mapper
public interface EventRoundRuleMapper extends BaseMapper<EventRoundRule> {
}
