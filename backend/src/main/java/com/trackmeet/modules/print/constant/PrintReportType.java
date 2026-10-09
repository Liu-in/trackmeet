package com.trackmeet.modules.print.constant;

import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 打印中心报表类型目录：把「URL 键 / 留痕类型 / 中文报表名 / 模板类型 / 权限编码」收敛到一处。
 * <p>
 * 三个编码口径的对应关系（同一份报表在不同表中使用不同词表，必须显式映射，避免各层各写一套）：
 * <ol>
 *     <li>{@code reportType}：对外 URL 键，用于 {@code /api/print/excel/{report}} 与 {@code ReportMetaVO}；</li>
 *     <li>{@code reportCode}：{@code sys_print_log.report_type} 留痕类型（大写，DDL 注释口径
 *         {@code START_LIST} / {@code RESULT_BOOK} 同族）；</li>
 *     <li>{@code templateReportType}：{@code print_template.report_type} 模板类型
 *         （{@code PROGRAM} / {@code START_LIST} / {@code RESULT} / {@code POINTS} / {@code MEDAL} / {@code CERT}）。</li>
 * </ol>
 * <p>
 * 权限编码与 {@code sql/05-init-data.sql} 中已初始化的按钮权限逐字一致（静态检查脚本会校验
 * {@code @PreAuthorize} 使用的编码必须已在初始化数据中定义）。
 *
 * @author TrackMeet
 */
@Getter
public enum PrintReportType {

    /** 秩序册：总日程表 + 分组表 + 分道表 + 检录表（多节） */
    ORDER_BOOK("order-book", "ORDER_BOOK", "秩序册", "PROGRAM", "print:orderbook",
            "总日程表 + 分组表 + 分道表 + 检录表（多节合并输出）"),

    /** 成绩公告 / 成绩册：按「项目 × 轮次」拆表 */
    RESULTS("results", "RESULTS", "成绩公告", "RESULT", "print:results",
            "已审核成绩按「项目 × 轮次」拆表，含名次、成绩、风速与纪录标记"),

    /** 检录表：按检录时间排序，含检录处与道次 */
    CALLING("calling", "CALLING", "检录表", "START_LIST", "print:calling",
            "含检录时间、检录处、项目、轮次、组次、号码、姓名、单位、道次"),

    /** 分道表：径赛 / 接力按分组逐组列出 1~N 道，空道补占位行 */
    LANES("lanes", "LANES", "分道表", "START_LIST", "print:orderbook",
            "径赛 / 接力按分组逐组列出 1~N 道，空道补占位行"),

    /** 积分表：团体总分（男子 / 女子 / 总团体） */
    POINTS("points", "POINTS", "积分表", "POINTS", "print:standings",
            "团体总分表（男子团体 / 女子团体 / 总团体），含名次分、纪录加分与接力分"),

    /** 奖牌榜 */
    MEDALS("medals", "MEDALS", "奖牌榜", "MEDAL", "print:standings",
            "单位奖牌统计：金 / 银 / 铜 / 奖牌总数 / 排序分"),

    /** 证书：批量生成 PDF 时每张证书一页，Excel 导出为证书清单 */
    CERTIFICATES("certificates", "CERTIFICATES", "证书", "CERT", "certificate:export",
            "证书批量输出：编号、姓名、单位、项目、名次、成绩、日期与二维码文本");

    /** 对外 URL 键（{@code /api/print/excel/{report}} 与 {@code ReportMetaVO.reportType}） */
    private final String reportType;

    /** 打印留痕类型（{@code sys_print_log.report_type}） */
    private final String reportCode;

    /** 中文报表名（PDF/Excel 标题与文件名主体） */
    private final String reportName;

    /** 打印模板类型（{@code print_template.report_type}） */
    private final String templateReportType;

    /** 所需权限编码 */
    private final String permission;

    /** 报表说明（打印中心清单展示） */
    private final String description;

    PrintReportType(String reportType, String reportCode, String reportName, String templateReportType,
                    String permission, String description) {
        this.reportType = reportType;
        this.reportCode = reportCode;
        this.reportName = reportName;
        this.templateReportType = templateReportType;
        this.permission = permission;
        this.description = description;
    }

    /**
     * PDF 输出地址（打印中心按报表清单渲染按钮）。
     *
     * @return 形如 {@code /api/print/pdf/order-book}
     */
    public String pdfPath() {
        return "/api/print/pdf/" + reportType;
    }

    /**
     * Excel 输出地址。
     *
     * @return 形如 {@code /api/print/excel/order-book}
     */
    public String excelPath() {
        return "/api/print/excel/" + reportType;
    }

    /**
     * 解析报表类型：忽略大小写与 {@code - _ 空格}，同时接受 URL 键（{@code order-book}）
     * 与留痕类型（{@code ORDER_BOOK}）。
     *
     * @param value 原始值
     * @return 报表类型
     * @throws BizException 无法识别时抛 {@code PARAM_ERROR}
     */
    public static PrintReportType of(String value) {
        PrintReportType type = optional(value);
        if (type == null) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "不支持的报表类型：" + value + "（支持 " + supportedKeys() + "）");
        }
        return type;
    }

    /**
     * 宽松解析：无法识别时返回 {@code null}（用于留痕过滤等「非法值即不过滤」的场景）。
     *
     * @param value 原始值
     * @return 报表类型或 {@code null}
     */
    public static PrintReportType optional(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return null;
        }
        for (PrintReportType type : values()) {
            if (normalize(type.reportType).equals(normalized) || normalize(type.reportCode).equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 规范化留痕类型：命中报表目录时返回标准 {@code reportCode}，否则返回「去空白 + 大写」原值
     * （留痕表允许记录目录之外的报表类型）。
     *
     * @param value 原始值
     * @return 留痕类型
     */
    public static String normalizeCode(String value) {
        PrintReportType type = optional(value);
        if (type != null) {
            return type.getReportCode();
        }
        return value == null ? null : value.trim().toUpperCase();
    }

    /**
     * 是否属于给定模板类型（{@code print_template.report_type}）。
     *
     * @param templateReportType 模板类型
     * @return {@code true} 表示一致
     */
    public boolean matchesTemplateType(String templateReportType) {
        return templateReportType != null && templateReportType.equalsIgnoreCase(this.templateReportType);
    }

    /**
     * 支持的 URL 键清单（错误提示用）。
     *
     * @return 以 {@code , } 分隔的键清单
     */
    public static String supportedKeys() {
        List<String> keys = new ArrayList<>(values().length);
        for (PrintReportType type : values()) {
            keys.add(type.reportType);
        }
        return String.join(", ", keys);
    }

    /**
     * 归一化：仅保留字母与数字并转大写（{@code order-book} → {@code ORDERBOOK}）。
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                builder.append(Character.toUpperCase(character));
            }
        }
        return builder.toString();
    }
}
