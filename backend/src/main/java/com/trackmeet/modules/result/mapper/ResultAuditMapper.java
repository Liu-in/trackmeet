package com.trackmeet.modules.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.result.entity.ResultAudit;
import com.trackmeet.modules.result.vo.ResultAuditVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成绩留痕 Mapper。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；留痕视图投影自带 {@code meet_id}、{@code deleted} 条件，
 * 关联项目 / 轮次 / 运动员 / 单位名称用于留痕列表直接展示，不引用其它模块实体。
 *
 * @author TrackMeet
 */
@Mapper
public interface ResultAuditMapper extends BaseMapper<ResultAudit> {

    /**
     * 按成绩 ID 查询留痕链（操作时间倒序，最新在前）。
     *
     * @param meetId   届次 ID
     * @param resultId 成绩 ID
     * @return 留痕视图列表
     */
    @Select("""
            SELECT au.id AS id, au.meet_id AS meet_id, au.result_id AS result_id,
                   au.event_id AS event_id, au.round_id AS round_id,
                   au.registration_id AS registration_id, au.action AS action,
                   au.before_json AS before_json, au.after_json AS after_json,
                   au.reason AS reason, au.operator_id AS operator_id,
                   au.operator_name AS operator_name, au.operate_time AS operate_time,
                   au.ip AS ip, au.remark AS remark, au.create_time AS create_time,
                   e.event_name AS event_name, rd.round_name AS round_name,
                   COALESCE(a.athlete_name, rg.relay_name) AS athlete_name,
                   a.bib_no AS bib_no, u.unit_name AS unit_name
            FROM result_audit au
            LEFT JOIN event e ON e.id = au.event_id AND e.deleted = 0
            LEFT JOIN event_round rd ON rd.id = au.round_id AND rd.deleted = 0
            LEFT JOIN registration rg ON rg.id = au.registration_id AND rg.deleted = 0
            LEFT JOIN athlete a ON a.id = rg.athlete_id AND a.deleted = 0
            LEFT JOIN unit u ON u.id = rg.unit_id AND u.deleted = 0
            WHERE au.meet_id = #{meetId} AND au.deleted = 0 AND au.result_id = #{resultId}
            ORDER BY au.operate_time DESC, au.id DESC
            """)
    List<ResultAuditVO> selectAuditVOs(@Param("meetId") Long meetId, @Param("resultId") Long resultId);
}
