package com.trackmeet.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.vo.AthleteEntryRowVO;
import com.trackmeet.modules.schedule.vo.HeatMemberVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 组内人员 Mapper：单表 CRUD 由 MyBatis-Plus 提供；
 * 种子装配行与人员列表投影显式带 meet_id 与 deleted 条件（跨模块取数使用投影 VO）。
 *
 * @author TrackMeet
 */
@Mapper
public interface HeatMemberMapper extends BaseMapper<HeatMember> {

    /**
     * 分组 / 田赛顺序的种子装配行：报名 × 运动员 × 单位。
     * 排序规则：有成绩的按成绩升序（径赛用时越小越好、田赛成绩越大越好在服务层反转），
     * 无成绩的按 bib_no 兜底。
     */
    @Select("""
            SELECT rg.id AS registration_id, rg.athlete_id AS athlete_id,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name,
                   rg.unit_id AS unit_id, u.unit_name AS unit_name, a.bib_no AS bib_no,
                   rg.seed_value AS seed_value, rg.seed_result AS seed_result_raw,
                   rg.seed_result_unit AS seed_result_unit, rg.seed_source AS seed_source,
                   rg.is_relay AS is_relay, rg.group_id AS group_id, rg.relay_name AS relay_name
            FROM registration rg
            LEFT JOIN athlete a ON a.id = rg.athlete_id AND a.deleted = 0
            LEFT JOIN unit u ON u.id = rg.unit_id AND u.deleted = 0
            WHERE rg.meet_id = #{meetId} AND rg.deleted = 0 AND rg.status = 'APPROVED' AND rg.event_id = #{eventId}
            ORDER BY (rg.seed_value IS NULL) ASC, rg.seed_value ASC, a.bib_no ASC, rg.id ASC
            """)
    List<AthleteEntryRowVO> selectSeedEntries(@Param("meetId") Long meetId, @Param("eventId") Long eventId);

    /**
     * 组内人员列表（含运动员姓名 / 单位名称）。
     */
    @Select("""
            <script>
            SELECT hm.id AS id, hm.meet_id AS meet_id, hm.heat_id AS heat_id, hm.schedule_id AS schedule_id,
                   hm.registration_id AS registration_id, hm.athlete_id AS athlete_id,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name,
                   hm.unit_id AS unit_id, u.unit_name AS unit_name, hm.bib_no AS bib_no,
                   hm.group_id AS group_id, hm.order_no AS order_no, hm.is_seed AS is_seed,
                   hm.is_scratch AS is_scratch, hm.status AS status, hm.seed_result AS seed_result,
                   hm.seed_result_raw AS seed_result_raw, hm.seed_result_unit AS seed_result_unit,
                   hm.lane_no AS lane_no, hm.is_locked AS is_locked, hm.lock_time AS lock_time,
                   hm.batch_no AS batch_no, hm.remark AS remark
            FROM heat_member hm
            LEFT JOIN athlete a ON a.id = hm.athlete_id AND a.deleted = 0
            LEFT JOIN registration rg ON rg.id = hm.registration_id AND rg.deleted = 0
            LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0
            WHERE hm.meet_id = #{meetId} AND hm.deleted = 0
              AND hm.heat_id IN <foreach collection="heatIds" item="hid" open="(" separator="," close=")">#{hid}</foreach>
            ORDER BY hm.heat_id ASC, hm.order_no ASC, hm.id ASC
            </script>
            """)
    List<HeatMemberVO> selectMemberVOs(@Param("meetId") Long meetId, @Param("heatIds") Collection<Long> heatIds);

    /**
     * 日程下全部组内人员（按日程装配检录 / 冲突数据）。
     */
    @Select("""
            SELECT hm.id AS id, hm.meet_id AS meet_id, hm.heat_id AS heat_id, hm.schedule_id AS schedule_id,
                   hm.registration_id AS registration_id, hm.athlete_id AS athlete_id,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name,
                   hm.unit_id AS unit_id, u.unit_name AS unit_name, hm.bib_no AS bib_no,
                   hm.group_id AS group_id, hm.order_no AS order_no, hm.is_seed AS is_seed,
                   hm.is_scratch AS is_scratch, hm.status AS status, hm.seed_result AS seed_result,
                   hm.seed_result_raw AS seed_result_raw, hm.seed_result_unit AS seed_result_unit,
                   hm.lane_no AS lane_no, hm.is_locked AS is_locked, hm.lock_time AS lock_time,
                   hm.batch_no AS batch_no, hm.remark AS remark
            FROM heat_member hm
            LEFT JOIN athlete a ON a.id = hm.athlete_id AND a.deleted = 0
            LEFT JOIN registration rg ON rg.id = hm.registration_id AND rg.deleted = 0
            LEFT JOIN unit u ON u.id = hm.unit_id AND u.deleted = 0
            WHERE hm.meet_id = #{meetId} AND hm.deleted = 0 AND hm.schedule_id = #{scheduleId}
            ORDER BY hm.heat_id ASC, hm.order_no ASC, hm.id ASC
            """)
    List<HeatMemberVO> selectMemberVOsBySchedule(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);

    /**
     * 按分组物理删除组内人员。
     * <p>
     * 逻辑删除会残留唯一键 {@code uk_hm_heat_order} / {@code uk_hm_heat_registration} 占位行，
     * 导致重新生成分组时无法复用组号与顺序号，故重建场景统一使用物理删除。
     *
     * @param heatIds 分组 ID 集合（不可为空）
     * @return 受影响行数
     */
    @Delete("""
            <script>
            DELETE FROM heat_member WHERE heat_id IN
            <foreach collection="heatIds" item="hid" open="(" separator="," close=")">#{hid}</foreach>
            </script>
            """)
    int deleteByHeatIdsPhysically(@Param("heatIds") Collection<Long> heatIds);

    /**
     * 按日程物理删除组内人员。
     *
     * @param meetId     届次 ID
     * @param scheduleId 日程 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM heat_member WHERE meet_id = #{meetId} AND schedule_id = #{scheduleId}")
    int deleteBySchedulePhysically(@Param("meetId") Long meetId, @Param("scheduleId") Long scheduleId);

    /**
     * 按主键物理删除单条组内人员。
     *
     * @param id 组内人员 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM heat_member WHERE id = #{id}")
    int deleteByIdPhysically(@Param("id") Long id);
}
