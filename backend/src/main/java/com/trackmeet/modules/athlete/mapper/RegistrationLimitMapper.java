package com.trackmeet.modules.athlete.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.athlete.entity.RegistrationLimit;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 报名限制计数 Mapper。
 * <p>
 * 计数表为派生数据，存在唯一键 {@code UK(meet_id, unit_id, event_id)}；
 * 逻辑删除会残留占位行导致无法重新插入，故刷新采用「物理删除 + 重新插入」的幂等写法。
 *
 * @author TrackMeet
 */
@Mapper
public interface RegistrationLimitMapper extends BaseMapper<RegistrationLimit> {

    /**
     * 物理删除「单位 × 项目」计数行。
     */
    @Delete("DELETE FROM registration_limit WHERE meet_id = #{meetId} AND unit_id = #{unitId} AND event_id = #{eventId}")
    int deletePhysicalByKey(@Param("meetId") Long meetId,
                            @Param("unitId") Long unitId,
                            @Param("eventId") Long eventId);

    /**
     * 查询「单位 × 项目」计数行（未删除）。
     */
    @Select("SELECT id, meet_id, unit_id, event_id, group_id, registered_count, max_count, "
            + "relay_registered_count, relay_max_count, remain_count, is_full, over_limit, "
            + "athlete_event_count_json, limit_source, limit_check_time, remark, create_by, create_time, "
            + "update_by, update_time, deleted, version "
            + "FROM registration_limit "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND unit_id = #{unitId} AND event_id = #{eventId} LIMIT 1")
    RegistrationLimit selectByKey(@Param("meetId") Long meetId,
                                  @Param("unitId") Long unitId,
                                  @Param("eventId") Long eventId);
}
