package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.EventRound;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 轮次 Mapper：单表 CRUD 由 MyBatis-Plus 提供，另含下级轮次引用校验与轮次级规则级联清理。
 *
 * @author TrackMeet
 */
@Mapper
public interface EventRoundMapper extends BaseMapper<EventRound> {

    /**
     * 统计以下级身份引用本轮的轮次数（{@code event_round_rule.source_round_id}）。
     *
     * @param meetId 届次 ID
     * @param roundId 轮次 ID
     * @return 引用条数
     */
    @Select("SELECT COUNT(*) FROM event_round_rule WHERE meet_id = #{meetId} AND source_round_id = #{roundId} "
            + "AND deleted = 0")
    long countSourceRoundReference(@Param("meetId") Long meetId, @Param("roundId") Long roundId);

    /**
     * 统计项目下未删除的轮次数，用于删除项目时的完整性判断。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 轮次数
     */
    @Select("SELECT COUNT(*) FROM event_round WHERE meet_id = #{meetId} AND event_id = #{eventId} AND deleted = 0")
    long countByEvent(@Param("meetId") Long meetId, @Param("eventId") Long eventId);

    /**
     * 按项目物理删除轮次级规则行，用于删除项目时的规则级联清理。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM event_round_rule WHERE meet_id = #{meetId} AND event_id = #{eventId}")
    int deleteRoundRuleByEvent(@Param("meetId") Long meetId, @Param("eventId") Long eventId);

    /**
     * 按轮次物理删除轮次级规则行。
     *
     * @param meetId  届次 ID
     * @param roundId 轮次 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM event_round_rule WHERE meet_id = #{meetId} AND round_id = #{roundId}")
    int deleteRoundRuleByRound(@Param("meetId") Long meetId, @Param("roundId") Long roundId);
}
