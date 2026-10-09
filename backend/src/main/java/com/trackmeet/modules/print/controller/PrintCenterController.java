package com.trackmeet.modules.print.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.common.util.RequestUtils;
import com.trackmeet.modules.print.constant.PrintReportType;
import com.trackmeet.modules.print.dto.PdfReportQuery;
import com.trackmeet.modules.print.dto.PrintLogQuery;
import com.trackmeet.modules.print.dto.PrintLogSaveDTO;
import com.trackmeet.modules.print.dto.PrintTemplateQuery;
import com.trackmeet.modules.print.dto.PrintTemplateSaveDTO;
import com.trackmeet.modules.print.pdf.PdfCapability;
import com.trackmeet.modules.print.service.PrintLogService;
import com.trackmeet.modules.print.service.PrintTemplateService;
import com.trackmeet.modules.print.service.ReportRenderService;
import com.trackmeet.modules.print.vo.PrintLogVO;
import com.trackmeet.modules.print.vo.PrintTemplateVO;
import com.trackmeet.modules.print.vo.ReportMetaVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 打印中心接口：PDF 输出、统一 Excel 导出、打印模板配置与打印留痕。
 * <p>
 * 路径全部位于 {@code /api/print} 下，与既有 {@code PrintController}（{@code /order-book}、{@code /heats}、
 * {@code /lanes}、{@code /calling-sheets}）和 {@code StandingPrintController}（{@code /results}、
 * {@code /standings/team}、{@code /points}）<b>不重叠</b>：本类只占用 {@code /pdf/**}、{@code /excel/{report}}、
 * {@code /templates/**}、{@code /logs}、{@code /reports}。
 * <p>
 * 权限编码（均已在 {@code sql/05-init-data.sql} 初始化）：
 * <ul>
 *     <li>{@code GET /pdf/capability} → {@code print:center}；</li>
 *     <li>{@code GET /pdf/order-book}、{@code /pdf/lanes} → {@code print:orderbook}；</li>
 *     <li>{@code GET /pdf/results} → {@code print:results}；{@code /pdf/calling} → {@code print:calling}；</li>
 *     <li>{@code GET /pdf/points}、{@code /pdf/medals} → {@code print:standings}；</li>
 *     <li>{@code GET /pdf/certificates} → {@code certificate:export}；</li>
 *     <li>{@code GET /excel/{report}} → {@code print:batch}；</li>
 *     <li>模板：{@code print:template:list} / {@code print:template:add} / {@code print:template:edit}；</li>
 *     <li>留痕与报表清单：{@code print:center}。</li>
 * </ul>
 * 导出与写操作记 {@code @OpLog(module = "PRINT")}：导出 {@code action=EXPORT}，模板 {@code action=TEMPLATE}，
 * 留痕 {@code action=LOG}。
 *
 * @author TrackMeet
 */
@Slf4j
@RestController
@RequestMapping("/api/print")
@RequiredArgsConstructor
@Validated
public class PrintCenterController {

    private final ReportRenderService reportRenderService;
    private final PrintTemplateService printTemplateService;
    private final PrintLogService printLogService;

    // ==================================================================================
    // 一、PDF 能力探测
    // ==================================================================================

    /**
     * PDF 生成能力：前端据此决定是否启用 PDF 按钮（中文字体缺失时给出配置提示）。
     *
     * @return 能力信息（{@code available} / {@code fontPath} / {@code message} / {@code candidates}）
     */
    @GetMapping("/pdf/capability")
    @PreAuthorize("@perm.has('print:center')")
    public R<PdfCapability> pdfCapability() {
        return R.ok(reportRenderService.capability());
    }

    // ==================================================================================
    // 二、PDF 输出（直接写响应流）
    // ==================================================================================

    /**
     * 秩序册 PDF：总日程表 + 分组表 + 分道表 + 检录表（多节）。
     *
     * @param query    查询条件（meetId / sessionId / groupId / eventId / unitId / publishOnly / templateId）
     * @param request  HTTP 请求（客户端 IP 写入留痕）
     * @param response HTTP 响应（{@code application/pdf} 附件流）
     */
    @GetMapping("/pdf/order-book")
    @PreAuthorize("@perm.has('print:orderbook')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "打印秩序册 PDF", targetType = "print_log")
    public void pdfOrderBook(@Valid PdfReportQuery query, HttpServletRequest request,
                             HttpServletResponse response) {
        reportRenderService.renderPdf(PrintReportType.ORDER_BOOK, query, request, response);
    }

    /**
     * 成绩公告 / 成绩册 PDF：按「项目 × 轮次」多节输出，含纪录标记。
     *
     * @param query    查询条件（eventId / groupId / unitId / templateId）
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/pdf/results")
    @PreAuthorize("@perm.has('print:results')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "打印成绩公告 PDF", targetType = "print_log")
    public void pdfResults(@Valid PdfReportQuery query, HttpServletRequest request,
                           HttpServletResponse response) {
        reportRenderService.renderPdf(PrintReportType.RESULTS, query, request, response);
    }

    /**
     * 检录表 PDF。
     *
     * @param query    查询条件（sessionId / eventId / unitId / templateId）
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/pdf/calling")
    @PreAuthorize("@perm.has('print:calling')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "打印检录表 PDF", targetType = "print_log")
    public void pdfCalling(@Valid PdfReportQuery query, HttpServletRequest request,
                           HttpServletResponse response) {
        reportRenderService.renderPdf(PrintReportType.CALLING, query, request, response);
    }

    /**
     * 分道表 PDF（径赛 / 接力按分组逐组输出，空道补占位行）。
     *
     * @param query    查询条件
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/pdf/lanes")
    @PreAuthorize("@perm.has('print:orderbook')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "打印分道表 PDF", targetType = "print_log")
    public void pdfLanes(@Valid PdfReportQuery query, HttpServletRequest request,
                         HttpServletResponse response) {
        reportRenderService.renderPdf(PrintReportType.LANES, query, request, response);
    }

    /**
     * 积分表 PDF（团体总分：男子 / 女子 / 总团体）。
     *
     * @param query    查询条件（groupId / scoreType / templateId）
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/pdf/points")
    @PreAuthorize("@perm.has('print:standings')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "打印积分表 PDF", targetType = "print_log")
    public void pdfPoints(@Valid PdfReportQuery query, HttpServletRequest request,
                          HttpServletResponse response) {
        reportRenderService.renderPdf(PrintReportType.POINTS, query, request, response);
    }

    /**
     * 奖牌榜 PDF。
     *
     * @param query    查询条件（groupId / templateId）
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/pdf/medals")
    @PreAuthorize("@perm.has('print:standings')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "打印奖牌榜 PDF", targetType = "print_log")
    public void pdfMedals(@Valid PdfReportQuery query, HttpServletRequest request,
                          HttpServletResponse response) {
        reportRenderService.renderPdf(PrintReportType.MEDALS, query, request, response);
    }

    /**
     * 证书批量 PDF：每张证书一页（编号 / 姓名 / 单位 / 项目 / 名次 / 成绩 / 日期 / 二维码文本）。
     *
     * @param query    查询条件（certType / unitId / templateId）
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @GetMapping("/pdf/certificates")
    @PreAuthorize("@perm.has('certificate:export')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "批量打印证书 PDF", targetType = "print_log")
    public void pdfCertificates(@Valid PdfReportQuery query, HttpServletRequest request,
                                HttpServletResponse response) {
        reportRenderService.renderPdf(PrintReportType.CERTIFICATES, query, request, response);
    }

    // ==================================================================================
    // 三、统一 Excel 导出
    // ==================================================================================

    /**
     * 统一 Excel 导出（多 Sheet）：{@code report} ∈
     * {@code order-book | results | calling | lanes | points | medals | certificates}，
     * 文件名 {@code 报表名_届次_yyyyMMdd.xlsx}。
     *
     * @param report   报表类型（URL 键）
     * @param query    查询条件
     * @param request  HTTP 请求
     * @param response HTTP 响应（xlsx 附件流）
     */
    @GetMapping("/excel/{report}")
    @PreAuthorize("@perm.has('print:batch')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "统一导出 Excel", targetType = "print_log")
    public void excel(@PathVariable("report") String report, @Valid PdfReportQuery query,
                      HttpServletRequest request, HttpServletResponse response) {
        reportRenderService.renderExcel(PrintReportType.of(report), query, request, response);
    }

    // ==================================================================================
    // 四、打印模板
    // ==================================================================================

    /**
     * 模板分页列表（条件：reportType / status / isDefault / keyword）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/templates")
    @PreAuthorize("@perm.has('print:template:list')")
    public R<PageResult<PrintTemplateVO>> pageTemplates(@Valid PrintTemplateQuery query) {
        return R.ok(printTemplateService.pageTemplates(query));
    }

    /**
     * 默认模板（某报表类型；不存在时 {@code data} 为 {@code null}）。
     *
     * @param reportType 报表类型（PROGRAM / START_LIST / RESULT / POINTS / MEDAL / CERT）
     * @param meetId     届次 ID（可空，默认当前届次上下文）
     * @return 默认模板
     */
    @GetMapping("/templates/default")
    @PreAuthorize("@perm.has('print:template:list')")
    public R<PrintTemplateVO> defaultTemplate(@RequestParam("reportType") String reportType,
                                              @RequestParam(value = "meetId", required = false) Long meetId) {
        return R.ok(printTemplateService.getDefaultTemplate(meetId, reportType));
    }

    /**
     * 模板详情。
     *
     * @param id 模板 ID
     * @return 模板
     */
    @GetMapping("/templates/{id}")
    @PreAuthorize("@perm.has('print:template:list')")
    public R<PrintTemplateVO> getTemplate(@PathVariable("id") Long id) {
        return R.ok(printTemplateService.getTemplate(id));
    }

    /**
     * 新增模板（{@code isDefault = 1} 时同一 reportType 的旧默认自动置 0）。
     *
     * @param dto 模板入参
     * @return 新模板 ID
     */
    @PostMapping("/templates")
    @PreAuthorize("@perm.has('print:template:add')")
    @OpLog(module = "PRINT", action = "TEMPLATE", actionName = "新增打印模板", targetType = "print_template")
    public R<Long> addTemplate(@Valid @RequestBody PrintTemplateSaveDTO dto) {
        return R.ok(printTemplateService.create(dto));
    }

    /**
     * 修改模板。
     *
     * @param id  模板 ID
     * @param dto 模板入参
     * @return 空返回体
     */
    @PutMapping("/templates/{id}")
    @PreAuthorize("@perm.has('print:template:edit')")
    @OpLog(module = "PRINT", action = "TEMPLATE", actionName = "修改打印模板", targetType = "print_template")
    public R<Void> editTemplate(@PathVariable("id") Long id, @Valid @RequestBody PrintTemplateSaveDTO dto) {
        printTemplateService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除模板（逻辑删除，删除前把唯一键改写为 {@code 原值_d{id}}）。
     *
     * @param id 模板 ID
     * @return 空返回体
     */
    @DeleteMapping("/templates/{id}")
    @PreAuthorize("@perm.has('print:template:edit')")
    @OpLog(module = "PRINT", action = "TEMPLATE", actionName = "删除打印模板", targetType = "print_template")
    public R<Void> deleteTemplate(@PathVariable("id") Long id) {
        printTemplateService.delete(id);
        return R.ok();
    }

    // ==================================================================================
    // 五、打印留痕
    // ==================================================================================

    /**
     * 留痕分页列表（条件：reportType / format / status / 日期区间 / keyword）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/logs")
    @PreAuthorize("@perm.has('print:center')")
    public R<PageResult<PrintLogVO>> pageLogs(@Valid PrintLogQuery query) {
        return R.ok(printLogService.pageLogs(query));
    }

    /**
     * 上报一条打印留痕（浏览器端 {@code window.print()} 等无法在服务端捕获的打印由前端上报）。
     *
     * @param dto     留痕入参
     * @param request HTTP 请求（客户端 IP）
     * @return 留痕 ID
     */
    @PostMapping("/logs")
    @PreAuthorize("@perm.has('print:center')")
    @OpLog(module = "PRINT", action = "LOG", actionName = "记录打印留痕", targetType = "print_log")
    public R<Long> saveLog(@Valid @RequestBody PrintLogSaveDTO dto, HttpServletRequest request) {
        return R.ok(printLogService.saveLog(dto, RequestUtils.getClientIp(request)));
    }

    // ==================================================================================
    // 六、报表清单
    // ==================================================================================

    /**
     * 报表清单：打印中心据此渲染报表卡片、PDF / Excel 按钮与权限判断。
     *
     * @return 报表清单（含 reportType / reportName / formats / permission / pdfPath / excelPath）
     */
    @GetMapping("/reports")
    @PreAuthorize("@perm.has('print:center')")
    public R<List<ReportMetaVO>> reports() {
        return R.ok(reportRenderService.listReports());
    }
}
