package com.trackmeet.modules.print.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 打印模板 VO：与 {@code print_template} 列一一对应，供模板配置页回显与编辑。
 *
 * @author TrackMeet
 */
@Data
public class PrintTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模板 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 模板编码，届次内唯一 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 报表类型：PROGRAM / START_LIST / RESULT / POINTS / MEDAL / CERT */
    private String reportType;

    /** 页眉 HTML */
    private String headerHtml;

    /** 页脚 HTML */
    private String footerHtml;

    /** Logo 文件 ID */
    private Long logoFileId;

    /** 签名栏 HTML */
    private String signatureHtml;

    /** 纸张规格：A4 / A3 / LETTER */
    private String pageSize;

    /** 纸张方向：PORTRAIT 纵向 / LANDSCAPE 横向 */
    private String orientation;

    /** 基准字号（磅） */
    private Integer fontSize;

    /** 是否显示页码：1 显示 / 0 不显示 */
    private Integer showPageNo;

    /** 是否显示打印时间：1 显示 / 0 不显示 */
    private Integer showPrintTime;

    /** 是否默认模板：1 是 / 0 否 */
    private Integer isDefault;

    /** 扩展配置 JSON */
    private String extraJson;

    /** 模板状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;
}
