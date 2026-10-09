package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.vo.EventAthleteRowVO;
import com.trackmeet.modules.schedule.vo.EventBriefRowVO;
import com.trackmeet.modules.schedule.vo.EventCountRowVO;
import com.trackmeet.modules.schedule.vo.EventRuleBriefVO;
import com.trackmeet.modules.schedule.vo.EventScheduleRuleVO;
import com.trackmeet.modules.schedule.vo.MeetRuleBriefVO;
import com.trackmeet.modules.schedule.vo.RefereeAssignRowVO;
import com.trackmeet.modules.schedule.vo.ScheduleTaskRowVO;
import com.trackmeet.modules.schedule.vo.VenueBriefVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 日程条目 Mapper。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；编排装配所需的跨模块取数（event / event_round / event_rule_config /
 * registration / relay_member / venue / meet_rule_config / event_schedule_rule / referee_assignment）
 * 一律使用投影 VO + 显式 SQL，自定义 SQL 自带 {@code meet_id} 与 {@code deleted} 条件，
 * 不引用其它模块的实体，避免跨模块耦合。
 *
 * @author TrackMeet
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 项目简要信息（按项目 / 组别过滤）。
     */
    @Select("""
            <script>
            SELECT e.id AS event_id, e.event_name AS event_name, e.event_type AS event_type,
                   e.sub_type AS sub_type, e.gender AS gender, e.is_relay AS is_relay,
                   e.group_id AS group_id, e.sort_no AS sort_no, e.duration_min AS duration_min,
                   e.status AS status
            FROM event e
            WHERE e.meet_id = #{meetId} AND e.deleted = 0
            <if test="eventIds != null and eventIds.size() > 0">
              AND e.id IN <foreach collection="eventIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
            </if>
            <if test="groupIds != null and groupIds.size() > 0">
              AND e.group_id IN <foreach collection="groupIds" item="gid" open="(" separator="," close=")">#{gid}</foreach>
            </if>
            ORDER BY e.sort_no ASC, e.id ASC
            </script>
            """)
    List<EventBriefRowVO> selectEventBriefs(@Param("meetId") Long meetId,
                                           @Param("eventIds") Collection<Long> eventIds,
                                           @Param("groupIds") Collection<Long> groupIds);

    /**
     * 项目 ID 列表（按组别过滤）。
     */
    @Select("""
            <script>
            SELECT e.id FROM event e
            WHERE e.meet_id = #{meetId} AND e.deleted = 0
              AND e.group_id IN <foreach collection="groupIds" item="gid" open="(" separator="," close=")">#{gid}</foreach>
            </script>
            """)
    List<Long> selectEventIdsByGroup(@Param("meetId") Long meetId, @Param("groupIds") Collection<Long> groupIds);

    /**
     * 自动编排任务装配行：项目 × 轮次（左连项目级规则），仅取启用项目。
     */
    @Select("""
            <script>
            SELECT e.id AS event_id, e.event_name AS event_name, e.event_type AS event_type,
                   e.sub_type AS sub_type, e.gender AS gender, e.is_relay AS is_relay,
                   e.group_id AS group_id, e.sort_no AS sort_no, e.duration_min AS event_duration_min,
                   rd.id AS round_id, rd.round_code AS round_code, rd.round_no AS round_no,
                   rd.is_final AS is_final, rd.plan_heat_count AS plan_heat_count,
                   rd.plan_heat_size AS plan_heat_size, rd.status AS round_status,
                   erc.lane_count AS rule_lane_count, erc.duration_min AS rule_duration_min,
                   erc.draw_group_size AS draw_group_size, erc.draw_mode AS draw_mode,
                   erc.draw_avoid_same_unit AS draw_avoid_same_unit, erc.attempt_count AS attempt_count,
                   erc.conflict_interval_min AS rule_conflict_interval_min
            FROM event e
            JOIN event_round rd ON rd.event_id = e.id AND rd.deleted = 0
            LEFT JOIN event_rule_config erc ON erc.event_id = e.id AND erc.deleted = 0
            WHERE e.meet_id = #{meetId} AND e.deleted = 0 AND e.status = 'ENABLED'
            <if test="eventIds != null and eventIds.size() > 0">
              AND e.id IN <foreach collection="eventIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
            </if>
            <if test="groupIds != null and groupIds.size() > 0">
              AND e.group_id IN <foreach collection="groupIds" item="gid" open="(" separator="," close=")">#{gid}</foreach>
            </if>
            ORDER BY e.sort_no ASC, e.id ASC, rd.round_no ASC, rd.id ASC
            </script>
            """)
    List<ScheduleTaskRowVO> selectTaskRows(@Param("meetId") Long meetId,
                                          @Param("eventIds") Collection<Long> eventIds,
                                          @Param("groupIds") Collection<Long> groupIds);

    /**
     * 项目有效报名人数汇总（status = APPROVED）。
     */
    @Select("""
            <script>
            SELECT rg.event_id AS event_id,
                   COUNT(*) AS approved_count,
                   SUM(CASE WHEN rg.round_scope = 'FINAL_ONLY' THEN 1 ELSE 0 END) AS final_only_count
            FROM registration rg
            WHERE rg.meet_id = #{meetId} AND rg.deleted = 0 AND rg.status = 'APPROVED'
            <if test="eventIds != null and eventIds.size() > 0">
              AND rg.event_id IN <foreach collection="eventIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
            </if>
            GROUP BY rg.event_id
            </script>
            """)
    List<EventCountRowVO> selectEventParticipantCounts(@Param("meetId") Long meetId,
                                                      @Param("eventIds") Collection<Long> eventIds);

    /**
     * 项目参赛运动员（接力按队员展开；个人项目取 registration.athlete_id）。
     */
    @Select("""
            <script>
            SELECT rg.event_id AS event_id, rg.id AS registration_id,
                   COALESCE(rm.athlete_id, rg.athlete_id) AS athlete_id
            FROM registration rg
            LEFT JOIN relay_member rm ON rm.registration_id = rg.id AND rm.deleted = 0
            WHERE rg.meet_id = #{meetId} AND rg.deleted = 0 AND rg.status = 'APPROVED'
            <if test="eventIds != null and eventIds.size() > 0">
              AND rg.event_id IN <foreach collection="eventIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
            </if>
            </script>
            """)
    List<EventAthleteRowVO> selectEventAthleteRows(@Param("meetId") Long meetId,
                                                   @Param("eventIds") Collection<Long> eventIds);

    /**
     * 场地列表（道数与道次顺序为分道算法输入）。
     */
    @Select("""
            SELECT v.id AS id, v.meet_id AS meet_id, v.venue_name AS venue_name, v.venue_code AS venue_code,
                   v.venue_type AS venue_type, v.lane_count AS lane_count, v.lane_order_json AS lane_order_json,
                   v.is_wind_measured AS is_wind_measured, v.is_electric_timing AS is_electric_timing,
                   v.status AS status
            FROM venue v
            WHERE v.meet_id = #{meetId} AND v.deleted = 0
            ORDER BY v.id ASC
            """)
    List<VenueBriefVO> selectVenueBriefs(@Param("meetId") Long meetId);

    /**
     * 单个场地。
     */
    @Select("""
            SELECT v.id AS id, v.meet_id AS meet_id, v.venue_name AS venue_name, v.venue_code AS venue_code,
                   v.venue_type AS venue_type, v.lane_count AS lane_count, v.lane_order_json AS lane_order_json,
                   v.is_wind_measured AS is_wind_measured, v.is_electric_timing AS is_electric_timing,
                   v.status AS status
            FROM venue v
            WHERE v.meet_id = #{meetId} AND v.deleted = 0 AND v.id = #{venueId}
            """)
    VenueBriefVO selectVenueBrief(@Param("meetId") Long meetId, @Param("venueId") Long venueId);

    /**
     * 届次级规则（编排相关列）。
     */
    @Select("""
            SELECT m.meet_id AS meet_id, m.conflict_interval_min AS conflict_interval_min,
                   m.referee_interval_min AS referee_interval_min, m.default_lane_count AS default_lane_count,
                   m.default_lane_order_json AS default_lane_order_json, m.lane_assign_mode AS lane_assign_mode,
                   m.field_attempt_count AS field_attempt_count
            FROM meet_rule_config m
            WHERE m.meet_id = #{meetId} AND m.deleted = 0
            LIMIT 1
            """)
    MeetRuleBriefVO selectMeetRuleBrief(@Param("meetId") Long meetId);

    /**
     * 项目级规则（编排相关列）。
     */
    @Select("""
            SELECT erc.event_id AS event_id, erc.draw_mode AS draw_mode,
                   erc.draw_avoid_same_unit AS draw_avoid_same_unit, erc.draw_group_size AS draw_group_size,
                   erc.lane_count AS lane_count, erc.lane_order_json AS lane_order_json,
                   erc.lane_assign_mode AS lane_assign_mode, erc.conflict_interval_min AS conflict_interval_min,
                   erc.attempt_count AS attempt_count, erc.duration_min AS duration_min,
                   erc.extra_json AS extra_json
            FROM event_rule_config erc
            WHERE erc.meet_id = #{meetId} AND erc.deleted = 0 AND erc.event_id = #{eventId}
            LIMIT 1
            """)
    EventRuleBriefVO selectEventRuleBrief(@Param("meetId") Long meetId, @Param("eventId") Long eventId);

    /**
     * 项目级规则批量（分组 / 分道装配）。
     */
    @Select("""
            <script>
            SELECT erc.event_id AS event_id, erc.draw_mode AS draw_mode,
                   erc.draw_avoid_same_unit AS draw_avoid_same_unit, erc.draw_group_size AS draw_group_size,
                   erc.lane_count AS lane_count, erc.lane_order_json AS lane_order_json,
                   erc.lane_assign_mode AS lane_assign_mode, erc.conflict_interval_min AS conflict_interval_min,
                   erc.attempt_count AS attempt_count, erc.duration_min AS duration_min,
                   erc.extra_json AS extra_json
            FROM event_rule_config erc
            WHERE erc.meet_id = #{meetId} AND erc.deleted = 0
            <if test="eventIds != null and eventIds.size() > 0">
              AND erc.event_id IN <foreach collection="eventIds" item="eid" open="(" separator="," close=")">#{eid}</foreach>
            </if>
            </script>
            """)
    List<EventRuleBriefVO> selectEventRuleBriefs(@Param("meetId") Long meetId,
                                                @Param("eventIds") Collection<Long> eventIds);

    /**
     * 项目时长与顺序默认规则（全局表，无 meet_id）。
     */
    @Select("""
            SELECT r.id AS id, r.event_type AS event_type, r.sub_type AS sub_type, r.gender AS gender,
                   r.default_duration_min AS default_duration_min, r.call_ahead_min AS call_ahead_min,
                   r.default_order AS default_order, r.track_priority AS track_priority,
                   r.default_lane_count AS default_lane_count, r.period_preference AS period_preference,
                   r.max_rounds_per_day AS max_rounds_per_day, r.event_interval_min AS event_interval_min,
                   r.is_field_order_required AS is_field_order_required
            FROM event_schedule_rule r
            WHERE r.deleted = 0 AND r.status = 'ENABLED'
            ORDER BY r.id ASC
            """)
    List<EventScheduleRuleVO> selectEventScheduleRules();

    /**
     * 日程的裁判分工（裁判冲突检测人员集合）。
     */
    @Select("""
            <script>
            SELECT ra.schedule_id AS schedule_id, ra.referee_id AS referee_id, ra.duty_code AS duty_code,
                   ra.start_time AS start_time, ra.end_time AS end_time
            FROM referee_assignment ra
            WHERE ra.meet_id = #{meetId} AND ra.deleted = 0 AND ra.status != 'CANCELLED'
            <if test="scheduleIds != null and scheduleIds.size() > 0">
              AND ra.schedule_id IN <foreach collection="scheduleIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
            </if>
            </script>
            """)
    List<RefereeAssignRowVO> selectRefereeAssignRows(@Param("meetId") Long meetId,
                                                    @Param("scheduleIds") Collection<Long> scheduleIds);

    /**
     * 按主键物理删除日程条目（快照回滚等重建场景使用）。
     * <p>
     * 逻辑删除会保留历史行，回滚重建时产生重复堆积；回滚属「以快照为准重建」语义，
     * 因此提供物理删除入口。调用方需先清理 {@code heat / heat_member / lane / field_order}。
     *
     * @param id 日程 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM schedule WHERE id = #{id}")
    int deleteByIdPhysically(@Param("id") Long id);
}
