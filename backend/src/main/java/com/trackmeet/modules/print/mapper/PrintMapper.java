package com.trackmeet.modules.print.mapper;

import com.trackmeet.modules.print.dto.HeatMemberProjection;
import com.trackmeet.modules.print.dto.HeatProjection;
import com.trackmeet.modules.print.dto.LiteProjection;
import com.trackmeet.modules.print.dto.MeetProjection;
import com.trackmeet.modules.print.dto.PrintQuery;
import com.trackmeet.modules.print.dto.ScheduleProjection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 秩序册/分组表/分道表/检录表专用 Mapper。
 * <p>
 * 设计说明：
 * <ol>
 *     <li><b>不依赖 modules/schedule 的实体与 Mapper</b>：全部为 {@code @Select} 投影查询，
 *         直接从 {@code schedule_session} / {@code schedule} / {@code heat} / {@code heat_member} /
 *         {@code lane} / {@code field_order} / {@code event} / {@code event_round} / {@code athlete} /
 *         {@code unit} / {@code meet_group} / {@code venue} / {@code meet} 读取，返回本模块 DTO；</li>
 *     <li><b>自带租户与逻辑删除条件</b>：所有涉及业务表的 SQL 都显式带上
 *         {@code meet_id = #{meetId}} 与 {@code deleted = 0}（join 表的 {@code deleted = 0} 写在 ON 子句），
 *         不依赖多租户拦截器；</li>
 *     <li><b>避免 N+1</b>：日程（含单元/项目/轮次/场地/组别）一次查全，分组按 {@code schedule_id} 批量查，
 *         组内人员按 {@code heat_id} 批量查（{@code IN} 批量），随后在服务层用内存 Map 组装四张表。</li>
 * </ol>
 * 说明：{@code meet} 为主数据表（无 {@code meet_id}），其余表均为届次内业务表。
 *
 * @author TrackMeet
 */
@Mapper
public interface PrintMapper {

    /**
     * 届次信息（秩序册封面/页眉）。
     *
     * @param meetId 届次 ID
     * @return 届次投影，不存在时返回 null
     */
    @Select("SELECT m.id AS meetId, m.meet_name AS meetName, m.edition AS edition, m.short_name AS shortName, "
            + "m.start_date AS startDate, m.end_date AS endDate, m.place AS place, m.host AS host, "
            + "m.organizer AS organizer "
            + "FROM meet m WHERE m.deleted = 0 AND m.id = #{meetId}")
    MeetProjection selectMeetInfo(@Param("meetId") Long meetId);

    /**
     * 日程条目（含单元、项目、轮次、场地、组别）投影，四张表共用的主数据。
     * <p>
     * 排序：日期 → 单元排序号 → 计划开始时间 → 单元内排序号（时段权重由服务层二次排序）。
     *
     * @param query       过滤条件（<b>不可为 null</b>，服务层保证）
     * @param meetId      届次 ID
     * @param publishOnly 是否只取已发布（{@code PUBLISHED}）日程
     * @return 日程投影列表
     */
    @Select("<script>"
            + "SELECT s.id AS scheduleId, s.session_id AS sessionId, s.event_id AS eventId, s.round_id AS roundId, "
            + "s.venue_id AS venueId, s.plan_start_time AS planStartTime, s.plan_end_time AS planEndTime, "
            + "s.heat_count AS heatCount, s.duration_min AS durationMin, s.order_no AS orderNo, "
            + "s.publish_status AS publishStatus, s.conflict_flag AS conflictFlag, s.remark AS scheduleRemark, "
            + "e.event_name AS eventName, e.event_code AS eventCode, e.event_type AS eventType, "
            + "e.sub_type AS subType, e.gender AS gender, e.is_relay AS relayFlag, e.group_id AS groupId, "
            + "e.duration_min AS eventDurationMin, "
            + "g.group_name AS groupName, "
            + "r.round_code AS roundCode, r.round_name AS roundName, r.round_no AS roundNo, r.is_final AS finalFlag, "
            + "ss.session_name AS sessionName, ss.session_date AS sessionDate, ss.day_no AS dayNo, "
            + "ss.period AS period, ss.start_time AS sessionStartTime, ss.end_time AS sessionEndTime, "
            + "ss.sort_no AS sessionSortNo, ss.publish_status AS sessionPublishStatus, "
            + "ss.venue_ids_json AS sessionVenueIdsJson, "
            + "v.venue_name AS venueName, v.lane_count AS venueLaneCount, "
            + "sv.venue_name AS sessionVenueName, sv.lane_count AS sessionVenueLaneCount "
            + "FROM schedule s "
            + "JOIN schedule_session ss ON ss.id = s.session_id AND ss.deleted = 0 "
            + "JOIN event e ON e.id = s.event_id AND e.deleted = 0 "
            + "LEFT JOIN event_round r ON r.id = s.round_id AND r.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = e.group_id AND g.deleted = 0 "
            + "LEFT JOIN venue v ON v.id = s.venue_id AND v.deleted = 0 "
            + "LEFT JOIN venue sv ON sv.id = ss.venue_id AND sv.deleted = 0 "
            + "WHERE s.deleted = 0 AND s.meet_id = #{meetId} "
            + "<if test='publishOnly'>AND s.publish_status = 'PUBLISHED' </if>"
            + "<if test='query.sessionId != null'>AND s.session_id = #{query.sessionId} </if>"
            + "<if test='query.eventId != null'>AND s.event_id = #{query.eventId} </if>"
            + "<if test='query.groupId != null'>AND e.group_id = #{query.groupId} </if>"
            + "<if test='query.venueId != null'>"
            + "AND (s.venue_id = #{query.venueId} OR ss.venue_id = #{query.venueId}) </if>"
            + "<if test='query.unitId != null'>"
            + "AND EXISTS (SELECT 1 FROM heat_member hm WHERE hm.schedule_id = s.id "
            + "AND hm.unit_id = #{query.unitId} AND hm.deleted = 0) </if>"
            + "ORDER BY ss.session_date ASC, ss.sort_no ASC, s.plan_start_time ASC, s.order_no ASC, s.id ASC"
            + "</script>")
    List<ScheduleProjection> selectSchedules(@Param("query") PrintQuery query,
                                             @Param("meetId") Long meetId,
                                             @Param("publishOnly") boolean publishOnly);

    /**
     * 分组投影：按日程条目 ID 批量查询（服务层保证 {@code scheduleIds} 非空）。
     *
     * @param meetId      届次 ID
     * @param scheduleIds 日程条目 ID 列表
     * @return 分组投影列表
     */
    @Select("<script>"
            + "SELECT h.id AS heatId, h.schedule_id AS scheduleId, h.heat_no AS heatNo, h.heat_name AS heatName, "
            + "h.lane_count AS laneCount, h.member_count AS memberCount, h.call_time AS callTime, "
            + "h.call_place AS callPlace, h.start_time AS heatStartTime, h.end_time AS heatEndTime, "
            + "h.status AS heatStatus, h.is_locked AS lockedFlag, h.remark AS heatRemark "
            + "FROM heat h "
            + "WHERE h.deleted = 0 AND h.meet_id = #{meetId} AND h.schedule_id IN "
            + "<foreach collection='scheduleIds' item='scheduleId' open='(' separator=',' close=')'>"
            + "#{scheduleId}</foreach> "
            + "ORDER BY h.schedule_id ASC, h.heat_no ASC, h.id ASC"
            + "</script>")
    List<HeatProjection> selectHeats(@Param("meetId") Long meetId,
                                     @Param("scheduleIds") List<Long> scheduleIds);

    /**
     * 组内人员投影：按分组 ID 批量查询，并关联道次（{@code lane}）、田赛出场顺序（{@code field_order}）、
     * 运动员姓名与单位名称（服务层保证 {@code heatIds} 非空）。
     *
     * @param meetId 届次 ID
     * @param heatIds 分组 ID 列表
     * @param unitId  参赛单位 ID（可为空，为空表示不限单位）
     * @return 组内人员投影列表
     */
    @Select("<script>"
            + "SELECT hm.id AS heatMemberId, hm.heat_id AS heatId, hm.schedule_id AS scheduleId, "
            + "hm.athlete_id AS athleteId, hm.unit_id AS unitId, hm.bib_no AS bibNo, "
            + "hm.order_no AS orderNo, hm.is_seed AS seedFlag, hm.status AS memberStatus, "
            + "hm.is_scratch AS scratchFlag, hm.lane_no AS laneNoRaw, "
            + "hm.seed_result AS seedResult, hm.seed_result_raw AS seedResultRaw, "
            + "hm.seed_result_unit AS seedResultUnit, "
            + "a.athlete_name AS athleteName, a.group_id AS athleteGroupId, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, "
            + "l.lane_no AS laneNo, l.assign_mode AS assignMode, l.is_best_lane AS bestLaneFlag, "
            + "f.order_no AS fieldOrderNo, f.group_no AS fieldGroupNo, "
            + "f.best_seed AS bestSeed, f.best_seed_raw AS bestSeedRaw, f.best_seed_unit AS bestSeedUnit "
            + "FROM heat_member hm "
            + "LEFT JOIN athlete a ON a.id = hm.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0 "
            + "LEFT JOIN lane l ON l.heat_member_id = hm.id AND l.deleted = 0 "
            + "LEFT JOIN field_order f ON f.heat_member_id = hm.id AND f.deleted = 0 "
            + "WHERE hm.deleted = 0 AND hm.meet_id = #{meetId} AND hm.heat_id IN "
            + "<foreach collection='heatIds' item='heatId' open='(' separator=',' close=')'>"
            + "#{heatId}</foreach> "
            + "<if test='unitId != null'>AND hm.unit_id = #{unitId} </if>"
            + "ORDER BY hm.heat_id ASC, hm.order_no ASC, l.lane_no ASC, hm.id ASC"
            + "</script>")
    List<HeatMemberProjection> selectHeatMembers(@Param("meetId") Long meetId,
                                                 @Param("heatIds") List<Long> heatIds,
                                                 @Param("unitId") Long unitId);

    /**
     * 竞赛单元「同时可用场地」清单（{@code schedule_session.venue_ids_json}）。
     * <p>
     * 单元是赛期内的小表（每届次通常 2~10 条），服务层<b>每请求一次</b>批量装入内存，
     * 用于按 {@code venueId} 过滤「日程未指定场地但场地属于该单元可用场地」的条目，
     * 避免在 {@link #selectSchedules} 中引入 MySQL 专有的 JSON 函数（保持 SQL 可移植）。
     * <p>
     * 未传 {@code sessionIds} 时返回该届次全部单元；传入时只查这些单元（{@code IN} 批量）。
     *
     * @param meetId     届次 ID
     * @param sessionIds 单元 ID 列表（可为 null/空，表示不限）
     * @return 单元 ID + 可用场地 JSON 文本
     */
    @Select("<script>"
            + "SELECT ss.id AS id, ss.venue_ids_json AS text "
            + "FROM schedule_session ss "
            + "WHERE ss.deleted = 0 AND ss.meet_id = #{meetId} "
            + "<if test='sessionIds != null and sessionIds.size() > 0'>AND ss.id IN "
            + "<foreach collection='sessionIds' item='sessionId' open='(' separator=',' close=')'>"
            + "#{sessionId}</foreach> </if>"
            + "ORDER BY ss.id ASC"
            + "</script>")
    List<LiteProjection> selectSessionVenueIds(@Param("meetId") Long meetId,
                                               @Param("sessionIds") List<Long> sessionIds);
}
