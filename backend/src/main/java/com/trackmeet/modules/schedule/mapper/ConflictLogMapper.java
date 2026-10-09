package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.ConflictLog;
import com.trackmeet.modules.schedule.vo.ConflictVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 冲突记录 Mapper：单表 CRUD 由 MyBatis-Plus 提供，列表投影显式带 meet_id 与 deleted 条件。
 *
 * @author TrackMeet
 */
@Mapper
public interface ConflictLogMapper extends BaseMapper<ConflictLog> {

    /**
     * 冲突清单（含运动员 / 主体项目 / 场地展示字段）。
     */
    @Select("""
            <script>
            SELECT c.id AS id, c.meet_id AS meet_id, c.conflict_type AS conflict_type, c.level AS level,
                   c.schedule_id AS schedule_id, c.related_schedule_id AS related_schedule_id,
                   c.heat_id AS heat_id, c.related_heat_id AS related_heat_id, c.athlete_id AS athlete_id,
                   c.registration_id AS registration_id, c.referee_id AS referee_id, c.venue_id AS venue_id,
                   c.session_id AS session_id, c.start_time AS start_time, c.end_time AS end_time,
                   c.related_start_time AS related_start_time, c.related_end_time AS related_end_time,
                   c.overlap_minutes AS overlap_minutes, c.interval_minutes AS interval_minutes,
                   c.required_interval_min AS required_interval_min, c.detail_json AS detail_json,
                   c.batch_no AS batch_no, c.detect_time AS detect_time, c.resolved AS resolved,
                   c.resolve_remark AS resolve_remark, c.resolve_by AS resolve_by, c.resolve_time AS resolve_time,
                   a.athlete_name AS athlete_name, e.event_name AS event_name, v.venue_name AS venue_name
            FROM conflict_log c
            LEFT JOIN athlete a ON a.id = c.athlete_id AND a.deleted = 0
            LEFT JOIN schedule s ON s.id = c.schedule_id AND s.deleted = 0
            LEFT JOIN event e ON e.id = s.event_id AND e.deleted = 0
            LEFT JOIN venue v ON v.id = c.venue_id AND v.deleted = 0
            WHERE c.meet_id = #{meetId} AND c.deleted = 0
            <if test="sessionId != null"> AND c.session_id = #{sessionId} </if>
            <if test="scheduleId != null"> AND (c.schedule_id = #{scheduleId} OR c.related_schedule_id = #{scheduleId}) </if>
            <if test="eventId != null">
              AND (c.schedule_id IN (SELECT s2.id FROM schedule s2 WHERE s2.deleted = 0 AND s2.event_id = #{eventId})
                   OR c.related_schedule_id IN (SELECT s3.id FROM schedule s3 WHERE s3.deleted = 0 AND s3.event_id = #{eventId}))
            </if>
            <if test="conflictType != null and conflictType != ''"> AND c.conflict_type = #{conflictType} </if>
            <if test="level != null and level != ''"> AND c.level = #{level} </if>
            <if test="resolved != null"> AND c.resolved = #{resolved} </if>
            ORDER BY c.resolved ASC, c.detect_time DESC, c.id DESC
            </script>
            """)
    List<ConflictVO> selectConflictVOs(@Param("meetId") Long meetId,
                                       @Param("sessionId") Long sessionId,
                                       @Param("scheduleId") Long scheduleId,
                                       @Param("eventId") Long eventId,
                                       @Param("conflictType") String conflictType,
                                       @Param("level") String level,
                                       @Param("resolved") Integer resolved);

    /**
     * 指定日程集合的冲突清单（用于冲突标记回填）。
     */
    @Select("""
            <script>
            SELECT c.id AS id, c.meet_id AS meet_id, c.conflict_type AS conflict_type, c.level AS level,
                   c.schedule_id AS schedule_id, c.related_schedule_id AS related_schedule_id,
                   c.overlap_minutes AS overlap_minutes, c.interval_minutes AS interval_minutes,
                   c.required_interval_min AS required_interval_min, c.detail_json AS detail_json,
                   c.batch_no AS batch_no, c.detect_time AS detect_time, c.resolved AS resolved
            FROM conflict_log c
            WHERE c.meet_id = #{meetId} AND c.deleted = 0 AND c.resolved = 0
              AND (c.schedule_id IN <foreach collection="scheduleIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
                   OR c.related_schedule_id IN <foreach collection="scheduleIds" item="sid2" open="(" separator="," close=")">#{sid2}</foreach>)
            </script>
            """)
    List<ConflictVO> selectUnresolvedByScheduleIds(@Param("meetId") Long meetId,
                                                   @Param("scheduleIds") Collection<Long> scheduleIds);
}
