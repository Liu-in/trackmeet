package com.trackmeet.modules.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.result.entity.AdvanceResult;
import com.trackmeet.modules.result.vo.AdvanceRowVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 晋级名单 Mapper。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；另有：
 * <ol>
 *     <li>晋级名单视图投影（关联项目 / 来源轮次 / 目标轮次 / 运动员 / 单位 / 组别）；</li>
 *     <li><b>按事件 × 目标轮次物理删除</b>：自动晋级采用「先删后插」幂等重建。
 *         表上唯一键 {@code uk_event_to_round_registration} 对逻辑删除行同样生效，
 *         若用逻辑删除会残留占位行导致同名单无法再次写入，故此处必须物理删除。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Mapper
public interface AdvanceResultMapper extends BaseMapper<AdvanceResult> {

    /**
     * 晋级名单视图（按项目 + 目标轮次查询）。
     *
     * @param meetId     届次 ID
     * @param eventId    项目 ID（可空）
     * @param toRoundId  目标轮次 ID（可空）
     * @param fromRoundId 来源轮次 ID（可空）
     * @return 晋级名单视图列表
     */
    @Select("""
            <script>
            SELECT ad.id AS id, ad.meet_id AS meet_id, ad.event_id AS event_id,
                   ad.from_round_id AS from_round_id, ad.to_round_id AS to_round_id,
                   ad.registration_id AS registration_id, ad.athlete_id AS athlete_id,
                   ad.unit_id AS unit_id, ad.group_id AS group_id,
                   ad.source_heat_no AS source_heat_no, ad.source_rank AS source_rank,
                   ad.source_result_value AS source_result_value, ad.source_result_raw AS source_result_raw,
                   ad.advance_type AS advance_type, ad.advance_order AS advance_order,
                   ad.is_confirmed AS is_confirmed, ad.confirm_by AS confirm_by,
                   ad.confirm_time AS confirm_time, ad.remark AS remark, ad.create_time AS create_time,
                   e.event_name AS event_name, e.event_type AS event_type,
                   fr.round_code AS from_round_code, fr.round_name AS from_round_name,
                   tr.round_code AS to_round_code, tr.round_name AS to_round_name,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, a.bib_no AS bib_no,
                   u.unit_name AS unit_name, g.group_name AS group_name, rg.is_relay AS relay_flag
            FROM advance_result ad
            LEFT JOIN event e ON e.id = ad.event_id AND e.deleted = 0
            LEFT JOIN event_round fr ON fr.id = ad.from_round_id AND fr.deleted = 0
            LEFT JOIN event_round tr ON tr.id = ad.to_round_id AND tr.deleted = 0
            LEFT JOIN registration rg ON rg.id = ad.registration_id AND rg.deleted = 0
            LEFT JOIN athlete a ON a.id = COALESCE(ad.athlete_id, rg.athlete_id) AND a.deleted = 0
            LEFT JOIN unit u ON u.id = ad.unit_id AND u.deleted = 0
            LEFT JOIN meet_group g ON g.id = ad.group_id AND g.deleted = 0
            WHERE ad.meet_id = #{meetId} AND ad.deleted = 0
            <if test="eventId != null">AND ad.event_id = #{eventId} </if>
            <if test="toRoundId != null">AND ad.to_round_id = #{toRoundId} </if>
            <if test="fromRoundId != null">AND ad.from_round_id = #{fromRoundId} </if>
            ORDER BY ad.advance_order ASC, ad.id ASC
            </script>
            """)
    List<AdvanceRowVO> selectAdvanceVOs(@Param("meetId") Long meetId,
                                        @Param("eventId") Long eventId,
                                        @Param("toRoundId") Long toRoundId,
                                        @Param("fromRoundId") Long fromRoundId);

