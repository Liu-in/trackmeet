package com.trackmeet.modules.print.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackmeet.modules.print.entity.PrintTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 打印模板 Mapper：模板的默认模板解析、唯一键校验与唯一键释放。
 * <p>
 * 设计说明：
 * <ol>
 *     <li><b>自定义 SQL 自带租户与逻辑删除条件</b>：所有语句显式带上 {@code deleted = 0} 与
 *         {@code meet_id = #{meetId}}，不依赖多租户拦截器（未携带 {@code X-Meet-Id} 时拦截器不追加条件）；</li>
 *     <li>所有查询列使用「列名 AS 驼峰」别名，与 {@code PrintMapper} 的既有约定一致；</li>
 *     <li>删除时的唯一键改写（{@code template_code -> 原值_d{id}}）也走本 Mapper，保证同样带租户与删除条件。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Mapper
public interface PrintTemplateMapper extends BaseMapper<PrintTemplate> {

    /** 全列投影（列名 → 驼峰别名） */
    String COLUMNS = "id, meet_id AS meetId, template_code AS templateCode, template_name AS templateName, "
            + "report_type AS reportType, header_html AS headerHtml, footer_html AS footerHtml, "
            + "logo_file_id AS logoFileId, signature_html AS signatureHtml, page_size AS pageSize, "
            + "orientation, font_size AS fontSize, show_page_no AS showPageNo, show_print_time AS showPrintTime, "
            + "is_default AS isDefault, extra_json AS extraJson, status, remark, "
            + "create_by AS createBy, create_time AS createTime, update_by AS updateBy, update_time AS updateTime, "
            + "deleted, version";

    /**
     * 查询某届次某报表类型的默认模板（启用状态，最早创建优先）。
     *
     * @param meetId     届次 ID
     * @param reportType 模板报表类型（PROGRAM / START_LIST / RESULT / POINTS / MEDAL / CERT）
     * @return 默认模板，不存在时返回 {@code null}
     */
    @Select("SELECT " + COLUMNS + " FROM print_template "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND report_type = #{reportType} "
            + "AND is_default = 1 AND (status IS NULL OR status = 'ENABLED') "
            + "ORDER BY id ASC LIMIT 1")
    PrintTemplate selectDefaultTemplate(@Param("meetId") Long meetId, @Param("reportType") String reportType);

    /**
     * 按主键查询模板（自带租户与逻辑删除条件）。
     *
     * @param meetId 届次 ID
     * @param id     模板 ID
     * @return 模板，不存在时返回 {@code null}
     */
    @Select("SELECT " + COLUMNS + " FROM print_template "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND id = #{id}")
    PrintTemplate selectTemplateById(@Param("meetId") Long meetId, @Param("id") Long id);

    /**
     * 按模板编码查询（用于 {@code uk_meet_template_code} 唯一性预校验）。
     *
     * @param meetId       届次 ID
     * @param templateCode 模板编码
     * @return 模板，不存在时返回 {@code null}
     */
    @Select("SELECT " + COLUMNS + " FROM print_template "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND template_code = #{templateCode} "
            + "ORDER BY id ASC LIMIT 1")
    PrintTemplate selectByTemplateCode(@Param("meetId") Long meetId,
                                       @Param("templateCode") String templateCode);

    /**
     * 清除某届次某报表类型的默认标记（设置新默认模板前调用，保证「同一 report_type 至多一个默认」）。
     *
     * @param meetId     届次 ID
     * @param reportType 模板报表类型
     * @return 受影响行数
     */
    @Update("UPDATE print_template SET is_default = 0 "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND report_type = #{reportType} AND is_default = 1")
    int clearDefaultFlag(@Param("meetId") Long meetId, @Param("reportType") String reportType);

    /**
     * 逻辑删除前释放唯一键：把 {@code template_code} 改写为「新值」（调用方按 {@code 原值_d{id}} 生成）。
     *
     * @param meetId       届次 ID
     * @param id           模板 ID
     * @param templateCode 改写后的模板编码
     * @return 受影响行数
     */
    @Update("UPDATE print_template SET template_code = #{templateCode} "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND id = #{id}")
    int rewriteTemplateCode(@Param("meetId") Long meetId, @Param("id") Long id,
                            @Param("templateCode") String templateCode);
}
