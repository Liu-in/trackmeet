package com.trackmeet.modules.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.vo.EntrySheetRowVO;
import com.trackmeet.modules.result.vo.RankRowVO;
import com.trackmeet.modules.result.vo.ResultVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 成绩 Mapper：单表 CRUD 由 MyBatis-Plus 提供，另含成绩列表 / 录入表 / 名次表投影与批量状态回写。
 * <p>
 * 设计说明：
 * <ol>
 *     <li>所有自定义 SQL <b>自带</b> {@code meet_id} 与 {@code deleted = 0} 条件，
 *         不依赖多租户拦截器；JOIN 表的 {@code deleted = 0} 写在 ON 子句，避免污染主表过滤；</li>
 *     <li>不引用其它模块实体：跨表取数一律用本模块 VO 投影（{@code athlete} / {@code unit} /
 *         {@code meet_group} / {@code event} / {@code event_round} / {@code heat} / {@code heat_member}）；</li>
 *     <li>列名与 {@code sql/04-schema-result-standing.sql} 的 {@code result} 表逐字一致；</li>
 *     <li>无效状态需把 {@code result_value} / {@code rank_no} / {@code rank_display} 置空，
 *         而 MyBatis-Plus 的实体更新默认忽略 null 字段，故批量状态回写使用显式 SQL。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Mapper
public interface ResultMapper extends BaseMapper<Result> {

    /** 成绩列表通用查询列（供列表与名次表共用，保证口径一致） */
    String RESULT_COLUMNS = """
            r.id AS id, r.meet_id AS meet_id, r.schedule_id AS schedule_id, r.heat_id AS heat_id,
            r.heat_member_id AS heat_member_id, r.event_id AS event_id, r.round_id AS round_id,
            r.registration_id AS registration_id, r.athlete_id AS athlete_id, r.unit_id AS unit_id,
            r.group_id AS group_id, r.lane_no AS lane_no, r.result_raw AS result_raw,
            r.result_value AS result_value, r.result_unit AS result_unit, r.result_score AS result_score,
            r.final_height AS final_height, r.rank_no AS rank_no, r.rank_display AS rank_display,
            IFNULL(r.status, 'VALID') AS status, r.foul_type AS foul_type, r.foul_reason AS foul_reason,
            r.wind_speed AS wind_speed, r.is_wind_legal AS is_wind_legal,
            r.is_record_candidate AS is_record_candidate, r.is_record_confirmed AS is_record_confirmed,
            r.record_id AS record_id, r.record_type AS record_type, r.is_pb AS is_pb, r.is_sb AS is_sb,
            r.is_manual AS is_manual, r.allround_group_id AS allround_group_id,
            IFNULL(r.audit_status, 'DRAFT') AS audit_status, r.audit_by AS audit_by,
            r.audit_time AS audit_time, r.reject_reason AS reject_reason, r.is_locked AS is_locked,
            r.client_token AS client_token, r.remark AS remark,
            r.create_time AS create_time, r.update_time AS update_time,
            COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, a.bib_no AS bib_no,
            u.unit_name AS unit_name, g.group_name AS group_name,
            e.event_name AS event_name, e.event_code AS event_code, e.event_type AS event_type,
            e.sub_type AS sub_type, rg.is_relay AS relay_flag,
            rd.round_code AS round_code, rd.round_name AS round_name,
            h.heat_no AS heat_no, h.heat_name AS heat_name
            """;

    /** 成绩列表通用查询关联（LEFT JOIN 全部带 deleted 与 meet_id 条件） */
    String RESULT_JOINS = """
            FROM result r
            LEFT JOIN registration rg ON rg.id = r.registration_id AND rg.deleted = 0
            LEFT JOIN athlete a ON a.id = COALESCE(r.athlete_id, rg.athlete_id) AND a.deleted = 0
            LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0
            LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0
            LEFT JOIN event e ON e.id = r.event_id AND e.deleted = 0
            LEFT JOIN event_round rd ON rd.id = r.round_id AND rd.deleted = 0
            LEFT JOIN heat h ON h.id = r.heat_id AND h.deleted = 0
            """;

