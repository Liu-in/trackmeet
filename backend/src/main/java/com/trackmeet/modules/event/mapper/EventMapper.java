package com.trackmeet.modules.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.event.entity.Event;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目 Mapper：单表 CRUD 由 MyBatis-Plus 提供，另含跨表的引用存在性校验与规则级联清理。
 * <p>
 * 说明：本文件所有自定义 SQL 都<b>不</b>经过多租户拦截器，因此全部自带
 * {@code meet_id} 与 {@code deleted} 条件，避免跨届次误判。
 *
 * @author TrackMeet
 */
@Mapper
public interface EventMapper extends BaseMapper<Event> {

    /**
     * 统计项目下的有效报名数（不含已取消报名），用于删除前引用校验。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 有效报名条数
     */
    @Select("SELECT COUNT(*) FROM registration WHERE meet_id = #{meetId} AND event_id = #{eventId} "
            + "AND status <> 'CANCELLED' AND deleted = 0")
    long countActiveRegistration(@Param("meetId") Long meetId, @Param("eventId") Long eventId);

    /**
     * 统计项目被日程引用条数，用于删除前引用校验。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 日程条目数
     */
    @Select("SELECT COUNT(*) FROM schedule WHERE meet_id = #{meetId} AND event_id = #{eventId} AND deleted = 0")
    long countScheduleReference(@Param("meetId") Long meetId, @Param("eventId") Long eventId);

    /**
     * 物理删除项目级规则行：用于「恢复继承」（{@code POST /api/events/{id}/rule-config/reset}）。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM event_rule_config WHERE meet_id = #{meetId} AND event_id = #{eventId}")
    int deleteRuleConfigPhysically(@Param("meetId") Long meetId, @Param("eventId") Long eventId);
}
