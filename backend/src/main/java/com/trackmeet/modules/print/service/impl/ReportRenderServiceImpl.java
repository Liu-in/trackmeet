package com.trackmeet.modules.print.service.impl;

import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.RequestUtils;
import com.trackmeet.modules.print.constant.PrintReportType;
import com.trackmeet.modules.print.constant.PrintSheetKeys;
import com.trackmeet.modules.print.dto.OrderBookQuery;
import com.trackmeet.modules.print.dto.PdfReportQuery;
import com.trackmeet.modules.print.dto.PrintQuery;
import com.trackmeet.modules.print.entity.PrintLog;
import com.trackmeet.modules.print.entity.PrintTemplate;
import com.trackmeet.modules.print.pdf.PdfCapability;
import com.trackmeet.modules.print.pdf.PdfFontProvider;
import com.trackmeet.modules.print.pdf.PdfRenderRequest;
import com.trackmeet.modules.print.pdf.PdfSection;
import com.trackmeet.modules.print.pdf.PdfTableRenderer;
import com.trackmeet.modules.print.service.OrderBookService;
import com.trackmeet.modules.print.service.PrintLogService;
import com.trackmeet.modules.print.service.PrintTemplateService;
import com.trackmeet.modules.print.service.ReportRenderService;
import com.trackmeet.modules.print.support.PrintPdfMerger;
import com.trackmeet.modules.print.support.PrintRenderSupport;
import com.trackmeet.modules.print.support.PrintText;
import com.trackmeet.modules.print.vo.OrderBookVO;
import com.trackmeet.modules.print.vo.ReportMetaVO;
import com.trackmeet.modules.print.vo.SheetBundleVO;
import com.trackmeet.modules.standing.dto.StandingMeetRow;
import com.trackmeet.modules.standing.service.CertificateService;
import com.trackmeet.modules.standing.service.impl.StandingServiceImpl;
import com.trackmeet.modules.standing.service.support.StandingPrintSupport;
import com.trackmeet.modules.standing.service.support.StandingText;
import com.trackmeet.modules.standing.vo.CertificateVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表渲染编排实现：PDF（OpenPDF）与 Excel（EasyExcel）输出的统一入口。
 * <p>
 * <b>取数全部复用既有服务</b>（本类不写任何业务 SQL）：
 * <ul>
 *     <li>秩序册 / 总日程表 / 分组表 / 分道表 / 检录表：{@link OrderBookService}
 *         （{@code getOrderBook} / {@code listCallingSheets} / {@code listLaneSheets} / {@code writeOrderBookExcel}）；</li>
 *     <li>成绩册 / 积分表 / 奖牌榜：{@link StandingServiceImpl}
 *         （{@code listResultRows} / {@code recordRows} / {@code groupNames} / {@code listTeamScoreRows} /
 *         {@code listMedalRows} / {@code meetInfo}）+ {@link StandingPrintSupport} 装配 {@code SheetBundleVO}；</li>
 *     <li>证书：{@link CertificateService}（{@code pageCertificates} 分页遍历）。</li>
 * </ul>
 * <b>排版全部复用既有渲染器</b>：PDF 交给 {@link PdfTableRenderer}（列宽自适应、表头加粗、跨页重复表头、
 * 页脚页码与打印时间、签名区），Excel 交给 {@link StandingPrintSupport#writeExcel}。
 * <p>
 * <b>模板生效规则</b>（{@link PrintTemplateService#resolveTemplate}）：显式 {@code templateId} → 该
 * {@code report_type} 的 {@code is_default = 1} → 无模板（内置默认 A4 / 纵向 / 9 磅 / 显示页码与打印时间 /
 * 签名区「记录员 / 裁判长 / 技术代表」）。命中模板时：
 * <ul>
 *     <li>{@code page_size} / {@code orientation} / {@code font_size} 覆盖纸张、方向、基准字号；</li>
 *     <li>{@code header_html} 去标签后作为页眉信息行、{@code footer_html} 作为每页页脚、{@code signature_html}
 *         作为签名区文本（可用 {@code |} 分 3 段），一律<b>不做 HTML 渲染</b>；</li>
 *     <li>{@code show_page_no} / {@code show_print_time} 控制页码与打印时间；</li>
 *     <li>{@code extra_json.sections} 的列宽配置优先于自适应列宽。</li>
 * </ul>
 * <b>留痕</b>：每次生成写一条 {@code sys_print_log}（{@code format = PDF / EXCEL}），成功 {@code SUCCESS}，
 * 异常先写 {@code FAILED} + {@code error_msg} 再重新抛出。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportRenderServiceImpl implements ReportRenderService {

    /** PDF 响应类型 */
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    /** Excel 响应类型 */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 文件名日期格式 */
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 届次日期格式 */
    private static final DateTimeFormatter META_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 文件名非法字符 */
    private static final String[] FILE_NAME_ILLEGAL = {"\\", "/", ":", "*", "?", "\"", "<", ">", "|"};

    /** 证书分页遍历每页条数（{@code CertificateService} 单页上限 200） */
    private static final long CERT_PAGE_SIZE = 200L;

    /** 证书分页遍历页数上限（防止证书量异常时无限循环） */
    private static final long CERT_MAX_PAGES = 50L;

    /** 默认签名区（无模板时） */
    private static final String DEFAULT_SIGNATURE_LEFT = "记录员：";
    private static final String DEFAULT_SIGNATURE_MIDDLE = "裁判长：";
    private static final String DEFAULT_SIGNATURE_RIGHT = "技术代表：";

    /** 证书字段表表头 */
    private static final List<String> CERT_FIELD_HEADERS = List.of("项目", "内容");

    /** 证书字段表默认列宽（项目 : 内容 = 1 : 3） */
    private static final float[] CERT_FIELD_WIDTHS = {1F, 3F};

    /** 证书 Excel 清单表头 */
    private static final List<String> CERT_SHEET_HEADERS = List.of(
            "证书编号", "证书类型", "姓名", "号码", "单位", "项目", "组别",
            "名次", "成绩", "荣誉", "签发日期", "签发单位", "二维码内容", "状态");

    /** 证书 Excel Sheet 名 */
    private static final String CERT_SHEET_NAME = "证书清单";

    /** 支持的输出格式（与前端打印中心展示口径一致：PDF / XLSX / HTML） */
    private static final List<String> REPORT_FORMATS = List.of("PDF", "XLSX", "HTML");

    private final PdfTableRenderer pdfTableRenderer;
    private final PdfFontProvider pdfFontProvider;
    private final OrderBookService orderBookService;
    private final StandingServiceImpl standingService;
    private final StandingPrintSupport standingPrintSupport;
    private final CertificateService certificateService;
    private final PrintTemplateService printTemplateService;
    private final PrintLogService printLogService;
    private final PrintRenderSupport renderSupport;

    // ==================================================================================
    // 一、能力与报表清单
    // ==================================================================================

    @Override
    public PdfCapability capability() {
        return pdfFontProvider.capability();
    }

    @Override
    public List<ReportMetaVO> listReports() {
        PrintReportType[] types = PrintReportType.values();
        List<ReportMetaVO> reports = new ArrayList<>(types.length);
        for (PrintReportType type : types) {
            ReportMetaVO vo = new ReportMetaVO();
            vo.setReportType(type.getReportType());
            vo.setReportCode(type.getReportCode());
            vo.setReportName(type.getReportName());
            vo.setTemplateReportType(type.getTemplateReportType());
            vo.setFormats(new ArrayList<>(REPORT_FORMATS));
            vo.setPermCode(type.getPermission());
            vo.setPdfPath(type.pdfPath());
            vo.setExcelPath(type.excelPath());
            vo.setDescription(type.getDescription());
            reports.add(vo);
        }
        return reports;
    }

    // ==================================================================================
    // 二、PDF 输出
    // ==================================================================================

    @Override
    public void renderPdf(PrintReportType type, PdfReportQuery query, HttpServletRequest request,
                          HttpServletResponse response) {
        PdfReportQuery condition = normalize(type, query);
        String ip = RequestUtils.getClientIp(request);
        Long meetId = null;
        Long templateId = null;
        try {
            meetId = resolveMeetId(condition);
            condition.setMeetId(meetId);
            PrintTemplate template = printTemplateService.resolveTemplate(meetId, condition.getTemplateId(),
                    type.getTemplateReportType());
            templateId = template == null ? null : template.getId();
            StandingMeetRow meet = meetOf(meetId);
            byte[] content = buildPdf(type, condition, meetId, template, meet);
            writeBinary(content, PDF_CONTENT_TYPE, buildFileName(type.getReportName(), meet, ".pdf"), response);
        } catch (Exception ex) {
            writeFailureLog(type, PrintLog.FORMAT_PDF, condition, meetId, templateId, ip, ex);
            throw asBizException(ex, "生成 PDF 失败");
        }
        // 成功留痕在响应写出之后单独落库：留痕写入失败不影响已输出的文件
        writeSuccessLog(type, PrintLog.FORMAT_PDF, condition, meetId, templateId, ip);
    }

    /**
     * 组装并渲染 PDF。
     */
    private byte[] buildPdf(PrintReportType type, PdfReportQuery query, Long meetId, PrintTemplate template,
                            StandingMeetRow meet) {
        if (type == PrintReportType.CERTIFICATES) {
            return buildCertificatesPdf(query, meetId, meet, template);
        }
        PdfRenderRequest.PdfRenderRequestBuilder builder = requestBuilder(type, query, template, meet);
        List<PdfSection> sections = new ArrayList<>();
        if (type == PrintReportType.ORDER_BOOK) {
            // 秩序册 = 总日程表 + 分组表 + 分道表 + 检录表（多节，节标题即各表标题）
            OrderBookVO orderBook = orderBookService.getOrderBook(toOrderBookQuery(query));
            sections.addAll(sectionsOf(PrintSheetKeys.NAME_SCHEDULE, orderBook.getScheduleSheets(), template));
            sections.addAll(sectionsOf(PrintSheetKeys.NAME_HEAT, orderBook.getHeatSheets(), template));
            sections.addAll(sectionsOf(PrintSheetKeys.NAME_LANE, orderBook.getLaneSheets(), template));
            sections.addAll(sectionsOf(PrintSheetKeys.NAME_CALLING, orderBook.getCallingSheets(), template));
        } else {
            sections.addAll(sectionsOf(sectionBaseName(type), buildBundles(type, query, meetId, meet), template));
        }
        // 无数据时 sections 为空，渲染器按「暂无数据」占位输出，仍返回一份 PDF（不抛异常）
        builder.sections(sections);
        return pdfTableRenderer.render(builder.build());
    }

    /**
     * 证书 PDF：每张证书渲染一页后合并；合并失败时降级为「单文档多节」，保证接口不失败。
     */
    private byte[] buildCertificatesPdf(PdfReportQuery query, Long meetId, StandingMeetRow meet,
                                        PrintTemplate template) {
        List<CertificateVO> certificates = listCertificates(meetId, query.normalizedCertType(), query.getUnitId());
        if (certificates.isEmpty()) {
            return pdfTableRenderer.render(requestBuilder(PrintReportType.CERTIFICATES, query, template, meet).build());
        }
        List<byte[]> pages = new ArrayList<>(certificates.size());
        for (CertificateVO certificate : certificates) {
            pages.add(pdfTableRenderer.render(certificateRequest(certificate, meet, template)));
        }
        try {
            return PrintPdfMerger.merge(pages);
        } catch (RuntimeException | LinkageError ex) {
            // 合并依赖 OpenPDF 的 PdfCopy：任何异常或链接错误都降级为单文档多节输出，保证接口可用
            log.warn("证书 PDF 合并失败，降级为单文档多节输出：{}", ex.getMessage());
            return pdfTableRenderer.render(certificatesFallbackRequest(certificates, query, template, meet));
        }
    }

    /**
     * 单张证书的渲染请求（一页）：编号 / 姓名 / 单位 / 项目 / 名次 / 成绩 / 日期 / 二维码文本。
     */
    private PdfRenderRequest certificateRequest(CertificateVO certificate, StandingMeetRow meet,
                                                PrintTemplate template) {
        List<String> metaLines = new ArrayList<>(4);
        metaLines.add("证书编号：" + StandingText.text(certificate.getCertNo()));
        if (certificate.getIssueDate() != null) {
            metaLines.add("签发日期：" + StandingText.date(certificate.getIssueDate()));
        }
        if (template != null) {
            metaLines.addAll(renderSupport.htmlLines(template.getHeaderHtml(), PrintRenderSupport.MAX_HEADER_LINES));
        }
        List<List<String>> rows = new ArrayList<>();
        addCertificateRows(rows, certificate);
        String[] signatures = signatures(template);
        return PdfRenderRequest.builder()
                .title(certificateTitle(certificate))
                .subtitle(meetTitle(meet))
                .metaLines(metaLines)
                .sections(List.of(certificateSection(certificate, template, rows)))
                .footerNote(template == null ? null : renderSupport.stripHtml(template.getFooterHtml()))
                // 每张证书自身即一页，页码无意义（打印时间仍按模板开关保留）
                .showPageNumber(false)
                .showPrintTime(template == null || template.showPrintTimeFlag())
                .signatureLeft(signatures[0])
                .signatureMiddle(signatures[1])
                .signatureRight(signatures[2])
                .pageSize(template == null ? PrintTemplate.PAGE_SIZE_A4 : template.pageSizeOrDefault())
                .orientation(template == null ? PrintTemplate.ORIENTATION_PORTRAIT : template.orientationOrDefault())
                .baseFontSize(template == null ? (float) PrintTemplate.DEFAULT_FONT_SIZE : template.fontSizeOrDefault())
                .build();
    }

    /**
     * 证书合并失败时的降级请求：单文档、每张证书一节（不再保证一页一证）。
     */
    private PdfRenderRequest certificatesFallbackRequest(List<CertificateVO> certificates, PdfReportQuery query,
                                                         PrintTemplate template, StandingMeetRow meet) {
        PdfRenderRequest.PdfRenderRequestBuilder builder =
                requestBuilder(PrintReportType.CERTIFICATES, query, template, meet);
        List<PdfSection> sections = new ArrayList<>(certificates.size());
        for (CertificateVO certificate : certificates) {
            List<List<String>> rows = new ArrayList<>();
            addCertificateRows(rows, certificate);
            sections.add(certificateSection(certificate, template, rows));
        }
        builder.sections(sections);
        return builder.build();
    }

    /**
     * 证书字段表：两列「项目 / 内容」，列宽可用 {@code extra_json.sections} 里的
     * {@code certificate} / {@code 证书} 键覆盖。
     */
    private PdfSection certificateSection(CertificateVO certificate, PrintTemplate template, List<List<String>> rows) {
        String extraJson = template == null ? null : template.getExtraJson();
        float[] widths = renderSupport.overrideWidths(extraJson,
                List.of("certificate", CERT_SHEET_NAME, PrintReportType.CERTIFICATES.getReportName()),
                CERT_FIELD_HEADERS.size());
        return PdfSection.builder()
                .heading(certificateHeading(certificate))
                .headers(CERT_FIELD_HEADERS)
                .rows(rows)
                .columnWidths(widths == null ? CERT_FIELD_WIDTHS.clone() : widths)
                .repeatHeader(true)
                .landscape(false)
                .build();
    }

    /**
     * 证书内容行。
     */
    private void addCertificateRows(List<List<String>> rows, CertificateVO certificate) {
        rows.add(certificateRow("证书编号", StandingText.text(certificate.getCertNo())));
        rows.add(certificateRow("证书类型", StandingText.text(certificate.getCertTypeName())));
        rows.add(certificateRow("姓名", StandingText.text(certificate.getAthleteName())));
        rows.add(certificateRow("号码", StandingText.text(certificate.getBibNo())));
        rows.add(certificateRow("单位", unitDisplay(certificate)));
        rows.add(certificateRow("项目", StandingText.text(certificate.getEventName())));
        rows.add(certificateRow("组别", StandingText.text(certificate.getGroupName())));
        rows.add(certificateRow("名次", StandingText.rankDisplay(certificate.getRankDisplay(),
                certificate.getRankNo())));
        rows.add(certificateRow("成绩", StandingText.text(certificate.getResultText())));
        rows.add(certificateRow("荣誉", StandingText.text(certificate.getHonorText())));
        rows.add(certificateRow("签发日期", StandingText.date(certificate.getIssueDate())));
        rows.add(certificateRow("签发单位", StandingText.text(certificate.getIssuer())));
        rows.add(certificateRow("二维码", StandingText.text(certificate.getQrContent())));
    }

    private List<String> certificateRow(String label, String value) {
        List<String> row = new ArrayList<>(2);
        row.add(label);
        row.add(value);
        return row;
    }

    /**
     * 证书标题：优先证书类型中文名（获奖证书 / 奖牌证书 / ...）。
     */
    private String certificateTitle(CertificateVO certificate) {
        return StringUtils.hasText(certificate.getCertTypeName())
                ? certificate.getCertTypeName().trim() : "证书";
    }

    /**
     * 证书节标题：荣誉描述（如「男子甲组 100 米 第1名」），缺失时用「项目 + 名次」。
     */
    private String certificateHeading(CertificateVO certificate) {
        String heading = PrintText.firstNonBlank(certificate.getHonorText(),
                PrintText.join(certificate.getEventName(),
                        StandingText.rankDisplay(certificate.getRankDisplay(), certificate.getRankNo())));
        return heading == null ? certificateTitle(certificate) : heading;
    }

    /**
     * 证书单位显示：优先单位简称。
     */
    private String unitDisplay(CertificateVO certificate) {
        return StandingText.text(StringUtils.hasText(certificate.getUnitShortName())
                ? certificate.getUnitShortName() : certificate.getUnitName());
    }

    // ==================================================================================
    // 三、Excel 输出
    // ==================================================================================

    @Override
    public void renderExcel(PrintReportType type, PdfReportQuery query, HttpServletRequest request,
                            HttpServletResponse response) {
        PdfReportQuery condition = normalize(type, query);
        String ip = RequestUtils.getClientIp(request);
        Long meetId = null;
        Long templateId = null;
        try {
            meetId = resolveMeetId(condition);
            condition.setMeetId(meetId);
            PrintTemplate template = printTemplateService.resolveTemplate(meetId, condition.getTemplateId(),
                    type.getTemplateReportType());
            templateId = template == null ? null : template.getId();
            StandingMeetRow meet = meetOf(meetId);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (type == PrintReportType.ORDER_BOOK) {
                // 秩序册沿用既有「固定 4 Sheet（总日程表 / 分组表 / 分道表 / 检录表）」导出
                orderBookService.writeOrderBookExcel(orderBookService.getOrderBook(toOrderBookQuery(condition)),
                        buffer);
            } else {
                standingPrintSupport.writeExcel(ensureSheets(buildBundles(type, condition, meetId, meet), type),
                        buffer);
            }
            String fileName = buildFileName(type.getReportName(), meet, ".xlsx");
            writeBinary(buffer.toByteArray(), XLSX_CONTENT_TYPE, fileName, response);
        } catch (Exception ex) {
            writeFailureLog(type, PrintLog.FORMAT_EXCEL, condition, meetId, templateId, ip, ex);
            throw asBizException(ex, "生成 Excel 失败");
        }
        // 成功留痕在响应写出之后单独落库：留痕写入失败不影响已输出的文件
        writeSuccessLog(type, PrintLog.FORMAT_EXCEL, condition, meetId, templateId, ip);
    }

    /**
     * 装配某报表的 {@code SheetBundleVO} 列表（表头 + 行数据，同一份数据同时服务 PDF 与 Excel）。
     * <p>
     * 秩序册不在本方法处理（PDF 走四表分节、Excel 走既有 {@code writeOrderBookExcel}）。
     */
    private List<SheetBundleVO> buildBundles(PrintReportType type, PdfReportQuery query, Long meetId,
                                             StandingMeetRow meet) {
        switch (type) {
            case RESULTS:
                // 成绩公告 / 成绩册：按「项目 × 轮次」拆表，含纪录标记（复用 StandingPrintSupport 装配）
                return standingPrintSupport.buildResultSheets(
                        standingService.listResultRows(meetId, query.getEventId(), query.getGroupId(),
                                query.getUnitId()),
                        standingService.recordRows(meetId), meet,
                        standingService.groupNames(meetId, query.getGroupId()));
            case POINTS:
                // 积分表（团体总分）：按团体口径分男子 / 女子 / 总团体
                return standingPrintSupport.buildTeamSheets(
                        standingService.listTeamScoreRows(meetId, query.getGroupId(), query.normalizedScoreType()),
                        meet);
            case MEDALS:
                return standingPrintSupport.buildMedalSheets(
                        standingService.listMedalRows(meetId, query.getGroupId()), meet);
            case CALLING:
                return orderBookService.listCallingSheets(toPrintQuery(query));
            case LANES:
                return orderBookService.listLaneSheets(toPrintQuery(query));
            case CERTIFICATES:
                return certificateSheets(listCertificates(meetId, query.normalizedCertType(), query.getUnitId()));
            default:
                log.debug("报表 {} 的 Excel 由专用方法处理，此处返回空表", type.getReportCode());
                return new ArrayList<>();
        }
    }

    /**
     * 证书清单表（Excel 用，一行一证书）。
     */
    private List<SheetBundleVO> certificateSheets(List<CertificateVO> certificates) {
        List<List<String>> rows = new ArrayList<>(certificates.size());
        for (CertificateVO certificate : certificates) {
            List<String> row = new ArrayList<>(CERT_SHEET_HEADERS.size());
            row.add(StandingText.text(certificate.getCertNo()));
            row.add(StandingText.text(certificate.getCertTypeName()));
            row.add(StandingText.text(certificate.getAthleteName()));
            row.add(StandingText.text(certificate.getBibNo()));
            row.add(unitDisplay(certificate));
            row.add(StandingText.text(certificate.getEventName()));
            row.add(StandingText.text(certificate.getGroupName()));
            row.add(StandingText.rankDisplay(certificate.getRankDisplay(), certificate.getRankNo()));
            row.add(StandingText.text(certificate.getResultText()));
            row.add(StandingText.text(certificate.getHonorText()));
            row.add(StandingText.date(certificate.getIssueDate()));
            row.add(StandingText.text(certificate.getIssuer()));
            row.add(StandingText.text(certificate.getQrContent()));
            row.add(StandingText.text(certificate.getStatus()));
            rows.add(row);
        }
        List<SheetBundleVO> bundles = new ArrayList<>(1);
        bundles.add(SheetBundleVO.of("certificates", CERT_SHEET_NAME, CERT_SHEET_HEADERS, rows));
        return bundles;
    }

    /**
     * 保证工作簿至少有一个 Sheet（EasyExcel 写出空工作簿不可靠）：无数据时输出「暂无数据」占位表。
     */
    private List<SheetBundleVO> ensureSheets(List<SheetBundleVO> bundles, PrintReportType type) {
        if (bundles != null && !bundles.isEmpty()) {
            return bundles;
        }
        List<SheetBundleVO> placeholder = new ArrayList<>(1);
        placeholder.add(SheetBundleVO.of("empty", type.getReportName(), List.of("说明"),
                List.of(List.of("暂无数据"))));
        return placeholder;
    }

    // ==================================================================================
    // 四、取数与装配（全部复用既有服务）
    // ==================================================================================

    /**
     * 证书分页遍历：{@code CertificateService.pageCertificates} 单页上限 200，按页收集至上限。
     */
    private List<CertificateVO> listCertificates(Long meetId, String certType, Long unitId) {
        List<CertificateVO> certificates = new ArrayList<>();
        long page = 1L;
        while (page <= CERT_MAX_PAGES) {
            PageResult<CertificateVO> result = certificateService.pageCertificates(meetId, certType, unitId,
                    null, page, CERT_PAGE_SIZE);
            if (result.getRecords() == null || result.getRecords().isEmpty()) {
                break;
            }
            certificates.addAll(result.getRecords());
            if (certificates.size() >= result.getTotal() || page >= result.getPages()) {
                break;
            }
            page++;
        }
        if (page > CERT_MAX_PAGES) {
            log.warn("证书数量超过单次导出上限（{} 页 × {} 条），已截断", CERT_MAX_PAGES, CERT_PAGE_SIZE);
        }
        return certificates;
    }

    /**
     * 届次信息（标题与文件名）：读取失败时降级为 {@code null}，不影响出表。
     */
    private StandingMeetRow meetOf(Long meetId) {
        try {
            return standingService.meetInfo(meetId);
        } catch (Exception ex) {
            log.debug("读取届次信息失败，标题与文件名改用报表名：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * {@code SheetBundleVO} → {@code PdfSection}（表头跨页重复；列宽取模板覆盖或自适应）。
     */
    private List<PdfSection> sectionsOf(String baseName, List<SheetBundleVO> bundles, PrintTemplate template) {
        List<PdfSection> sections = new ArrayList<>();
        if (bundles == null || bundles.isEmpty()) {
            return sections;
        }
        String extraJson = template == null ? null : template.getExtraJson();
        for (SheetBundleVO bundle : bundles) {
            if (bundle == null) {
                continue;
            }
            List<String> headers = bundle.getHeaders() == null ? new ArrayList<>() : bundle.getHeaders();
            List<List<String>> rows = bundle.getRows() == null ? new ArrayList<>() : bundle.getRows();
            List<String> keys = new ArrayList<>(3);
            keys.add(bundle.getSheetKey());
            keys.add(bundle.getSheetName());
            keys.add(baseName);
            float[] widths = renderSupport.resolveWidths(extraJson, keys, headers, rows);
            sections.add(PdfSection.builder()
                    .heading(bundle.getSheetName())
                    .headers(headers)
                    .rows(rows)
                    .columnWidths(widths)
                    .repeatHeader(true)
                    .landscape(false)
                    .build());
        }
        return sections;
    }

    /**
     * 报表分节基名（模板 {@code extra_json.sections} 的推荐键，同时兼容 sheetKey / sheetName）。
     */
    private String sectionBaseName(PrintReportType type) {
        return switch (type) {
            case CALLING -> PrintSheetKeys.NAME_CALLING;
            case LANES -> PrintSheetKeys.NAME_LANE;
            case RESULTS -> "成绩册";
            case POINTS -> "积分表";
            case MEDALS -> "奖牌榜";
            case CERTIFICATES -> CERT_SHEET_NAME;
            case ORDER_BOOK -> PrintSheetKeys.NAME_SCHEDULE;
        };
    }

    // ==================================================================================
    // 五、渲染请求装配（模板生效规则）
    // ==================================================================================

    /**
     * 组装渲染请求的公共部分：标题、副标题、信息行、页脚、页码与打印时间开关、签名区、纸张参数。
     */
    private PdfRenderRequest.PdfRenderRequestBuilder requestBuilder(PrintReportType type, PdfReportQuery query,
                                                                    PrintTemplate template, StandingMeetRow meet) {
        List<String> metaLines = metaLines(meet, query);
        if (template != null) {
            metaLines.addAll(renderSupport.htmlLines(template.getHeaderHtml(), PrintRenderSupport.MAX_HEADER_LINES));
        }
        String[] signatures = signatures(template);
        return PdfRenderRequest.builder()
                .title(reportTitle(type, meet))
                .subtitle(meet == null ? null : PrintText.firstNonBlank(meet.getEdition(), meet.getMeetName()))
                .metaLines(metaLines)
                .footerNote(template == null ? null : renderSupport.stripHtml(template.getFooterHtml()))
                // 无模板内置默认：A4 纵向、基准字号 9、显示页码与打印时间、签名区「记录员 / 裁判长 / 技术代表」
                .showPageNumber(template == null || template.showPageNoFlag())
                .showPrintTime(template == null || template.showPrintTimeFlag())
                .signatureLeft(signatures[0])
                .signatureMiddle(signatures[1])
                .signatureRight(signatures[2])
                .pageSize(template == null ? PrintTemplate.PAGE_SIZE_A4 : template.pageSizeOrDefault())
                .orientation(template == null ? PrintTemplate.ORIENTATION_PORTRAIT : template.orientationOrDefault())
                .baseFontSize(template == null ? (float) PrintTemplate.DEFAULT_FONT_SIZE : template.fontSizeOrDefault());
    }

    /**
     * 标题：{@code 届次简称/标识/名称 + 报表名}。
     */
    private String reportTitle(PrintReportType type, StandingMeetRow meet) {
        String subject = meet == null ? null
                : PrintText.firstNonBlank(meet.getShortName(), meet.getEdition(), meet.getMeetName());
        return subject == null ? type.getReportName() : subject + " " + type.getReportName();
    }

    /**
     * 副标题：届次标识优先，其次运动会名称。
     */
    private String meetTitle(StandingMeetRow meet) {
        return meet == null ? null : PrintText.firstNonBlank(meet.getEdition(), meet.getMeetName());
    }

    /**
     * 信息行：比赛日期 / 届次 / 地点 / 主办 / 承办 + 报表过滤说明（+ 模板页眉纯文本）。
     */
    private List<String> metaLines(StandingMeetRow meet, PdfReportQuery query) {
        List<String> lines = new ArrayList<>(6);
        if (meet != null) {
            lines.add(dateRangeText(meet));
            if (StringUtils.hasText(meet.getEdition())) {
                lines.add("届次：" + meet.getEdition().trim());
            }
            if (StringUtils.hasText(meet.getPlace())) {
                lines.add("地点：" + meet.getPlace().trim());
            }
            if (StringUtils.hasText(meet.getHost())) {
                lines.add("主办：" + meet.getHost().trim());
            }
            if (StringUtils.hasText(meet.getOrganizer())) {
                lines.add("承办：" + meet.getOrganizer().trim());
            }
        }
        if (query != null && StringUtils.hasText(query.getCertType())) {
            lines.add("证书类型：" + StandingText.certTypeName(query.getCertType()));
        }
        if (query != null && query.getPublishOnly() != null && !query.isPublishOnlyEnabled()) {
            lines.add("范围：含未发布（草稿）日程");
        }
        return lines;
    }

    /**
     * 比赛日期行。
     */
    private String dateRangeText(StandingMeetRow meet) {
        if (meet.getStartDate() == null) {
            return "比赛日期：" + META_DATE_FORMATTER.format(LocalDate.now());
        }
        if (meet.getEndDate() == null || meet.getEndDate().equals(meet.getStartDate())) {
            return "比赛日期：" + META_DATE_FORMATTER.format(meet.getStartDate());
        }
        return "比赛日期：" + META_DATE_FORMATTER.format(meet.getStartDate())
                + " ~ " + META_DATE_FORMATTER.format(meet.getEndDate());
    }

    /**
     * 签名区：模板 {@code signature_html} 去标签后按 {@code |} 拆 3 段；无模板时用内置默认。
     */
    private String[] signatures(PrintTemplate template) {
        if (template != null && StringUtils.hasText(template.getSignatureHtml())) {
            String[] parts = renderSupport.splitSignature(template.getSignatureHtml(), 3);
            if (StringUtils.hasText(parts[0]) || StringUtils.hasText(parts[1]) || StringUtils.hasText(parts[2])) {
                return parts;
            }
        }
        return new String[]{DEFAULT_SIGNATURE_LEFT, DEFAULT_SIGNATURE_MIDDLE, DEFAULT_SIGNATURE_RIGHT};
    }

    // ==================================================================================
    // 六、查询对象转换与校验
    // ==================================================================================

    /**
     * 校验 {@code reportType} 与接口路径一致（兼容 {@code order-book} 与 {@code ORDER_BOOK} 写法）。
     */
    private PdfReportQuery normalize(PrintReportType type, PdfReportQuery query) {
        PdfReportQuery condition = query == null ? new PdfReportQuery() : query;
        if (StringUtils.hasText(condition.getReportType())
                && PrintReportType.optional(condition.getReportType()) != type) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "reportType 与接口路径不匹配：" + condition.getReportType());
        }
        return condition;
    }

    /**
     * 解析并校验届次：入参与 {@link MeetContext} 不一致时抛 {@code FORBIDDEN}。
     */
    private Long resolveMeetId(PdfReportQuery query) {
        Long requestMeetId = query == null ? null : query.getMeetId();
        Long contextMeetId = MeetContext.get();
        if (requestMeetId != null && contextMeetId != null && !requestMeetId.equals(contextMeetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "请求届次与当前届次上下文不一致，禁止跨届次导出");
        }
        Long meetId = requestMeetId != null ? requestMeetId : contextMeetId;
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少届次参数 meetId（或请求头 X-Meet-Id）");
        }
        return meetId;
    }

    /**
     * 打印中心查询条件 → 既有 {@code PrintQuery}（秩序册四表）。
     */
    private PrintQuery toPrintQuery(PdfReportQuery source) {
        PrintQuery query = new PrintQuery();
        query.setMeetId(source.getMeetId());
        query.setSessionId(source.getSessionId());
        query.setGroupId(source.getGroupId());
        query.setEventId(source.getEventId());
        query.setUnitId(source.getUnitId());
        query.setVenueId(source.getVenueId());
        query.setPublishOnly(source.getPublishOnly());
        query.setFormat(PrintQuery.FORMAT_JSON);
        return query;
    }

    /**
     * 打印中心查询条件 → 既有 {@code OrderBookQuery}（秩序册）。
     */
    private OrderBookQuery toOrderBookQuery(PdfReportQuery source) {
        OrderBookQuery query = new OrderBookQuery();
        query.setMeetId(source.getMeetId());
        query.setSessionId(source.getSessionId());
        query.setGroupId(source.getGroupId());
        query.setEventId(source.getEventId());
        query.setUnitId(source.getUnitId());
        query.setVenueId(source.getVenueId());
        query.setPublishOnly(source.getPublishOnly());
        query.setFormat(PrintQuery.FORMAT_JSON);
        return query;
    }

    // ==================================================================================
    // 七、响应输出与留痕
    // ==================================================================================

    /**
     * 写出二进制响应：先在内存生成完整内容，再设置响应头，避免生成失败时输出半截文件。
     */
    private void writeBinary(byte[] content, String contentType, String fileName, HttpServletResponse response) {
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(content.length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=UTF-8''" + encodedName);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(content);
            outputStream.flush();
        } catch (IOException ex) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "输出文件失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 文件名：{@code 报表名_届次_yyyyMMdd.扩展名}。
     */
    private String buildFileName(String reportName, StandingMeetRow meet, String extension) {
        String subject = meet == null ? null
                : PrintText.firstNonBlank(meet.getShortName(), meet.getEdition(), meet.getMeetName());
        LocalDate date = meet != null && meet.getStartDate() != null ? meet.getStartDate() : LocalDate.now();
        StringBuilder builder = new StringBuilder(sanitizeFileName(reportName));
        if (subject != null) {
            builder.append('_').append(sanitizeFileName(subject));
        }
        builder.append('_').append(FILE_DATE_FORMATTER.format(date)).append(extension);
        return builder.toString();
    }

    /**
     * 过滤文件名非法字符。
     */
    private String sanitizeFileName(String value) {
        String text = StringUtils.hasText(value) ? value.trim() : "";
        for (String illegal : FILE_NAME_ILLEGAL) {
            text = text.replace(illegal, "_");
        }
        text = text.replaceAll("[\\r\\n\\t]", " ").trim();
        return text.isEmpty() ? "报表" : text;
    }

    /**
     * 写成功留痕（{@code status = SUCCESS}）：文件已输出，留痕写入自身异常只记日志，不改变调用结果。
     */
    private void writeSuccessLog(PrintReportType type, String format, PdfReportQuery query, Long meetId,
                                 Long templateId, String ip) {
        try {
            printLogService.recordSuccess(type, format, query, meetId, templateId, 1, ip);
        } catch (Exception ex) {
            log.warn("写入打印留痕异常（不影响已输出的文件）：{}", ex.getMessage());
        }
    }

    /**
     * 写失败留痕：留痕写入自身再出问题时只记日志，绝不影响对外抛出的原始异常。
     */
    private void writeFailureLog(PrintReportType type, String format, PdfReportQuery query, Long meetId,
                                 Long templateId, String ip, Exception ex) {
        log.error("打印输出失败：report={}, format={}, meetId={}", type.getReportCode(), format, meetId, ex);
        try {
            printLogService.recordFailure(type, format, query, meetId, templateId, ip, ex.getMessage());
        } catch (Exception logEx) {
            log.warn("写入打印失败留痕异常：{}", logEx.getMessage());
        }
    }

    /**
     * 统一异常转换：业务异常原样抛出，其余包装为 {@code SYSTEM_ERROR}。
     */
    private BizException asBizException(Exception ex, String message) {
        if (ex instanceof BizException biz) {
            return biz;
        }
        return new BizException(ResultCode.SYSTEM_ERROR, message + "：" + ex.getMessage(), ex);
    }
}
