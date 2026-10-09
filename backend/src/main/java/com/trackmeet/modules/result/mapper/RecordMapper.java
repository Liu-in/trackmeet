package com.trackmeet.modules.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.trackmeet.modules.result.entity.Record;
import com.trackmeet.modules.result.vo.RecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 纪录 Mapper（对应 {@code record} 表，DDL 见 {@code sql/02-schema-meet-athlete.sql}）。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；自定义 SQL 自带 {@code meet_id}、{@code deleted} 条件，
 * 另含「破纪录候选扫描」投影：按现行纪录对标同项目、同组别归并码、同性别、同接力属性的有效成绩。
 *
 * @author TrackMeet
 */
@Mapper
public interface RecordMapper extends BaseMapper<Record> {

    /**
     * 纪录分页列表（条件：项目 / 组别 / 组别归并码 / 层级 / 性别 / 接力 / 是否现行 / 关键字）。
     *
     * @param page  分页对象（由 MyBatis-Plus 分页插件处理）
     * @param query 查询条件
     * @return 纪录视图分页
     */
    @Select("""
            <script>
            SELECT rc.id AS id, rc.meet_id AS meet_id, rc.record_type AS record_type,
                   rc.event_id AS event_id,
                   COALESCE(rc.event_name, e.event_name) AS event_name,
                   rc.group_id AS group_id, rc.group_code AS group_code, rc.gender AS gender,
                   rc.is_relay AS is_relay, rc.athlete_name AS athlete_name, rc.unit_name AS unit_name,
                   rc.region_name AS region_name, rc.record_value AS record_value,
                   rc.record_raw AS record_raw, rc.record_unit AS record_unit,
                   rc.wind_speed AS wind_speed, rc.is_wind_legal AS is_wind_legal,
                   rc.record_date AS record_date, rc.record_place AS record_place,
                   rc.is_current AS is_current, rc.source AS source,
                   rc.create_time AS create_time, rc.update_time AS update_time,
                   g.group_name AS group_name, e.event_code AS event_code, e.event_type AS event_type
            FROM record rc
            LEFT JOIN event e ON e.id = rc.event_id AND e.deleted = 0
            LEFT JOIN meet_group g ON g.id = rc.group_id AND g.deleted = 0
            WHERE rc.meet_id = #{meetId} AND rc.deleted = 0
            <if test="eventId != null">AND rc.event_id = #{eventId} </if>
            <if test="groupId != null">AND rc.group_id = #{groupId} </if>
            <if test="groupCode != null and groupCode != ''">AND rc.group_code = #{groupCode} </if>
            <if test="recordType != null and recordType != ''">AND rc.record_type = #{recordType} </if>
            <if test="gender != null and gender != ''">AND rc.gender = #{gender} </if>
            <if test="isRelay != null">AND rc.is_relay = #{isRelay} </if>
            <if test="isCurrent != null">AND rc.is_current = #{isCurrent} </if>
            <if test="keyword != null and keyword != ''">
              AND (rc.athlete_name LIKE CONCAT('%', #{keyword}, '%')
                   OR rc.unit_name LIKE CONCAT('%', #{keyword}, '%')
                   OR rc.event_name LIKE CONCAT('%', #{keyword}, '%')
                   OR e.event_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY rc.event_id ASC, rc.record_type ASC, rc.is_current DESC, rc.id ASC
            </script>
            """)
    IPage<RecordVO> selectRecordPage(IPage<RecordVO> page, @Param("meetId") Long meetId,
                                     @Param("eventId") Long eventId,
                                     @Param("groupId") Long groupId,
                                     @Param("groupCode") String groupCode,
                                     @Param("recordType") String recordType,
                                     @Param("gender") String gender,
                                     @Param("isRelay") Integer isRelay,
                                     @Param("isCurrent") Integer isCurrent,
                                     @Param("keyword") String keyword);

    /**
     * 查询某项目现行纪录（用于破纪录候选对标）。
     * <p>
     * 组别归并码与性别为可空条件（空值表示不限制），一次返回全部层级，由服务层按成绩逐项比较。
     *
     * @param meetId    届次 ID
     * @param eventId   项目 ID
     * @param groupCode 组别归并码（可空）
     * @param gender    性别（可空）
     * @return 现行纪录列表（按纪录值由好到差、层级排序）
     */
    @Select("""
            <script>
            SELECT rc.id AS id, rc.meet_id AS meet_id, rc.record_type AS record_type,
                   rc.event_id AS event_id,
                   COALESCE(rc.event_name, e.event_name) AS event_name,
                   rc.group_id AS group_id, rc.group_code AS group_code, rc.gender AS gender,
                   rc.is_relay AS is_relay, rc.athlete_name AS athlete_name, rc.unit_name AS unit_name,
                   rc.region_name AS region_name, rc.record_value AS record_value,
                   rc.record_raw AS record_raw, rc.record_unit AS record_unit,
                   rc.wind_speed AS wind_speed, rc.is_wind_legal AS is_wind_legal,
                   rc.record_date AS record_date, rc.record_place AS record_place,
                   rc.is_current AS is_current, rc.source AS source,
                   rc.create_time AS create_time, rc.update_time AS update_time,
                   g.group_name AS group_name, e.event_code AS event_code, e.event_type AS event_type
            FROM record rc
            LEFT JOIN event e ON e.id = rc.event_id AND e.deleted = 0
            LEFT JOIN meet_group g ON g.id = rc.group_id AND g.deleted = 0
            WHERE rc.meet_id = #{meetId} AND rc.deleted = 0 AND rc.is_current = 1
              AND rc.event_id = #{eventId}
            <if test="groupCode != null and groupCode != ''">AND rc.group_code = #{groupCode} </if>
            <if test="gender != null and gender != ''">AND (rc.gender = #{gender} OR rc.gender = 'MIXED') </if>
            ORDER BY rc.record_type ASC, rc.id ASC
            </script>
            """)
    List<RecordVO> selectCurrentRecords(@Param("meetId") Long meetId,
                                        @Param("eventId") Long eventId,
                                        @Param("groupCode") String groupCode,
                                        @Param("gender") String gender);

