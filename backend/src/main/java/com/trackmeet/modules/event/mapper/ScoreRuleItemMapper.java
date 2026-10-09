package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.ScoreRuleItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 名次分明细 Mapper：{@code (rule_id, rank_no)} 上唯一（{@code uk_scoreitem_rule_rank}）。
 * <p>
 * 明细批量保存采用「先按 rule_id 物理删除再批量插入」，避免唯一键与逻辑删除行冲突。
 *
 * @author TrackMeet
 */
@Mapper
public interface ScoreRuleItemMapper extends BaseMapper<ScoreRuleItem> {

    /**
     * 物理删除方案下的全部明细行。
     *
     * @param meetId 届次 ID
     * @param ruleId 方案 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM score_rule_item WHERE meet_id = #{meetId} AND rule_id = #{ruleId}")
    int deleteByRuleId(@Param("meetId") Long meetId, @Param("ruleId") Long ruleId);
}
