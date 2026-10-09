package com.trackmeet.modules.print.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 打印模板新增 / 修改入参。
 * <p>
 * 说明：
 * <ol>
 *     <li>{@code meetId} 必填，且必须与 {@code MeetContext} 一致（不一致按越权处理）；</li>
 *     <li>{@code templateCode} 为届次内唯一键 {@code uk_meet_template_code (meet_id, template_code)} 的业务值；</li>
 *     <li>{@code isDefault = 1} 时同一 {@code reportType} 只能有一个默认模板（保存前先把旧的置 0）；</li>
 *     <li>HTML 字段（{@code headerHtml / footerHtml / signatureHtml}）在渲染时<b>只去标签取纯文本</b>，不做 HTML 渲染。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Data
public class PrintTemplateSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填） */
    @NotNull(message = "届次不能为空")
    private Long meetId;

    /** 模板编码，届次内唯一 */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码最长 64 字符")
    private String templateCode;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称最长 100 字符")
    private String templateName;

    /** 报表类型：PROGRAM / START_LIST / RESULT / POINTS / MEDAL / CERT */
    @NotBlank(message = "报表类型不能为空")
    @Size(max = 32, message = "报表类型最长 32 字符")
    private String reportType;

    /** 页眉 HTML（去标签后作为纯文本写入页眉区） */
    private String headerHtml;

    /** 页脚 HTML（去标签后作为纯文本写入每页页脚） */
    private String footerHtml;

    /** Logo 文件 ID（sys_file.id） */
    private Long logoFileId;

    /** 签名栏 HTML（去标签后写入签名区，可用 {@code |} 拆成 3 段） */
    private String signatureHtml;

    /** 纸张规格：A4 / A3（其余值按 A4 处理） */
    @Size(max = 16, message = "纸张规格最长 16 字符")
    @Pattern(regexp = "(?i)^(A4|A3|LETTER)?$", message = "纸张规格仅支持 A4 / A3 / LETTER")
    private String pageSize;

    /** 纸张方向：PORTRAIT 纵向 / LANDSCAPE 横向 */
    @Size(max = 16, message = "纸张方向最长 16 字符")
    @Pattern(regexp = "(?i)^(PORTRAIT|LANDSCAPE)?$", message = "纸张方向仅支持 PORTRAIT / LANDSCAPE")
    private String orientation;

    /** 基准字号（磅），6~24 */
    @Min(value = 6, message = "基准字号最小 6 磅")
    @Max(value = 24, message = "基准字号最大 24 磅")
    private Integer fontSize;

    /** 是否显示页码：1 显示 / 0 不显示（为空按 1 处理） */
    private Integer showPageNo;

    /** 是否显示打印时间：1 显示 / 0 不显示（为空按 1 处理） */
    private Integer showPrintTime;

    /** 是否为该报表类型默认模板：1 是 / 0 否（为空按 0 处理） */
    private Integer isDefault;

    /** 扩展配置 JSON，如 {@code {"sections":{"分组表":[2,1,1,1]}}} */
    private String extraJson;

    /** 模板状态：ENABLED 启用 / DISABLED 停用（为空按 ENABLED 处理） */
    @Size(max = 32, message = "模板状态最长 32 字符")
    private String status;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 字符")
    private String remark;
}
