package com.trackmeet.modules.print.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 单个表格节（一个标题 + 一张表，可独立设置列宽与字号）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfSection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节标题（如「男子甲组 100米 预赛 分组表」） */
    private String heading;

    /** 节前的补充信息行（如「比赛时间：2025-10-18 08:00」「场地：主跑道」） */
    @Builder.Default
    private List<String> metaLines = new ArrayList<>();

    /** 表头（为空表示不渲染表格，仅输出标题与说明） */
    @Builder.Default
    private List<String> headers = new ArrayList<>();

    /** 数据行 */
    @Builder.Default
    private List<List<String>> rows = new ArrayList<>();

    /** 列宽占比（为空按内容长度自适应） */
    private float[] columnWidths;

    /** 本节的说明/备注（渲染在表格下方） */
    private String note;

    /** 是否横向（任一节为横向时整份文档按横向输出） */
    private boolean landscape;

    /** 是否重复表头（长表格跨页时） */
    @Builder.Default
    private boolean repeatHeader = true;

    /** 本节字号（为空取文档基准字号） */
    private Float fontSize;
}
