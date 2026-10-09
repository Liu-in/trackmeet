package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.ScoreRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 名次分方案 Mapper：{@code rule_code} 在届次内唯一（{@code uk_scorerule_meet_code}）。
 *
 * @author TrackMeet
 */
@Mapper
public interface ScoreRuleMapper extends BaseMapper<ScoreRule> {

    /**
     * 统计引用该方案的届次默认规则条数（{@code meet_rule_config.score_rule_id}），用于删除前引用校验。
     *
     * @param meetId      届次 ID
     * @param scoreRuleId 名次分方案 ID
     * @return 引用条数
     */
    @Select("SELECT COUNT(*) FROM meet_rule_config WHERE meet_id = #{meetId} AND score_rule_id = #{scoreRuleId} "
            + "AND deleted = 0")
    long countMeetRuleReference(@Param("meetId") Long meetId, @Param("scoreRuleId") Long scoreRuleId);

    /**
     * 统计项目绑定该方案的条数（{@code event.score_rule_id}），用于删除前引用校验。
     *
     * @param meetId      届次 ID
     * @param scoreRuleId 名次分方案 ID
     * @return 绑定项目数
     */
    @Select("SELECT COUNT(*) FROM event WHERE meet_id = #{meetId} AND score_rule_id = #{scoreRuleId} AND deleted = 0")
    long countEventReference(@Param("meetId") Long meetId, @Param("scoreRuleId") Long scoreRuleId);
}
