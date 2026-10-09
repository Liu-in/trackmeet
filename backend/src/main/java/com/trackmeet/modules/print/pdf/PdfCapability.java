package com.trackmeet.modules.print.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 生成能力信息（供前端判断是否启用 PDF 导出按钮并给出提示）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfCapability implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否可用（找到并成功加载中文字体） */
    private boolean available;

    /** 实际使用的字体文件路径 */
    private String fontPath;

    /** 字体名称 */
    private String fontName;

    /** 嵌入方式，如 IDENTITY_H+EMBEDDED */
    private String embeddedMode;

    /** 提示信息（不可用时说明如何配置） */
    private String message;

    /** 已尝试的候选字体路径 */
    @Builder.Default
    private List<String> candidates = new ArrayList<>();
}
