package com.trackmeet.modules.result.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.result.entity.ResultAttempt;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 田赛试跳试掷 Mapper。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；自定义 SQL 自带 {@code meet_id}、{@code deleted} 条件，
 * 供名次生成装配 {@code ResultRecord.attempts}（按 {@code attempt_no} 顺序）与最好成绩回填使用。
 *
 * @author TrackMeet
 */
@Mapper
public interface ResultAttemptMapper extends BaseMapper<ResultAttempt> {

    /**
     * 按成绩 ID 查询试跳明细（按总序号升序，失败/免跳行也返回，由服务层转为 null 占位）。
     *
     * @param meetId    届次 ID
     * @param resultIds 成绩 ID 列表（不可为空）
     * @return 试跳明细列表
     */
    @Select("""
            <script>
            SELECT * FROM result_attempt
            WHERE meet_id = #{meetId} AND deleted = 0
              AND result_id IN <foreach collection="resultIds" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            ORDER BY result_id ASC, attempt_no ASC, id ASC
            </script>
            """)
    List<ResultAttempt> selectByResultIds(@Param("meetId") Long meetId,
                                         @Param("resultIds") Collection<Long> resultIds);

    /**
     * 查询某条成绩的试跳明细（按总序号升序）。
     *
     * @param meetId   届次 ID
     * @param resultId 成绩 ID
     * @return 试跳明细列表
     */
    @Select("""
            SELECT * FROM result_attempt
            WHERE meet_id = #{meetId} AND deleted = 0 AND result_id = #{resultId}
            ORDER BY attempt_no ASC, id ASC
            """)
    List<ResultAttempt> selectByResultId(@Param("meetId") Long meetId, @Param("resultId") Long resultId);

    /**
     * 清除某条成绩下的「最好一次」标记（重算最好成绩前调用）。
     *
     * @param meetId   届次 ID
     * @param resultId 成绩 ID
     * @return 受影响行数
     */
    @Update("UPDATE result_attempt SET is_best = 0 WHERE meet_id = #{meetId} AND result_id = #{resultId} "
            + "AND deleted = 0 AND is_best = 1")
    int clearBestFlag(@Param("meetId") Long meetId, @Param("resultId") Long resultId);

    /**
     * 标记某次试跳为「最好一次」。
     *
     * @param meetId    届次 ID
     * @param resultId  成绩 ID
     * @param attemptNo 试跳总序号
     * @return 受影响行数
     */
    @Update("UPDATE result_attempt SET is_best = 1 WHERE meet_id = #{meetId} AND result_id = #{resultId} "
            + "AND attempt_no = #{attemptNo} AND deleted = 0")
    int markBest(@Param("meetId") Long meetId, @Param("resultId") Long resultId,
                 @Param("attemptNo") Integer attemptNo);

    /**
     * 按成绩 ID 物理删除全部试跳明细（整表替换试跳时使用，避免残留唯一键 {@code uk_result_attempt} 占位行）。
     *
     * @param meetId   届次 ID
     * @param resultId 成绩 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM result_attempt WHERE meet_id = #{meetId} AND result_id = #{resultId}")
    int deleteByResultIdPhysically(@Param("meetId") Long meetId, @Param("resultId") Long resultId);

    /**
     * 清除某条成绩全部试跳的「最好一次」标记（按成绩 ID，用于重算前重置）。
     *
     * @param meetId   届次 ID
     * @param resultId 成绩 ID
     * @return 受影响行数
     */
    @Update("UPDATE result_attempt SET is_best = 0 WHERE meet_id = #{meetId} AND result_id = #{resultId} "
            + "AND deleted = 0")
    int clearAllBestFlags(@Param("meetId") Long meetId, @Param("resultId") Long resultId);

    /**
     * 按成绩与试跳序号回写单次成绩（修改试跳时使用）。
     *
     * @param meetId       届次 ID
     * @param id           试跳记录 ID
     * @param attemptRaw   单次成绩原文
     * @param attemptValue 单次成绩数值
     * @param isSuccess    是否成功：1 是 / 0 否
     * @param foulType     犯规类型码
     * @return 受影响行数
     */
    @Update("UPDATE result_attempt SET attempt_raw = #{attemptRaw}, attempt_value = #{attemptValue}, "
            + "is_success = #{isSuccess}, foul_type = #{foulType}, update_time = NOW() "
            + "WHERE id = #{id} AND meet_id = #{meetId} AND deleted = 0")
    int updateAttemptValue(@Param("meetId") Long meetId, @Param("id") Long id,
                           @Param("attemptRaw") String attemptRaw,
                           @Param("attemptValue") BigDecimal attemptValue,
                           @Param("isSuccess") Integer isSuccess,
                           @Param("foulType") String foulType);
}