    /**
     * 成绩分页列表（条件：日程/项目/轮次/分组/单位/组别/运动员/审核状态/成绩状态/锁定/关键字）。
     * <p>
     * 关键字同时匹配成绩原文、号码布与姓名（后两项用 EXISTS 子查询，避免 JOIN 造成行放大）。
     *
     * @param page  分页对象（由 MyBatis-Plus 分页插件处理）
     * @param query 查询条件
     * @return 成绩视图分页
     */
    @Select("""
            <script>
            SELECT
            """ + RESULT_COLUMNS + RESULT_JOINS + """
            WHERE r.meet_id = #{meetId} AND r.deleted = 0
            <if test="scheduleId != null">AND r.schedule_id = #{scheduleId} </if>
            <if test="eventId != null">AND r.event_id = #{eventId} </if>
            <if test="roundId != null">AND r.round_id = #{roundId} </if>
            <if test="heatId != null">AND r.heat_id = #{heatId} </if>
            <if test="unitId != null">AND r.unit_id = #{unitId} </if>
            <if test="groupId != null">AND r.group_id = #{groupId} </if>
            <if test="athleteId != null">AND r.athlete_id = #{athleteId} </if>
            <if test="auditStatus != null and auditStatus != ''">
              AND IFNULL(r.audit_status, 'DRAFT') = #{auditStatus} </if>
            <if test="status != null and status != ''">
              AND IFNULL(r.status, 'VALID') = #{status} </if>
            <if test="isLocked != null">AND r.is_locked = #{isLocked} </if>
            <if test="keyword != null and keyword != ''">
              AND (r.result_raw LIKE CONCAT('%', #{keyword}, '%')
                   OR EXISTS (SELECT 1 FROM athlete ka WHERE ka.id = COALESCE(r.athlete_id, rg.athlete_id)
                              AND ka.deleted = 0 AND (ka.athlete_name LIKE CONCAT('%', #{keyword}, '%')
                              OR ka.bib_no LIKE CONCAT('%', #{keyword}, '%')))
                   OR rg.relay_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY r.event_id ASC, r.round_id ASC, r.heat_id ASC,
                     (r.rank_no IS NULL) ASC, r.rank_no ASC, r.id ASC
            </script>
            """)
    IPage<ResultVO> selectResultPage(IPage<ResultVO> page, @Param("meetId") Long meetId,
                                     @Param("scheduleId") Long scheduleId,
                                     @Param("eventId") Long eventId,
                                     @Param("roundId") Long roundId,
                                     @Param("heatId") Long heatId,
                                     @Param("unitId") Long unitId,
                                     @Param("groupId") Long groupId,
                                     @Param("athleteId") Long athleteId,
                                     @Param("auditStatus") String auditStatus,
                                     @Param("status") String status,
                                     @Param("isLocked") Integer isLocked,
                                     @Param("keyword") String keyword);

