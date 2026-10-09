package com.trackmeet.modules.print.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.print.dto.PrintTemplateQuery;
import com.trackmeet.modules.print.dto.PrintTemplateSaveDTO;
import com.trackmeet.modules.print.entity.PrintTemplate;
import com.trackmeet.modules.print.vo.PrintTemplateVO;

/**
 * 打印模板服务：模板增删改查与「生效模板」解析。
 * <p>
 * 模板生效规则（渲染顺序）：
 * <ol>
 *     <li>显式 {@code templateId}（校验届次归属，且可命中已停用模板，便于按历史模板重打）；</li>
 *     <li>该 {@code report_type} 的 {@code is_default = 1} 且启用的模板；</li>
 *     <li>都没有时返回 {@code null}，由渲染编排使用内置默认（A4 纵向、基准字号 9、显示页码与打印时间、
 *         签名区「记录员 / 裁判长 / 技术代表」）。</li>
 * </ol>
 *
 * @author TrackMeet
 */
public interface PrintTemplateService extends IService<PrintTemplate> {

    /**
     * 模板分页查询（条件：reportType / status / isDefault / keyword）。
     *
     * @param query 查询条件（meetId 为空时取当前届次上下文）
     * @return 分页结果
     */
    PageResult<PrintTemplateVO> pageTemplates(PrintTemplateQuery query);

    /**
     * 模板详情（做届次越权校验）。
     *
     * @param id 模板 ID
     * @return 模板 VO
     */
    PrintTemplateVO getTemplate(Long id);

    /**
     * 某届次某报表类型的默认模板（不存在时返回 {@code null}，不抛异常）。
     *
     * @param meetId     届次 ID（为空时取当前届次上下文）
     * @param reportType 模板报表类型（PROGRAM / START_LIST / RESULT / POINTS / MEDAL / CERT）
     * @return 默认模板 VO 或 {@code null}
     */
    PrintTemplateVO getDefaultTemplate(Long meetId, String reportType);

    /**
     * 新增模板（模板编码届次内唯一；{@code isDefault = 1} 时先清除同报表类型的旧默认）。
     *
     * @param dto 新增入参
     * @return 新模板 ID
     */
    Long create(PrintTemplateSaveDTO dto);

    /**
     * 修改模板（编码唯一性校验；{@code isDefault = 1} 时先清除同报表类型的旧默认）。
     *
     * @param id  模板 ID
     * @param dto 修改入参
     */
    void update(Long id, PrintTemplateSaveDTO dto);

    /**
     * 删除模板（逻辑删除）：先把 {@code template_code} 改写为「原值_d{id}」释放唯一键，再 {@code removeById}。
     *
     * @param id 模板 ID
     */
    void delete(Long id);

    /**
     * 解析生效模板（供 PDF / Excel 渲染编排使用）。
     *
     * @param meetId             届次 ID
     * @param templateId         显式模板 ID（可空）
     * @param templateReportType 模板报表类型（可空表示不解析默认模板）
     * @return 生效模板实体或 {@code null}（表示使用内置默认版式）
     */
    PrintTemplate resolveTemplate(Long meetId, Long templateId, String templateReportType);

    /**
     * 实体 → VO。
     *
     * @param entity 模板实体
     * @return 模板 VO
     */
    PrintTemplateVO toVO(PrintTemplate entity);
}
