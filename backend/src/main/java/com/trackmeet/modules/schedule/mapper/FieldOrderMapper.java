package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.FieldOrder;
import com.trackmeet.modules.schedule.vo.FieldOrderVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 田赛出场顺序 Mapper：单表 CRUD 由 MyBatis-Plus 提供，列表投影显式带 meet_id 与 deleted 条件。
 *
 * @author TrackMeet
 */
@Mapper
public interface FieldOrderMapper extends BaseMapper<FieldOrder> {

    /**
     * 分组的出场顺序列表（含人员与项目展示字段）。
     */
    @Select("""
            <script>
            SELECT fo.id AS id, fo.meet_id AS meet_id, fo.heat_id AS heat_id, fo.heat_member_id AS heat_member_id,
                   fo.schedule_id AS schedule_id, fo.event_id AS event_id, fo.order_no AS order_no,
                   fo.group_no AS group_no, fo.order_mode AS order_mode, fo.best_seed AS best_seed,
                   fo.best_seed_raw AS best_seed_raw, fo.best_seed_unit AS best_seed_unit,
                   fo.start_height AS start_height, fo.pass_count AS pass_count, fo.is_scratch AS is_scratch,
                   fo.is_pass AS is_pass, fo.is_locked AS is_locked, fo.locked_by AS locked_by,
                   fo.lock_time AS lock_time, fo.move_count AS move_count, fo.batch_no AS batch_no,
                   fo.remark AS remark,
                   hm.registration_id AS registration_id, hm.athlete_id AS athlete_id,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, hm.bib_no AS bib_no,
                   hm.unit_id AS unit_id, u.unit_name AS unit_name, h.heat_no AS heat_no,
                   e.event_name AS event_name
            FROM field_order fo
            LEFT JOIN heat h ON h.id = fo.heat_id AND h.deleted = 0
            LEFT JOIN heat_member hm ON hm.id = fo.heat_member_id AND hm.deleted = 0
            LEFT JOIN athlete a ON a.id = hm.athlete_id AND a.deleted = 0
            LEFT JOIN registration rg ON rg.id = hm.registration_id AND rg.deleted = 0
            LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0
            LEFT JOIN event e ON e.id = fo.event_id AND e.deleted = 0
            WHERE fo.meet_id = #{meetId} AND fo.deleted = 0
              AND fo.heat_id IN <foreach collection="heatIds" item="hid" open="(" separator="," close=")">#{hid}</foreach>
            ORDER BY fo.heat_id ASC, fo.order_no ASC, fo.id ASC
            </script>
            """)
    List<FieldOrderVO> selectFieldOrderVOs(@Param("meetId") Long meetId, @Param("heatIds") Collection<Long> heatIds);

    /**
     * 日程的全部出场顺序。
     */
    @Select("""
            SELECT fo.id AS id, fo.meet_id AS meet_id, fo.heat_id AS heat_id, fo.heat_member_id AS heat_member_id,
                   fo.schedule_id AS schedule_id, fo.event_id AS event_id, fo.order_no AS order_no,
                   fo.group_no AS group_no, fo.order_mode AS order_mode, fo.best_seed AS best_seed,
                   fo.best_seed_raw AS best_seed_raw, fo.best_seed_unit AS best_seed_unit,
                   fo.start_height AS start_height, fo.pass_count AS pass_count, fo.is_scratch AS is_scratch,
                   fo.is_pass AS is_pass, fo.is_locked AS is_locked, fo.locked_by AS locked_by,
                   fo.lock_time AS lock_time, fo.move_count AS move_count, fo.batch_no AS batch_no,
                   fo.remark AS remark,
                   hm.registration_id AS registration_id, hm.athlete_id AS athlete_id,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name, hm.bib_no AS bib_no,
                   hm.unit_id AS unit_id, u.unit_name AS unit_name, h.heat_no AS heat_no,
                   e.event_name AS event_name
            FROM field_order fo
            LEFT JOIN heat h ON h.id = fo.heat_id AND h.deleted = 0
            LEFT JOIN heat_member hm ON hm.id = fo.heat_member_id AND hm.deleted = 0
            LEFT JOIN athlete a ON a.id = hm.athlete_id AND a.deleted = 0
            LEFT JOIN registration rg ON rg.id = hm.registration_id AND rg.deleted = 0
            LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0
            LEFT JOIN event e ON e.id = fo.event_id AND e.deleted = 0
            WHERE fo.meet_id = #{meetId} AND fo.deleted = 0 AND fo.schedule_id = #{scheduleId}
            ORDER BY fo.heat_id ASC, fo.order_no ASC, fo.id ASC
            """)
    List<FieldOrderVO> selectFieldOrderVOsBySchedule(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);

    /**
     * 按分组物理删除出场顺序。
     * <p>
     * 逻辑删除会残留唯一键 {@code uk_forder_heat_order} / {@code uk_forder_heat_member} 占位行，
     * 导致重新生成出场顺序时无法复用顺序号，故重建场景统一使用物理删除。
     *
     * @param heatIds 分组 ID 集合（不可为空）
     * @return 受影响行数
     */
    @Delete("""
            <script>
            DELETE FROM field_order WHERE heat_id IN
            <foreach collection="heatIds" item="hid" open="(" separator="," close=")">#{hid}</foreach>
            </script>
            """)
    int deleteByHeatIdsPhysically(@Param("heatIds") Collection<Long> heatIds);

    /**
     * 按日程物理删除出场顺序。
     *
     * @param meetId     届次 ID
     * @param scheduleId 日程 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM field_order WHERE meet_id = #{meetId} AND schedule_id = #{scheduleId}")
    int deleteBySchedulePhysically(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);

    /**
     * 按组内人员物理删除出场顺序。
     *
     * @param heatMemberId 组内人员 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM field_order WHERE heat_member_id = #{heatMemberId}")
    int deleteByHeatMemberPhysically(@Param("heatMemberId") Long heatMemberId);
}
