package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.EventRuleConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 项目级规则 Mapper：{@code event_id} 上唯一（{@code uk_evrule_event}），与项目一对一。
 *
 * @author TrackMeet
 */
@Mapper
public interface EventRuleConfigMapper extends BaseMapper<EventRuleConfig> {

    /**
     * 物理删除指定项目的项目级规则行（「恢复继承」用；逻辑删除会受逻辑删除列影响，故用物理删除）。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM event_rule_config WHERE meet_id = #{meetId} AND event_id = #{eventId}")
    int deleteByEventId(@Param("meetId") Long meetId, @Param("eventId") Long eventId);
}
