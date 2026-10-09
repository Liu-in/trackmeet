package com.trackmeet.modules.print.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 渲染请求（页眉 + 若干表格节 + 签名区）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfRenderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主标题（如「2025 年市中学生田径运动会 秩序册」） */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 标题下的信息行（届次/日期/地点等） */
    @Builder.Default
    private List<String> metaLines = new ArrayList<>();

    /** 表格节 */
    @Builder.Default
    private List<PdfSection> sections = new ArrayList<>();

    /** 页脚说明（打印在各页底部） */
    private String footerNote;

    /** 是否显示页码 */
    @Builder.Default
    private boolean showPageNumber = true;

    /** 是否显示打印时间 */
    @Builder.Default
    private boolean showPrintTime = true;

    /** 签名区左（如「记录员：」） */
    private String signatureLeft;

    /** 签名区中（如「裁判长：」） */
    private String signatureMiddle;

    /** 签名区右（如「技术代表：」） */
    private String signatureRight;

    /** 纸张规格：A4 / A3 */
    @Builder.Default
    private String pageSize = "A4";

    /** 纸张方向：PORTRAIT 纵向 / LANDSCAPE 横向 */
    @Builder.Default
    private String orientation = "PORTRAIT";

    /** 基准字号（磅） */
    @Builder.Default
    private Float baseFontSize = 9F;
}