    /**
     * 成绩录入表：编排结果（{@code heat_member}）左连已有成绩（{@code result}），
     * 保证未录成绩者也出现在录入表上；按组次 → 道次 → 出场顺序 → 组内人员 ID 排列。
     * <p>
     * 定位方式二选一：传 {@code scheduleId}，或传 {@code eventId} + {@code roundId}。
     *
     * @param meetId     届次 ID
     * @param scheduleId 日程条目 ID（可为空）
     * @param eventId    项目 ID（可为空）
     * @param roundId    轮次 ID（可为空）
     * @return 录入表行列表
     */
    @Select("""
            <script>
            SELECT hm.schedule_id AS schedule_id, sc.event_id AS event_id, sc.round_id AS round_id,
                   hm.heat_id AS heat_id, hm.id AS heat_member_id, hm.registration_id AS registration_id,
                   r.id AS result_id, hm.athlete_id AS athlete_id, hm.unit_id AS unit_id,
                   COALESCE(hm.group_id, r.group_id) AS group_id,
                   h.heat_no AS heat_no, h.heat_name AS heat_name,
                   COALESCE(hm.lane_no, r.lane_no) AS lane_no, hm.order_no AS order_no,
                   hm.bib_no AS bib_no, COALESCE(a.athlete_name, rg.relay_name) AS athlete_name,
                   u.unit_name AS unit_name, g.group_name AS group_name,
                   hm.is_seed AS is_seed, hm.is_scratch AS is_scratch,
                   hm.seed_result AS seed_result, hm.seed_result_raw AS seed_result_raw,
                   hm.seed_result_unit AS seed_result_unit,
                   r.result_raw AS result_raw, r.result_value AS result_value, r.result_unit AS result_unit,
                   IFNULL(r.status, 'VALID') AS result_status,
                   r.audit_status AS audit_status, r.rank_no AS rank_no, r.rank_display AS rank_display,
                   r.wind_speed AS wind_speed, r.is_wind_legal AS is_wind_legal,
                   hm.status AS member_status
            FROM heat_member hm
            JOIN schedule sc ON sc.id = hm.schedule_id AND sc.deleted = 0
            LEFT JOIN heat h ON h.id = hm.heat_id AND h.deleted = 0
            LEFT JOIN result r ON r.id = (
                SELECT r2.id FROM result r2
                WHERE r2.meet_id = hm.meet_id AND r2.deleted = 0
                  AND r2.schedule_id = hm.schedule_id AND r2.registration_id = hm.registration_id
                ORDER BY r2.id ASC LIMIT 1)
            LEFT JOIN registration rg ON rg.id = hm.registration_id AND rg.deleted = 0
            LEFT JOIN athlete a ON a.id = COALESCE(hm.athlete_id, rg.athlete_id) AND a.deleted = 0
            LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0
            LEFT JOIN meet_group g ON g.id = COALESCE(hm.group_id, r.group_id) AND g.deleted = 0
            WHERE hm.meet_id = #{meetId} AND hm.deleted = 0
            <if test="scheduleId != null">AND hm.schedule_id = #{scheduleId} </if>
            <if test="eventId != null">AND sc.event_id = #{eventId} </if>
            <if test="roundId != null">AND sc.round_id = #{roundId} </if>
            ORDER BY h.heat_no ASC, (COALESCE(hm.lane_no, r.lane_no) IS NULL) ASC,
                     COALESCE(hm.lane_no, r.lane_no) ASC, hm.order_no ASC, hm.id ASC
            </script>
            """)
    List<EntrySheetRowVO> selectEntrySheetRows(@Param("meetId") Long meetId,
                                               @Param("scheduleId") Long scheduleId,
                                               @Param("eventId") Long eventId,
                                               @Param("roundId") Long roundId);

    /**
     * 名次表投影（预览 / 生成 / 展示共用）：含成绩列、名次列、风速列与关联展示字段。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @param roundId 轮次 ID
     * @return 名次行列表
     */
    @Select("""
            <script>
            SELECT
            """ + RESULT_COLUMNS + RESULT_JOINS + """
            WHERE r.meet_id = #{meetId} AND r.deleted = 0
              AND r.event_id = #{eventId} AND r.round_id = #{roundId}
            ORDER BY (r.rank_no IS NULL) ASC, r.rank_no ASC, r.heat_id ASC, r.id ASC
            </script>
            """)
    List<RankRowVO> selectRankRows(@Param("meetId") Long meetId,
                                  @Param("eventId") Long eventId,
                                  @Param("roundId") Long roundId);

    /**
     * 按主键批量回写名次（{@code rank_no} / {@code rank_display}）。
     *
     * @param meetId 届次 ID
     * @param id     成绩 ID
     * @param rankNo 名次序号（无效成绩传 null 以清除名次）
     * @return 受影响行数
     */
    @Update("UPDATE result SET rank_no = #{rankNo}, update_time = NOW() "
            + "WHERE id = #{id} AND meet_id = #{meetId} AND deleted = 0")
    int updateRankNo(@Param("meetId") Long meetId, @Param("id") Long id, @Param("rankNo") Integer rankNo);

