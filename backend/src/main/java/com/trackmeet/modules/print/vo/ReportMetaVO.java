package com.trackmeet.modules.print.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表清单项：打印中心据此渲染「报表卡片 + PDF/Excel 按钮 + 权限判断」。
 *
 * @author TrackMeet
 */
@Data
public class ReportMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报表类型（URL 键，如 {@code order-book}，用于拼接 {@code /excel/{report}}） */
    private String reportType;

    /** 留痕类型（{@code sys_print_log.report_type}，如 {@code ORDER_BOOK}） */
    private String reportCode;

    /** 报表名称（如「秩序册」） */
    private String reportName;

    /** 打印模板类型（{@code print_template.report_type}，如 {@code PROGRAM}） */
    private String templateReportType;

    /** 支持的输出格式：PDF / XLSX / HTML（HTML 指打印中心的 A4 预览 + 浏览器打印通道） */
    private List<String> formats = new ArrayList<>();

    /** 需要的权限编码（字段名与前端 {@code frontend/src/api/print.ts} 的 {@code ReportMeta.permCode} 一致） */
    private String permCode;

    /** PDF 输出地址（形如 {@code /api/print/pdf/order-book}） */
    private String pdfPath;

    /** Excel 输出地址（形如 {@code /api/print/excel/order-book}） */
    private String excelPath;

    /** 报表说明 */
    private String description;
}