    /**
     * 单条晋级名单视图（手动调整后回显）。
     *
     * @param meetId 届次 ID
     * @param id     晋级名单 ID
     * @return 晋级名单视图，不存在时返回 null
     */
    @Select("""
            SELECT ad.id AS id, ad.meet_id AS meet_id, ad.event_id AS event_id,
                   ad.from_round_id AS from_round_id, ad.to_round_id AS to_round_id,
                   ad.registration_id AS registration_id, ad.athlete_id AS athlete_id,
                   ad.unit_id AS unit_id, ad.group_id AS group_id,
                   ad.source_heat_no AS source_heat_no, ad.source_rank AS source_rank,
                   ad.source_result_value AS source_result_value, ad.source_result_raw AS source_result_raw,
                   ad.advance_type AS advance_type, ad.advance_order AS advance_order,
                   ad.is_confirmed AS is_confirmed, ad.confirm_by AS confirm_by,
                   ad.confirm_time AS confirm_time, ad.remark AS remark, ad.create_time AS create_time,
                   e.event_name AS event_name, e.event_type AS event_type,
                   fr.round_code AS from_round_code, fr.round_name AS from_round_name,
                   tr.round_code AS to_round_code, tr.round_name AS to_round_name,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, a.bib_no AS bib_no,
                   u.unit_name AS unit_name, g.group_name AS group_name, rg.is_relay AS relay_flag
            FROM advance_result ad
            LEFT JOIN event e ON e.id = ad.event_id AND e.deleted = 0
            LEFT JOIN event_round fr ON fr.id = ad.from_round_id AND fr.deleted = 0
            LEFT JOIN event_round tr ON tr.id = ad.to_round_id AND tr.deleted = 0
            LEFT JOIN registration rg ON rg.id = ad.registration_id AND rg.deleted = 0
            LEFT JOIN athlete a ON a.id = COALESCE(ad.athlete_id, rg.athlete_id) AND a.deleted = 0
            LEFT JOIN unit u ON u.id = ad.unit_id AND u.deleted = 0
            LEFT JOIN meet_group g ON g.id = ad.group_id AND g.deleted = 0
            WHERE ad.meet_id = #{meetId} AND ad.deleted = 0 AND ad.id = #{id}
            """)
    AdvanceRowVO selectAdvanceVOById(@Param("meetId") Long meetId, @Param("id") Long id);

    /**
     * 按项目 × 目标轮次物理删除晋级名单（幂等重建前置步骤）。
     *
     * @param meetId    届次 ID
     * @param eventId   项目 ID
     * @param toRoundId 目标轮次 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM advance_result WHERE meet_id = #{meetId} AND event_id = #{eventId} "
            + "AND to_round_id = #{toRoundId}")
    int deleteByTargetRoundPhysically(@Param("meetId") Long meetId,
                                      @Param("eventId") Long eventId,
                                      @Param("toRoundId") Long toRoundId);

    /**
     * 确认晋级名单：按项目 × 目标轮次把 {@code is_confirmed} 置 1 并记录确认人 / 确认时间。
     *
     * @param meetId      届次 ID
     * @param eventId     项目 ID
     * @param toRoundId   目标轮次 ID
     * @param confirmBy   确认人 ID
     * @param confirmTime 确认时间
     * @return 受影响行数
     */
    @Update("UPDATE advance_result SET is_confirmed = 1, confirm_by = #{confirmBy}, "
            + "confirm_time = #{confirmTime}, update_time = NOW() "
            + "WHERE meet_id = #{meetId} AND event_id = #{eventId} AND to_round_id = #{toRoundId} AND deleted = 0")
    int confirmByTargetRound(@Param("meetId") Long meetId,
                             @Param("eventId") Long eventId,
                             @Param("toRoundId") Long toRoundId,
                             @Param("confirmBy") Long confirmBy,
                             @Param("confirmTime") LocalDateTime confirmTime);

    /**
     * 查询某目标轮次下已存在的报名 ID（用于生成后校验人数与去重统计）。
     *
     * @param meetId    届次 ID
     * @param eventId   项目 ID
     * @param toRoundId 目标轮次 ID
     * @return 报名 ID 列表
     */
    @Select("SELECT registration_id FROM advance_result WHERE meet_id = #{meetId} AND event_id = #{eventId} "
            + "AND to_round_id = #{toRoundId} AND deleted = 0")
    List<Long> selectRegistrationIds(@Param("meetId") Long meetId,
                                     @Param("eventId") Long eventId,
                                     @Param("toRoundId") Long toRoundId);

    /**
     * 按主键批量物理删除（清理误生成名单）。
     *
     * @param meetId 届次 ID
     * @param ids    晋级名单 ID 列表（不可为空）
     * @return 受影响行数
     */
    @Delete("""
            <script>
            DELETE FROM advance_result WHERE meet_id = #{meetId}
              AND id IN <foreach collection="ids" item="aid" open="(" separator="," close=")">#{aid}</foreach>
            </script>
            """)
    int deleteByIdsPhysically(@Param("meetId") Long meetId, @Param("ids") Collection<Long> ids);
}