    /**
     * 按主键批量回写名次显示文本（{@code rank_display}）。
     *
     * @param meetId      届次 ID
     * @param id          成绩 ID
     * @param rankDisplay 名次显示文本（无效成绩传 null 以清除名次）
     * @return 受影响行数
     */
    @Update("UPDATE result SET rank_display = #{rankDisplay}, update_time = NOW() "
            + "WHERE id = #{id} AND meet_id = #{meetId} AND deleted = 0")
    int updateRankDisplay(@Param("meetId") Long meetId, @Param("id") Long id,
                          @Param("rankDisplay") String rankDisplay);

    /**
     * 按项目 × 轮次清空名次（成绩改值、驳回或撤销审核时联动，避免名次与成绩不一致）。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @param roundId 轮次 ID
     * @return 受影响行数
     */
    @Update("UPDATE result SET rank_no = NULL, rank_display = NULL, update_time = NOW() "
            + "WHERE meet_id = #{meetId} AND deleted = 0 AND event_id = #{eventId} AND round_id = #{roundId} "
            + "AND (rank_no IS NOT NULL OR rank_display IS NOT NULL)")
    int clearRankByRound(@Param("meetId") Long meetId,
                         @Param("eventId") Long eventId,
                         @Param("roundId") Long roundId);

    /**
     * 批量标记成绩状态：无效状态（DNS/DNF/DSQ/NM/PASS/SCRATCH）同时把
     * {@code result_value} / {@code rank_no} / {@code rank_display} 置空，使其不参与名次。
     *
     * @param meetId     届次 ID
     * @param ids        成绩 ID 列表（不可为空）
     * @param status     目标状态
     * @param foulType   犯规类型码（可空）
     * @param foulReason 判罚依据说明（可空）
     * @return 受影响行数
     */
    @Update("""
            <script>
            UPDATE result
            SET status = #{status},
                foul_type = #{foulType},
                foul_reason = #{foulReason},
                result_value = CASE WHEN #{status} = 'VALID' THEN result_value ELSE NULL END,
                rank_no = CASE WHEN #{status} = 'VALID' THEN rank_no ELSE NULL END,
                rank_display = CASE WHEN #{status} = 'VALID' THEN rank_display ELSE NULL END,
                update_time = NOW()
            WHERE meet_id = #{meetId} AND deleted = 0
              AND id IN <foreach collection="ids" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            </script>
            """)
    int updateStatusBatch(@Param("meetId") Long meetId,
                          @Param("ids") Collection<Long> ids,
                          @Param("status") String status,
                          @Param("foulType") String foulType,
                          @Param("foulReason") String foulReason);

    /**
     * 批量回写风速判定：{@code wind_speed}、{@code is_wind_legal} 与破纪录候选位。
     * <p>
     * 超风速时清除 {@code is_record_confirmed} 与对标纪录（{@code record_id}），
     * 但保留 {@code is_record_candidate}，符合「超风速不认定纪录」规则。
     *
     * @param meetId     届次 ID
     * @param ids        成绩 ID 列表（不可为空）
     * @param windSpeed  风速（m/s）
     * @param windLegal  是否合法：1 合法 / 0 超风速
     * @return 受影响行数
     */
    @Update("""
            <script>
            UPDATE result
            SET wind_speed = #{windSpeed},
                is_wind_legal = #{windLegal},
                is_record_confirmed = CASE WHEN #{windLegal} = 1 THEN is_record_confirmed ELSE 0 END,
                record_id = CASE WHEN #{windLegal} = 1 THEN record_id ELSE NULL END,
                update_time = NOW()
            WHERE meet_id = #{meetId} AND deleted = 0
              AND id IN <foreach collection="ids" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            </script>
            """)
    int updateWindBatch(@Param("meetId") Long meetId,
                        @Param("ids") Collection<Long> ids,
                        @Param("windSpeed") BigDecimal windSpeed,
                        @Param("windLegal") Integer windLegal);

