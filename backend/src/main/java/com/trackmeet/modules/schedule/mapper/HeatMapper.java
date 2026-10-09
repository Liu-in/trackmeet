package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.vo.HeatVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 分组 Mapper：单表 CRUD 由 MyBatis-Plus 提供，列表投影显式带 meet_id 与 deleted 条件。
 *
 * @author TrackMeet
 */
@Mapper
public interface HeatMapper extends BaseMapper<Heat> {

    /**
     * 按日程查询分组（不含人员与道次）。
     */
    @Select("""
            SELECT h.id AS id, h.meet_id AS meet_id, h.schedule_id AS schedule_id, h.heat_no AS heat_no,
                   h.heat_name AS heat_name, h.lane_count AS lane_count, h.member_count AS member_count,
                   h.seed_count AS seed_count, h.call_time AS call_time, h.call_place AS call_place,
                   h.start_time AS start_time, h.end_time AS end_time, h.status AS status,
                   h.is_locked AS is_locked, h.locked_by AS locked_by, h.lock_time AS lock_time,
                   h.source_type AS source_type, h.batch_no AS batch_no, h.draw_mode AS draw_mode, h.remark AS remark
            FROM heat h
            WHERE h.meet_id = #{meetId} AND h.deleted = 0 AND h.schedule_id = #{scheduleId}
            ORDER BY h.heat_no ASC, h.id ASC
            """)
    List<HeatVO> selectHeatVOs(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);

    /**
     * 按分组 ID 批量查询分组（不含人员与道次）。
     */
    @Select("""
            <script>
            SELECT h.id AS id, h.meet_id AS meet_id, h.schedule_id AS schedule_id, h.heat_no AS heat_no,
                   h.heat_name AS heat_name, h.lane_count AS lane_count, h.member_count AS member_count,
                   h.seed_count AS seed_count, h.call_time AS call_time, h.call_place AS call_place,
                   h.start_time AS start_time, h.end_time AS end_time, h.status AS status,
                   h.is_locked AS is_locked, h.locked_by AS locked_by, h.lock_time AS lock_time,
                   h.source_type AS source_type, h.batch_no AS batch_no, h.draw_mode AS draw_mode, h.remark AS remark
            FROM heat h
            WHERE h.meet_id = #{meetId} AND h.deleted = 0
              AND h.id IN <foreach collection="heatIds" item="hid" open="(" separator="," close=")">#{hid}</foreach>
            ORDER BY h.schedule_id ASC, h.heat_no ASC, h.id ASC
            </script>
            """)
    List<HeatVO> selectHeatVOsByIds(@Param("meetId") Long meetId, @Param("heatIds") Collection<Long> heatIds);

    /**
     * 按主键物理删除分组行。
     * <p>
     * {@code heat} 上有唯一键 {@code uk_heat_schedule_no(schedule_id, heat_no)}：
     * 逻辑删除（{@code @TableLogic}）会把行留在表内并继续占用唯一键，
     * 导致「删除后以相同组号重新生成分组」触发唯一键冲突。重新生成分组属重建语义，
     * 因此提供物理删除入口（调用方需先清理 {@code heat_member / lane / field_order}）。
     *
     * @param id 分组 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM heat WHERE id = #{id}")
    int deleteByIdPhysically(@Param("id") Long id);

    /**
     * 按日程物理删除全部分组行（删除日程时级联清理）。
     *
     * @param meetId     届次 ID
     * @param scheduleId 日程 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM heat WHERE meet_id = #{meetId} AND schedule_id = #{scheduleId}")
    int deleteBySchedulePhysically(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);
}
