package com.trackmeet.modules.athlete.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.athlete.entity.AthleteEventCount;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 每人项数计数 Mapper。
 * <p>
 * 计数表为派生数据，存在唯一键 {@code UK(athlete_id)}；
 * 逻辑删除会残留占位行导致无法重新插入，故刷新采用「物理删除 + 重新插入」的幂等写法。
 *
 * @author TrackMeet
 */
@Mapper
public interface AthleteEventCountMapper extends BaseMapper<AthleteEventCount> {

    /**
     * 物理删除某运动员的项数计数行。
     */
    @Delete("DELETE FROM athlete_event_count WHERE meet_id = #{meetId} AND athlete_id = #{athleteId}")
    int deletePhysicalByKey(@Param("meetId") Long meetId, @Param("athleteId") Long athleteId);

    /**
     * 查询某运动员的项数计数行（未删除）。
     */
    @Select("SELECT id, meet_id, athlete_id, unit_id, group_id, event_count, individual_event_count, "
            + "relay_event_count, allround_event_count, max_events, remain_count, is_relay_counted, over_limit, "
            + "event_ids_json, count_source, last_register_time, check_time, create_by, create_time, "
            + "update_by, update_time, deleted, version "
            + "FROM athlete_event_count WHERE deleted = 0 AND meet_id = #{meetId} AND athlete_id = #{athleteId} LIMIT 1")
    AthleteEventCount selectByKey(@Param("meetId") Long meetId, @Param("athleteId") Long athleteId);
}
