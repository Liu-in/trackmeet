package com.trackmeet.modules.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.result.entity.WindReading;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 风速读数 Mapper。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；自定义 SQL 自带 {@code meet_id}、{@code deleted} 条件。
 * 表上唯一键 {@code uk_result (result_id)} 决定「一成绩一条读数」，
 * 服务层按 {@code result_id} 先查后改（存在则更新、不存在则新增），避免重复键冲突。
 *
 * @author TrackMeet
 */
@Mapper
public interface WindReadingMapper extends BaseMapper<WindReading> {

    /**
     * 按成绩 ID 查询风速读数。
     *
     * @param meetId   届次 ID
     * @param resultId 成绩 ID
     * @return 风速读数，不存在时返回 null
     */
    @Select("SELECT * FROM wind_reading WHERE meet_id = #{meetId} AND deleted = 0 "
            + "AND result_id = #{resultId} ORDER BY id ASC LIMIT 1")
    WindReading selectByResultId(@Param("meetId") Long meetId, @Param("resultId") Long resultId);
}
