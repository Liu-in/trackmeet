package com.trackmeet.modules.print.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.print.entity.PrintLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打印留痕 Mapper：分页查询打印记录。
 * <p>
 * 设计说明：
 * <ol>
 *     <li>{@code sys_print_log} 为系统表（无 {@code meet_id} 列，多租户拦截器按 {@code sys_} 前缀忽略），
 *         届次以弱引用列 {@code meet_ref_id} 记录，因此自定义 SQL 自带 {@code deleted = 0} 与
 *         {@code meet_ref_id} 过滤条件；</li>
 *     <li>全部条件采用「{@code #{param} IS NULL OR 列 = #{param}}」的静态写法（不使用动态 SQL 标签），
 *         参数显式声明 {@code jdbcType}，避免空值类型推断问题，同时保证 SQL 完全可预测；</li>
 *     <li>查询列使用「列名 AS 驼峰」别名，与 {@code PrintMapper} 的既有约定一致。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Mapper
public interface PrintLogMapper extends BaseMapper<PrintLog> {

    /** 全列投影（列名 → 驼峰别名） */
    String COLUMNS = "id, report_type AS reportType, report_name AS reportName, params_json AS paramsJson, "
            + "format, meet_ref_id AS meetRefId, file_id AS fileId, template_id AS templateId, "
            + "operator_id AS operatorId, operator_name AS operatorName, print_count AS printCount, "
            + "print_device AS printDevice, ip, status, error_msg AS errorMsg, print_time AS printTime, "
            + "create_by AS createBy, create_time AS createTime, update_by AS updateBy, update_time AS updateTime, "
            + "deleted, version";

    /**
     * 打印留痕分页查询（按打印时间倒序，物理分页）。
     *
     * @param reportType 留痕报表类型（可空，不过滤）
     * @param format     输出格式（可空，不过滤）
     * @param status     状态 SUCCESS / FAILED（可空，不过滤）
     * @param meetRefId  届次弱引用（可空，不过滤）
     * @param beginTime  打印时间起（含，可空）
     * @param endTime    打印时间止（含，可空）
     * @param keyword    关键字（匹配报表名称或操作人姓名，可空）
     * @param size       每页条数
     * @param offset     偏移量
     * @return 留痕记录列表
     */
    @Select("SELECT " + COLUMNS + " FROM sys_print_log WHERE deleted = 0 "
            + "AND (#{reportType,jdbcType=VARCHAR} IS NULL OR report_type = #{reportType,jdbcType=VARCHAR}) "
            + "AND (#{format,jdbcType=VARCHAR} IS NULL OR format = #{format,jdbcType=VARCHAR}) "
            + "AND (#{status,jdbcType=VARCHAR} IS NULL OR status = #{status,jdbcType=VARCHAR}) "
            + "AND (#{meetRefId,jdbcType=BIGINT} IS NULL OR meet_ref_id = #{meetRefId,jdbcType=BIGINT}) "
            + "AND (#{beginTime,jdbcType=TIMESTAMP} IS NULL OR print_time >= #{beginTime,jdbcType=TIMESTAMP}) "
            + "AND (#{endTime,jdbcType=TIMESTAMP} IS NULL OR print_time <= #{endTime,jdbcType=TIMESTAMP}) "
            + "AND (#{keyword,jdbcType=VARCHAR} IS NULL "
            + "OR report_name LIKE CONCAT('%', #{keyword,jdbcType=VARCHAR}, '%') "
            + "OR operator_name LIKE CONCAT('%', #{keyword,jdbcType=VARCHAR}, '%')) "
            + "ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<PrintLog> selectLogPage(@Param("reportType") String reportType,
                                 @Param("format") String format,
                                 @Param("status") String status,
                                 @Param("meetRefId") Long meetRefId,
                                 @Param("beginTime") LocalDateTime beginTime,
                                 @Param("endTime") LocalDateTime endTime,
                                 @Param("keyword") String keyword,
                                 @Param("size") long size,
                                 @Param("offset") long offset);

    /**
     * 打印留痕总数（条件与 {@link #selectLogPage} 完全一致）。
     *
     * @param reportType 留痕报表类型（可空）
     * @param format     输出格式（可空）
     * @param status     状态（可空）
     * @param meetRefId  届次弱引用（可空）
     * @param beginTime  打印时间起（含，可空）
     * @param endTime    打印时间止（含，可空）
     * @param keyword    关键字（可空）
     * @return 记录总数
     */
    @Select("SELECT COUNT(1) FROM sys_print_log WHERE deleted = 0 "
            + "AND (#{reportType,jdbcType=VARCHAR} IS NULL OR report_type = #{reportType,jdbcType=VARCHAR}) "
            + "AND (#{format,jdbcType=VARCHAR} IS NULL OR format = #{format,jdbcType=VARCHAR}) "
            + "AND (#{status,jdbcType=VARCHAR} IS NULL OR status = #{status,jdbcType=VARCHAR}) "
            + "AND (#{meetRefId,jdbcType=BIGINT} IS NULL OR meet_ref_id = #{meetRefId,jdbcType=BIGINT}) "
            + "AND (#{beginTime,jdbcType=TIMESTAMP} IS NULL OR print_time >= #{beginTime,jdbcType=TIMESTAMP}) "
            + "AND (#{endTime,jdbcType=TIMESTAMP} IS NULL OR print_time <= #{endTime,jdbcType=TIMESTAMP}) "
            + "AND (#{keyword,jdbcType=VARCHAR} IS NULL "
            + "OR report_name LIKE CONCAT('%', #{keyword,jdbcType=VARCHAR}, '%') "
            + "OR operator_name LIKE CONCAT('%', #{keyword,jdbcType=VARCHAR}, '%'))")
    long countLogs(@Param("reportType") String reportType,
                   @Param("format") String format,
                   @Param("status") String status,
                   @Param("meetRefId") Long meetRefId,
                   @Param("beginTime") LocalDateTime beginTime,
                   @Param("endTime") LocalDateTime endTime,
                   @Param("keyword") String keyword);
}
