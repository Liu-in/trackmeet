package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.BonusRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 加分规则 Mapper：{@code rule_code} 在届次内唯一（{@code uk_bonus_meet_code}）。
 *
 * @author TrackMeet
 */
@Mapper
public interface BonusRuleMapper extends BaseMapper<BonusRule> {
}