    /**
     * 批量流转审核状态（提交 / 审核通过 / 驳回）。
     * <p>
     * 审核通过时写入 {@code is_locked = 1}，驳回时 {@code is_locked = 0} 并写入驳回原因。
     *
     * @param meetId       届次 ID
     * @param ids          成绩 ID 列表（不可为空）
     * @param auditStatus  目标审核状态：SUBMITTED/APPROVED/REJECTED
     * @param isLocked     是否锁定：1 锁定 / 0 未锁定
     * @param auditBy      审核人 ID（提交时可为空）
     * @param rejectReason 驳回原因（仅 REJECTED 时写入）
     * @return 受影响行数
     */
    @Update("""
            <script>
            UPDATE result
            SET audit_status = #{auditStatus},
                is_locked = #{isLocked},
                audit_by = CASE WHEN #{auditStatus} = 'SUBMITTED' THEN audit_by ELSE #{auditBy} END,
                audit_time = CASE WHEN #{auditStatus} = 'SUBMITTED' THEN audit_time ELSE #{auditTime} END,
                reject_reason = #{rejectReason},
                update_time = NOW()
            WHERE meet_id = #{meetId} AND deleted = 0
              AND id IN <foreach collection="ids" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            </script>
            """)
    int updateAuditBatch(@Param("meetId") Long meetId,
                         @Param("ids") Collection<Long> ids,
                         @Param("auditStatus") String auditStatus,
                         @Param("isLocked") Integer isLocked,
                         @Param("auditBy") Long auditBy,
                         @Param("auditTime") LocalDateTime auditTime,
                         @Param("rejectReason") String rejectReason);

    /**
     * 回写破纪录认定结果。
     *
     * @param meetId           届次 ID
     * @param resultId         成绩 ID
     * @param confirmed        是否认定：1 认定 / 0 取消认定
     * @param recordId         对标纪录 ID（取消认定时写 null）
     * @param recordType       认定纪录层级
     * @param recordCandidate  是否破纪录候选：1 是 / 0 否
     * @param remark           认定说明（写入备注）
     * @return 受影响行数
     */
    @Update("""
            UPDATE result
            SET is_record_confirmed = #{confirmed},
                record_id = #{recordId},
                record_type = #{recordType},
                is_record_candidate = #{recordCandidate},
                remark = COALESCE(#{remark}, remark),
                update_time = NOW()
            WHERE id = #{resultId} AND meet_id = #{meetId} AND deleted = 0
            """)
    int updateRecordConfirm(@Param("meetId") Long meetId,
                            @Param("resultId") Long resultId,
                            @Param("confirmed") Integer confirmed,
                            @Param("recordId") Long recordId,
                            @Param("recordType") String recordType,
                            @Param("recordCandidate") Integer recordCandidate,
                            @Param("remark") String remark);

    /**
     * 查询一轮成绩的原始实体列表（名次装配与晋级装配的输入）。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID
     * @param roundId 轮次 ID
     * @return 成绩实体列表
     */
    @Select("""
            SELECT * FROM result
            WHERE meet_id = #{meetId} AND deleted = 0 AND event_id = #{eventId} AND round_id = #{roundId}
            ORDER BY id ASC
            """)
    List<Result> selectByRound(@Param("meetId") Long meetId,
                               @Param("eventId") Long eventId,
                               @Param("roundId") Long roundId);

    /**
     * 查询一批成绩的原始实体列表（按 ID）。
     *
     * @param meetId 届次 ID
     * @param ids    成绩 ID 列表（不可为空）
     * @return 成绩实体列表
     */
    @Select("""
            <script>
            SELECT * FROM result
            WHERE meet_id = #{meetId} AND deleted = 0
              AND id IN <foreach collection="ids" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            ORDER BY id ASC
            </script>
            """)
    List<Result> selectByIds(@Param("meetId") Long meetId, @Param("ids") Collection<Long> ids);

    /**
     * 统计成绩引用某纪录的条数（纪录删除前的引用校验）。
     *
     * @param meetId   届次 ID
     * @param recordId 纪录 ID
     * @return 引用条数
     */
    @Select("SELECT COUNT(*) FROM result WHERE meet_id = #{meetId} AND record_id = #{recordId} AND deleted = 0")
    long countByRecordId(@Param("meetId") Long meetId, @Param("recordId") Long recordId);
}
