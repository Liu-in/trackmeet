package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.Lane;
import com.trackmeet.modules.schedule.vo.LaneVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 道次 Mapper：单表 CRUD 由 MyBatis-Plus 提供，列表投影显式带 meet_id 与 deleted 条件。
 *
 * @author TrackMeet
 */
@Mapper
public interface LaneMapper extends BaseMapper<Lane> {

    /**
     * 分组的道次列表（含人员展示字段）。
     */
    @Select("""
            <script>
            SELECT l.id AS id, l.meet_id AS meet_id, l.heat_id AS heat_id, l.heat_member_id AS heat_member_id,
                   l.schedule_id AS schedule_id, l.lane_no AS lane_no, l.assign_mode AS assign_mode,
                   l.lane_order_source AS lane_order_source, l.lane_order_json AS lane_order_json,
                   l.seed_rank AS seed_rank, l.is_best_lane AS is_best_lane, l.is_locked AS is_locked,
                   l.locked_by AS locked_by, l.lock_time AS lock_time, l.swap_group AS swap_group,
                   l.swap_from_lane AS swap_from_lane, l.swap_reason AS swap_reason, l.swap_time AS swap_time,
                   l.swap_by AS swap_by, l.batch_no AS batch_no, l.remark AS remark,
                   hm.registration_id AS registration_id, hm.athlete_id AS athlete_id,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, hm.bib_no AS bib_no,
                   hm.unit_id AS unit_id, u.unit_name AS unit_name, h.heat_no AS heat_no
            FROM lane l
            LEFT JOIN heat_member hm ON hm.id = l.heat_member_id AND hm.deleted = 0
            LEFT JOIN athlete a ON a.id = hm.athlete_id AND a.deleted = 0
            LEFT JOIN registration rg ON rg.id = hm.registration_id AND rg.deleted = 0
            LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0
            LEFT JOIN heat h ON h.id = l.heat_id AND h.deleted = 0
            WHERE l.meet_id = #{meetId} AND l.deleted = 0
              AND l.heat_id IN <foreach collection="heatIds" item="hid" open="(" separator="," close=")">#{hid}</foreach>
            ORDER BY l.heat_id ASC, l.lane_no ASC, l.id ASC
            </script>
            """)
    List<LaneVO> selectLaneVOs(@Param("meetId") Long meetId, @Param("heatIds") Collection<Long> heatIds);

    /**
     * 日程的全部分道结果。
     */
    @Select("""
            SELECT l.id AS id, l.meet_id AS meet_id, l.heat_id AS heat_id, l.heat_member_id AS heat_member_id,
                   l.schedule_id AS schedule_id, l.lane_no AS lane_no, l.assign_mode AS assign_mode,
                   l.lane_order_source AS lane_order_source, l.lane_order_json AS lane_order_json,
                   l.seed_rank AS seed_rank, l.is_best_lane AS is_best_lane, l.is_locked AS is_locked,
                   l.locked_by AS locked_by, l.lock_time AS lock_time, l.swap_group AS swap_group,
                   l.swap_from_lane AS swap_from_lane, l.swap_reason AS swap_reason, l.swap_time AS swap_time,
                   l.swap_by AS swap_by, l.batch_no AS batch_no, l.remark AS remark,
                   hm.registration_id AS registration_id, hm.athlete_id AS athlete_id,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, hm.bib_no AS bib_no,
                   hm.unit_id AS unit_id, u.unit_name AS unit_name, h.heat_no AS heat_no
            FROM lane l
            LEFT JOIN heat_member hm ON hm.id = l.heat_member_id AND hm.deleted = 0
            LEFT JOIN athlete a ON a.id = hm.athlete_id AND a.deleted = 0
            LEFT JOIN registration rg ON rg.id = hm.registration_id AND rg.deleted = 0
            LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0
            LEFT JOIN heat h ON h.id = l.heat_id AND h.deleted = 0
            WHERE l.meet_id = #{meetId} AND l.deleted = 0 AND l.schedule_id = #{scheduleId}
            ORDER BY l.heat_id ASC, l.lane_no ASC, l.id ASC
            """)
    List<LaneVO> selectLaneVOsBySchedule(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);

    /**
     * 按分组物理删除道次。
     * <p>
     * 逻辑删除会残留唯一键 {@code uk_lane_heat_no} / {@code uk_lane_heat_member} 占位行，
     * 导致重新分配道次时无法复用道次号，故重建场景统一使用物理删除。
     *
     * @param heatIds 分组 ID 集合（不可为空）
     * @return 受影响行数
     */
    @Delete("""
            <script>
            DELETE FROM lane WHERE heat_id IN
            <foreach collection="heatIds" item="hid" open="(" separator="," close=")">#{hid}</foreach>
            </script>
            """)
    int deleteByHeatIdsPhysically(@Param("heatIds") Collection<Long> heatIds);

    /**
     * 按日程物理删除道次。
     *
     * @param meetId     届次 ID
     * @param scheduleId 日程 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM lane WHERE meet_id = #{meetId} AND schedule_id = #{scheduleId}")
    int deleteBySchedulePhysically(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);

    /**
     * 按组内人员物理删除道次。
     *
     * @param heatMemberId 组内人员 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM lane WHERE heat_member_id = #{heatMemberId}")
    int deleteByHeatMemberPhysically(@Param("heatMemberId") Long heatMemberId);
}