    /**
     * 统计成绩引用某纪录的条数（删除纪录前引用校验）。
     *
     * @param meetId   届次 ID
     * @param recordId 纪录 ID
     * @return 引用条数
     */
    @Select("SELECT COUNT(*) FROM result WHERE meet_id = #{meetId} AND record_id = #{recordId} AND deleted = 0")
    long countResultReference(@Param("meetId") Long meetId, @Param("recordId") Long recordId);

    /**
     * 破纪录候选扫描投影行：有效成绩 + 项目 + 组别归并码 + 性别 + 接力属性 + 已有纪录对标信息。
     * <p>
     * 仅返回 {@code status = VALID} 且成绩数值非空的成绩，供服务层逐条与现行纪录比较。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID（可空，为空则扫描全届次）
     * @param roundId 轮次 ID（可空）
     * @return 候选投影行列表
     */
    @Select("""
            <script>
            SELECT r.id AS result_id, r.meet_id AS meet_id, r.event_id AS event_id,
                   r.round_id AS round_id, r.schedule_id AS schedule_id, r.heat_id AS heat_id,
                   r.registration_id AS registration_id, r.athlete_id AS athlete_id,
                   r.unit_id AS unit_id, r.group_id AS group_id,
                   r.result_raw AS result_raw, r.result_value AS result_value,
                   r.result_unit AS result_unit, r.rank_no AS rank_no, r.rank_display AS rank_display,
                   IFNULL(r.status, 'VALID') AS status, r.wind_speed AS wind_speed,
                   r.is_wind_legal AS is_wind_legal, r.is_record_candidate AS is_record_candidate,
                   r.is_record_confirmed AS is_record_confirmed, r.record_id AS record_id,
                   r.record_type AS record_type,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, a.bib_no AS bib_no,
                   a.gender AS gender, u.unit_name AS unit_name, g.group_name AS group_name,
                   g.record_group_code AS group_code, e.event_name AS event_name,
                   e.event_type AS event_type, e.sub_type AS sub_type, e.gender AS event_gender,
                   rg.is_relay AS relay_flag
            FROM result r
            LEFT JOIN registration rg ON rg.id = r.registration_id AND rg.deleted = 0
            LEFT JOIN athlete a ON a.id = COALESCE(r.athlete_id, rg.athlete_id) AND a.deleted = 0
            LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0
            LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0
            LEFT JOIN event e ON e.id = r.event_id AND e.deleted = 0
            WHERE r.meet_id = #{meetId} AND r.deleted = 0
              AND IFNULL(r.status, 'VALID') = 'VALID' AND r.result_value IS NOT NULL
            <if test="eventId != null">AND r.event_id = #{eventId} </if>
            <if test="roundId != null">AND r.round_id = #{roundId} </if>
            ORDER BY r.event_id ASC, r.id ASC
            </script>
            """)
    List<RecordCandidateRowVO> selectRecordCandidateRows(@Param("meetId") Long meetId,
                                                         @Param("eventId") Long eventId,
                                                         @Param("roundId") Long roundId);

    /**
     * 回写单条成绩的破纪录候选信息（扫描候选时逐条更新，各条命中的纪录层级可能不同）。
     *
     * @param meetId          届次 ID
     * @param resultId        成绩 ID
     * @param isCandidate     是否破纪录候选：1 是 / 0 否
     * @param recordId        对标纪录 ID（无对标时写 null）
     * @param recordType      对标纪录层级
     * @return 受影响行数
     */
    @Update("UPDATE result SET is_record_candidate = #{isCandidate}, record_id = #{recordId}, "
            + "record_type = #{recordType}, update_time = NOW() "
            + "WHERE id = #{resultId} AND meet_id = #{meetId} AND deleted = 0")
    int updateCandidate(@Param("meetId") Long meetId,
                        @Param("resultId") Long resultId,
                        @Param("isCandidate") Integer isCandidate,
                        @Param("recordId") Long recordId,
                        @Param("recordType") String recordType);
}
