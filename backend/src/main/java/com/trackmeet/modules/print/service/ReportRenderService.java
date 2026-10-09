package com.trackmeet.modules.print.service;

import com.trackmeet.modules.print.constant.PrintReportType;
import com.trackmeet.modules.print.dto.PdfReportQuery;
import com.trackmeet.modules.print.pdf.PdfCapability;
import com.trackmeet.modules.print.vo.ReportMetaVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 报表渲染编排服务：PDF 与 Excel 输出的统一入口（取数 → 装配 → 渲染 → 落盘留痕）。
 * <p>
 * 职责边界：
 * <ol>
 *     <li><b>取数</b>：全部复用既有服务——{@code OrderBookService}（秩序册四表）、
 *         {@code StandingServiceImpl} + {@code StandingPrintSupport}（成绩册 / 积分表 / 奖牌榜）、
 *         {@code CertificateService}（证书），本模块不新增业务取数 SQL；</li>
 *     <li><b>排版</b>：全部交给既有 {@code PdfTableRenderer}（PDF）与 {@code StandingPrintSupport#writeExcel}
 *         （Excel，EasyExcel），本模块只负责组织 {@code PdfRenderRequest} / {@code SheetBundleVO}；</li>
 *     <li><b>模板</b>：命中 {@code print_template} 时覆盖纸张、方向、字号、页码、打印时间、页眉页脚签名与
 *         列宽（{@code extra_json.sections}）；未命中时使用内置默认版式；</li>
 *     <li><b>留痕</b>：每次生成写一条 {@code sys_print_log}，成功记 {@code SUCCESS}，异常记 {@code FAILED} 后重新抛出。</li>
 * </ol>
 *
 * @author TrackMeet
 */
public interface ReportRenderService {

    /**
     * PDF 生成能力（前端据此决定是否启用 PDF 按钮）。
     *
     * @return 能力信息（不抛异常）
     */
    PdfCapability capability();

    /**
     * 报表清单（打印中心渲染报表卡片、按钮与权限）。
     *
     * @return 报表清单
     */
    List<ReportMetaVO> listReports();

    /**
     * 输出 PDF 到响应流（{@code Content-Type: application/pdf}）。
     *
     * @param type     报表类型
     * @param query    查询条件（meetId 为空时取当前届次上下文）
     * @param request  HTTP 请求（取客户端 IP 写入留痕）
     * @param response HTTP 响应（写出 PDF 字节）
     */
    void renderPdf(PrintReportType type, PdfReportQuery query, HttpServletRequest request,
                   HttpServletResponse response);

    /**
     * 输出 Excel 到响应流（多 Sheet xlsx，文件名 {@code 报表名_届次_yyyyMMdd.xlsx}）。
     *
     * @param type     报表类型
     * @param query    查询条件
     * @param request  HTTP 请求（取客户端 IP 写入留痕）
     * @param response HTTP 响应（写出 xlsx 字节）
     */
    void renderExcel(PrintReportType type, PdfReportQuery query, HttpServletRequest request,
                     HttpServletResponse response);
}
