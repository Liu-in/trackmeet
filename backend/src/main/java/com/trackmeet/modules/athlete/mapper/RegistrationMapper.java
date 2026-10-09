package com.trackmeet.modules.athlete.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.trackmeet.modules.athlete.dto.RegistrationQuery;
import com.trackmeet.modules.athlete.entity.AthleteEventCount;
import com.trackmeet.modules.athlete.entity.Registration;
import com.trackmeet.modules.athlete.entity.RegistrationLimit;
import com.trackmeet.modules.athlete.vo.EventBriefVO;
import com.trackmeet.modules.athlete.vo.RegistrationStatVO;
import com.trackmeet.modules.athlete.vo.RegistrationVO;
import com.trackmeet.modules.athlete.vo.RegistrationWarningVO;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 报名 Mapper：本实体 CRUD + 跨模块查询投影 + 计数聚合 + 统计/预警。
 * <p>
 * 注意：自定义 {@code @Select} 不依赖其它模块实体，直接投影目标表；
 * 所有语句均显式带 {@code meet_id} 与 {@code deleted = 0}，不依赖多租户拦截器。
 *
 * @author TrackMeet
 */
@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {

    /**
     * 届次级限报规则投影（meet_rule_config）。
     * 缺失或为 0/负 视为不限，由服务层统一按此口径解释。
     */
    @Data
    class MeetRules implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 每人限报项数（0 表示不限） */
        private Integer athleteMaxEvents;

        /** 每人限报接力项数 */
        private Integer relayMaxPerAthlete;

        /** 每单位每项限报人数 */
        private Integer maxPerUnit;
    }

    /**
     * 查询单个项目投影（event 表）。
     *
     * @param eventId 项目 ID
     * @param meetId  届次 ID
     * @return 项目投影，不存在返回 null
     */
    @Select("SELECT id, event_name, event_code, group_id, gender, is_relay, relay_min, relay_max, max_per_unit, duration_min "
            + "FROM event WHERE id = #{eventId} AND meet_id = #{meetId} AND deleted = 0")
    EventBriefVO selectEventBrief(@Param("eventId") Long eventId, @Param("meetId") Long meetId);

    /**
     * 批量查询项目投影（event 表）。
     *
     * @param eventIds 项目 ID 集合（不能为空）
     * @param meetId   届次 ID
     * @return 项目投影列表
     */
    @Select("<script>"
            + "SELECT id, event_name, event_code, group_id, gender, is_relay, relay_min, relay_max, max_per_unit, duration_min "
            + "FROM event WHERE meet_id = #{meetId} AND deleted = 0 AND id IN "
            + "<foreach collection='eventIds' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</script>")
    List<EventBriefVO> selectEventBriefs(@Param("eventIds") Collection<Long> eventIds, @Param("meetId") Long meetId);

    /**
     * 查询届次级限报规则。
     *
     * @param meetId 届次 ID
     * @return 规则投影，未配置返回 null
     */
    @Select("SELECT athlete_max_events AS athleteMaxEvents, relay_max_per_athlete AS relayMaxPerAthlete, "
            + "max_per_unit AS maxPerUnit "
            + "FROM meet_rule_config WHERE meet_id = #{meetId} AND deleted = 0 LIMIT 1")
    MeetRules selectMeetRules(@Param("meetId") Long meetId);

    /**
     * 报名分页（联表投影：运动员姓名、单位名、组别名、项目名）。
     */
    @Select("<script>"
            + "SELECT r.id, r.meet_id, r.event_id, r.athlete_id, r.unit_id, r.group_id, r.round_scope, r.is_relay, "
            + "r.relay_name, r.relay_team_seq, r.seed_result, r.seed_value, r.seed_result_unit AS seed_unit, "
            + "r.seed_source, r.seed_source_meet_id, r.seed_time, r.status, r.reject_reason, r.check_result_json, "
            + "r.submit_time, r.audit_by, r.audit_time, r.client_token, r.remark, r.create_time, r.update_time, "
            + "a.athlete_name AS athlete_name, a.bib_no AS bib_no, "
            + "u.unit_name AS unit_name, g.group_name AS group_name, e.event_name AS event_name "
            + "FROM registration r "
            + "LEFT JOIN athlete a ON a.id = r.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0 "
            + "LEFT JOIN event e ON e.id = r.event_id AND e.deleted = 0 "
            + "WHERE r.deleted = 0 "
            + "<if test='meetId != null'>AND r.meet_id = #{meetId} </if>"
            + "<if test='query.eventId != null'>AND r.event_id = #{query.eventId} </if>"
            + "<if test='query.unitId != null'>AND r.unit_id = #{query.unitId} </if>"
            + "<if test='query.groupId != null'>AND r.group_id = #{query.groupId} </if>"
            + "<if test='query.athleteId != null'>AND r.athlete_id = #{query.athleteId} </if>"
            + "<if test='query.status != null and query.status != \"\"'>AND r.status = #{query.status} </if>"
            + "<if test='query.isRelay != null'>AND r.is_relay = #{query.isRelay} </if>"
            + "<if test='query.keyword != null and query.keyword != \"\"'>"
            + "AND (a.athlete_name LIKE CONCAT('%', #{query.keyword}, '%') "
            + "OR u.unit_name LIKE CONCAT('%', #{query.keyword}, '%') "
            + "OR e.event_name LIKE CONCAT('%', #{query.keyword}, '%') "
            + "OR r.relay_name LIKE CONCAT('%', #{query.keyword}, '%')) "
            + "</if>"
            + "ORDER BY r.submit_time DESC, r.id DESC"
            + "</script>")
    IPage<RegistrationVO> selectVoPage(@Param("page") IPage<RegistrationVO> page,
                                       @Param("query") RegistrationQuery query,
                                       @Param("meetId") Long meetId);

    /**
     * 报名详情（联表投影）。
     */
    @Select("SELECT r.id, r.meet_id, r.event_id, r.athlete_id, r.unit_id, r.group_id, r.round_scope, r.is_relay, "
            + "r.relay_name, r.relay_team_seq, r.seed_result, r.seed_value, r.seed_result_unit AS seed_unit, "
            + "r.seed_source, r.seed_source_meet_id, r.seed_time, r.status, r.reject_reason, r.check_result_json, "
            + "r.submit_time, r.audit_by, r.audit_time, r.client_token, r.remark, r.create_time, r.update_time, "
            + "a.athlete_name AS athlete_name, a.bib_no AS bib_no, "
            + "u.unit_name AS unit_name, g.group_name AS group_name, e.event_name AS event_name "
            + "FROM registration r "
            + "LEFT JOIN athlete a ON a.id = r.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0 "
            + "LEFT JOIN event e ON e.id = r.event_id AND e.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.id = #{id} AND r.meet_id = #{meetId}")
    RegistrationVO selectVoById(@Param("id") Long id, @Param("meetId") Long meetId);

    /**
     * 单位 × 项目 有效报名数聚合（待审 + 已通过）。
     */
    @Select("SELECT COUNT(*) AS registered_count, "
            + "IFNULL(SUM(CASE WHEN is_relay = 1 THEN 1 ELSE 0 END), 0) AS relay_registered_count "
            + "FROM registration "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND unit_id = #{unitId} AND event_id = #{eventId} "
            + "AND status IN ('PENDING', 'APPROVED')")
    RegistrationLimit selectUnitEventStat(@Param("meetId") Long meetId,
                                          @Param("unitId") Long unitId,
                                          @Param("eventId") Long eventId);

    /**
     * 运动员项数聚合（个人/接力/全能，待审 + 已通过）。
     */
    @Select("SELECT r.athlete_id, COUNT(*) AS event_count, "
            + "IFNULL(SUM(CASE WHEN e.event_type = 'ALLROUND' THEN 1 ELSE 0 END), 0) AS allround_event_count, "
            + "IFNULL(SUM(CASE WHEN r.is_relay = 1 THEN 1 ELSE 0 END), 0) AS relay_event_count, "
            + "IFNULL(SUM(CASE WHEN r.is_relay = 0 AND (e.event_type IS NULL OR e.event_type <> 'ALLROUND') "
            + "THEN 1 ELSE 0 END), 0) AS individual_event_count, "
            + "MAX(r.unit_id) AS unit_id, MAX(r.group_id) AS group_id, MAX(r.submit_time) AS last_register_time "
            + "FROM registration r "
            + "LEFT JOIN event e ON e.id = r.event_id AND e.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.meet_id = #{meetId} AND r.athlete_id = #{athleteId} "
            + "AND r.status IN ('PENDING', 'APPROVED') "
            + "GROUP BY r.athlete_id")
    AthleteEventCount selectAthleteEventStat(@Param("meetId") Long meetId, @Param("athleteId") Long athleteId);

    /**
     * 单位 × 项目中涉及的运动员（个人项目报名人）。
     */
    @Select("SELECT DISTINCT athlete_id FROM registration "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND unit_id = #{unitId} AND event_id = #{eventId} "
            + "AND status IN ('PENDING', 'APPROVED') AND athlete_id IS NOT NULL")
    List<Long> selectAthleteIdsByUnitEvent(@Param("meetId") Long meetId,
                                           @Param("unitId") Long unitId,
                                           @Param("eventId") Long eventId);

    /**
     * 单位 × 项目中涉及的运动员（接力队员）。
     */
    @Select("SELECT DISTINCT rm.athlete_id FROM relay_member rm "
            + "INNER JOIN registration r ON r.id = rm.registration_id AND r.deleted = 0 "
            + "WHERE rm.deleted = 0 AND rm.meet_id = #{meetId} AND r.unit_id = #{unitId} AND r.event_id = #{eventId} "
            + "AND r.status IN ('PENDING', 'APPROVED')")
    List<Long> selectRelayAthleteIdsByUnitEvent(@Param("meetId") Long meetId,
                                                @Param("unitId") Long unitId,
                                                @Param("eventId") Long eventId);

    /**
     * 运动员个人项目已报项目 ID（待审 + 已通过）。
     */
    @Select("SELECT DISTINCT event_id FROM registration "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND athlete_id = #{athleteId} "
            + "AND status IN ('PENDING', 'APPROVED')")
    List<Long> selectEventIdsByAthlete(@Param("meetId") Long meetId, @Param("athleteId") Long athleteId);

    /**
     * 运动员作为接力队员已报项目 ID（待审 + 已通过）。
     */
    @Select("SELECT DISTINCT r.event_id FROM relay_member rm "
            + "INNER JOIN registration r ON r.id = rm.registration_id AND r.deleted = 0 "
            + "WHERE rm.deleted = 0 AND rm.meet_id = #{meetId} AND rm.athlete_id = #{athleteId} "
            + "AND r.status IN ('PENDING', 'APPROVED')")
    List<Long> selectRelayEventIdsByAthlete(@Param("meetId") Long meetId, @Param("athleteId") Long athleteId);

    /**
     * 同单位同项目已使用的最大接力队序号。
     */
    @Select("SELECT IFNULL(MAX(relay_team_seq), 0) FROM registration "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND unit_id = #{unitId} AND event_id = #{eventId} "
            + "AND is_relay = 1")
    Integer selectMaxRelayTeamSeq(@Param("meetId") Long meetId,
                                  @Param("unitId") Long unitId,
                                  @Param("eventId") Long eventId);

    /**
     * 报名统计：按单位。
     */
    @Select("SELECT 'UNIT' AS dimension, r.unit_id AS ref_id, "
            + "IFNULL(u.unit_name, CONCAT('单位#', r.unit_id)) AS name, "
            + "COUNT(*) AS total, "
            + "IFNULL(SUM(CASE WHEN r.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending, "
            + "IFNULL(SUM(CASE WHEN r.status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved, "
            + "IFNULL(SUM(CASE WHEN r.status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected "
            + "FROM registration r "
            + "LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.meet_id = #{meetId} AND r.status <> 'CANCELLED' "
            + "GROUP BY r.unit_id, u.unit_name "
            + "ORDER BY total DESC, r.unit_id ASC")
    List<RegistrationStatVO> selectStatByUnit(@Param("meetId") Long meetId);

    /**
     * 报名统计：按项目。
     */
    @Select("SELECT 'EVENT' AS dimension, r.event_id AS ref_id, "
            + "IFNULL(e.event_name, CONCAT('项目#', r.event_id)) AS name, "
            + "COUNT(*) AS total, "
            + "IFNULL(SUM(CASE WHEN r.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending, "
            + "IFNULL(SUM(CASE WHEN r.status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved, "
            + "IFNULL(SUM(CASE WHEN r.status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected "
            + "FROM registration r "
            + "LEFT JOIN event e ON e.id = r.event_id AND e.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.meet_id = #{meetId} AND r.status <> 'CANCELLED' "
            + "GROUP BY r.event_id, e.event_name "
            + "ORDER BY total DESC, r.event_id ASC")
    List<RegistrationStatVO> selectStatByEvent(@Param("meetId") Long meetId);

    /**
     * 报名统计：按组别。
     */
    @Select("SELECT 'GROUP' AS dimension, r.group_id AS ref_id, "
            + "IFNULL(g.group_name, CONCAT('组别#', r.group_id)) AS name, "
            + "COUNT(*) AS total, "
            + "IFNULL(SUM(CASE WHEN r.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending, "
            + "IFNULL(SUM(CASE WHEN r.status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved, "
            + "IFNULL(SUM(CASE WHEN r.status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected "
            + "FROM registration r "
            + "LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.meet_id = #{meetId} AND r.status <> 'CANCELLED' "
            + "GROUP BY r.group_id, g.group_name "
            + "ORDER BY total DESC, r.group_id ASC")
    List<RegistrationStatVO> selectStatByGroup(@Param("meetId") Long meetId);

    /**
     * 报名不足预警：已通过报名数低于预警线的项目。
     */
    @Select("SELECT e.id AS event_id, e.event_name AS event_name, "
            + "COUNT(r.id) AS total, "
            + "IFNULL(SUM(CASE WHEN r.status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved "
            + "FROM event e "
            + "LEFT JOIN registration r ON r.event_id = e.id AND r.deleted = 0 AND r.status <> 'CANCELLED' "
            + "WHERE e.deleted = 0 AND e.meet_id = #{meetId} "
            + "GROUP BY e.id, e.event_name "
            + "HAVING IFNULL(SUM(CASE WHEN r.status = 'APPROVED' THEN 1 ELSE 0 END), 0) < #{minApproved} "
            + "ORDER BY approved ASC, e.id ASC")
    List<RegistrationWarningVO> selectWarnings(@Param("meetId") Long meetId,
                                               @Param("minApproved") Integer minApproved);
}
