package com.trackmeet.modules.print.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

/**
 * print_template 打印模板表：各类报表的页眉页脚、签名栏与纸张设置，届次级可覆盖全局默认模板。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code print_template} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, meet_id, template_code, template_name, report_type, header_html, footer_html, logo_file_id,
 * signature_html, page_size, orientation, font_size, show_page_no, show_print_time, is_default,
 * extra_json, status, remark, create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * 唯一键 {@code uk_meet_template_code (meet_id, template_code)} 不含 {@code deleted}，因此逻辑删除时先把
 * {@code template_code} 改写为「原值 + {@code _d}{id}」再删除（与 {@code SysDictServiceImpl} 等既有策略一致）。
 * <p>
 * {@code extra_json} 中的 {@code {"sections":{"分组表":[2,1,1,1]}}} 用于覆盖某张表的列宽占比
 * （键可为 sheetKey / sheetName / 报表名，见 {@code PrintRenderSupport#overrideWidths}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("print_template")
public class PrintTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 模板状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 模板状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 纸张方向：纵向 */
    public static final String ORIENTATION_PORTRAIT = "PORTRAIT";
    /** 纸张方向：横向 */
    public static final String ORIENTATION_LANDSCAPE = "LANDSCAPE";

    /** 纸张规格：A4 */
    public static final String PAGE_SIZE_A4 = "A4";
    /** 纸张规格：A3 */
    public static final String PAGE_SIZE_A3 = "A3";

    /** 默认基准字号（磅） */
    public static final int DEFAULT_FONT_SIZE = 9;

    /** 模板编码列长度（删除时改写唯一键需截断） */
    public static final int TEMPLATE_CODE_MAX_LENGTH = 64;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 模板编码，届次内唯一 */
    @TableField("template_code")
    private String templateCode;

    /** 模板名称 */
    @TableField("template_name")
    private String templateName;

    /** 报表类型：PROGRAM / START_LIST / RESULT / POINTS / MEDAL / CERT */
    @TableField("report_type")
    private String reportType;

    /** 页眉 HTML（渲染时去标签后按纯文本写入页眉区） */
    @TableField("header_html")
    private String headerHtml;

    /** 页脚 HTML（渲染时去标签后按纯文本写入每页页脚） */
    @TableField("footer_html")
    private String footerHtml;

    /** Logo 文件 ID（sys_file.id，本期仅落库不参与渲染） */
    @TableField("logo_file_id")
    private Long logoFileId;

    /** 签名栏 HTML（去标签后按纯文本写入签名区，可用 {@code |} 分成 3 段） */
    @TableField("signature_html")
    private String signatureHtml;

    /** 纸张规格：A4 / A3 / LETTER（渲染器当前支持 A4 / A3，其余按 A4 处理） */
    @TableField("page_size")
    private String pageSize;

    /** 纸张方向：PORTRAIT 纵向 / LANDSCAPE 横向 */
    @TableField("orientation")
    private String orientation;

    /** 基准字号（磅） */
    @TableField("font_size")
    private Integer fontSize;

    /** 是否显示页码：1 显示 / 0 不显示 */
    @TableField("show_page_no")
    private Integer showPageNo;

    /** 是否显示打印时间：1 显示 / 0 不显示 */
    @TableField("show_print_time")
    private Integer showPrintTime;

    /** 是否为该报表类型默认模板：1 是 / 0 否（同一届次同一报类型至多一个 1） */
    @TableField("is_default")
    private Integer isDefault;

    /** 扩展配置 JSON：{@code {"sections":{"分组表":[2,1,1,1]}}} 等 */
    @TableField("extra_json")
    private String extraJson;

    /** 模板状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /**
     * 是否启用（{@code status} 为空视为启用）。
     *
     * @return {@code true} 表示启用
     */
    public boolean enabled() {
        return !StringUtils.hasText(status) || STATUS_ENABLED.equalsIgnoreCase(status.trim());
    }

    /**
     * 生效纸张规格：为空按 A4。
     *
     * @return A4 / A3
     */
    public String pageSizeOrDefault() {
        return StringUtils.hasText(pageSize) ? pageSize.trim().toUpperCase() : PAGE_SIZE_A4;
    }

    /**
     * 生效纸张方向：为空按纵向。
     *
     * @return PORTRAIT / LANDSCAPE
     */
    public String orientationOrDefault() {
        return StringUtils.hasText(orientation) ? orientation.trim().toUpperCase() : ORIENTATION_PORTRAIT;
    }

    /**
     * 生效基准字号：为空或非正数按 9 磅。
     *
     * @return 字号（磅）
     */
    public float fontSizeOrDefault() {
        if (fontSize == null || fontSize <= 0) {
            return DEFAULT_FONT_SIZE;
        }
        return Math.min(24, Math.max(6, fontSize));
    }

    /**
     * 是否显示页码（{@code show_page_no}，null 视为显示）。
     *
     * @return {@code true} 表示显示
     */
    public boolean showPageNoFlag() {
        return showPageNo == null || showPageNo == 1;
    }

    /**
     * 是否显示打印时间（{@code show_print_time}，null 视为显示）。
     *
     * @return {@code true} 表示显示
     */
    public boolean showPrintTimeFlag() {
        return showPrintTime == null || showPrintTime == 1;
    }

    /**
     * 是否默认模板（{@code is_default == 1}）。
     *
     * @return {@code true} 表示默认模板
     */
    public boolean defaultFlag() {
        return isDefault != null && isDefault == 1;
    }
}
