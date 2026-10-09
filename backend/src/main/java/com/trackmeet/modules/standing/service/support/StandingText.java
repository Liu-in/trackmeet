package com.trackmeet.modules.standing.service.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 榜单文本工具：统一空值占位、数值与日期格式化、证书类型与状态的中文名映射。
 * <p>
 * 与 {@code modules/print/support/PrintText} 同源约定：{@code SheetBundleVO.rows} 中所有单元格
 * 必须为字符串且空值输出 {@code -}。数值与文本转换复用 {@link com.trackmeet.modules.print.support.PrintText}，
 * 本类只补充日期与枚举名映射。
 *
 * @author TrackMeet
 */
public final class StandingText {

    /** 空值占位符 */
    public static final String EMPTY = "-";

    /** 日期时间格式 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 日期格式 */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private StandingText() {
    }

    /**
     * 日期格式化，空值输出 {@link #EMPTY}。
     *
     * @param value 日期
     * @return 显示文本
     */
    public static String date(LocalDate value) {
        return value == null ? EMPTY : DATE.format(value);
    }

    /**
     * 日期时间格式化，空值输出 {@link #EMPTY}。
     *
     * @param value 日期时间
     * @return 显示文本
     */
    public static String dateTime(LocalDateTime value) {
        return value == null ? EMPTY : DATE_TIME.format(value);
    }

    /**
     * 数值格式化（去尾零），空值输出 {@link #EMPTY}。
     *
     * @param value 数值
     * @return 显示文本
     */
    public static String number(BigDecimal value) {
        if (value == null) {
            return EMPTY;
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 整数格式化，空值输出 {@link #EMPTY}。
     *
     * @param value 整数
     * @return 显示文本
     */
    public static String number(Integer value) {
        return value == null ? EMPTY : String.valueOf(value);
    }

    /**
     * 文本格式化（去首尾空白），空值输出 {@link #EMPTY}。
     *
     * @param value 文本
     * @return 显示文本
     */
    public static String text(String value) {
        if (value == null) {
            return EMPTY;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? EMPTY : trimmed;
    }

    /**
     * 团体口径中文名。
     *
     * @param scoreType MALE / FEMALE / TOTAL
     * @return 中文名
     */
    public static String scoreTypeName(String scoreType) {
        if (scoreType == null) {
            return EMPTY;
        }
        return switch (scoreType.toUpperCase()) {
            case "MALE" -> "男子团体";
            case "FEMALE" -> "女子团体";
            case "TOTAL" -> "总团体";
            default -> scoreType;
        };
    }

    /**
     * 证书类型中文名。
     *
     * @param certType RANK / RESULT / MEDAL / PARTICIPATION
     * @return 中文名
     */
    public static String certTypeName(String certType) {
        if (certType == null) {
            return EMPTY;
        }
        return switch (certType.toUpperCase()) {
            case "RANK" -> "获奖证书";
            case "RESULT" -> "成绩证书";
            case "MEDAL" -> "奖牌证书";
            case "PARTICIPATION" -> "参赛证书";
            default -> certType;
        };
    }

    /**
     * 证书状态中文名。
     *
     * @param status GENERATED / ISSUED / REVOKED
     * @return 中文名
     */
    public static String certStatusName(String status) {
        if (status == null) {
            return EMPTY;
        }
        return switch (status.toUpperCase()) {
            case "GENERATED" -> "已生成";
            case "ISSUED" -> "已签发";
            case "REVOKED" -> "已作废";
            default -> status;
        };
    }

    /**
     * 名次显示文本：优先库内 {@code rank_display}，为空时用名次序号。
     *
     * @param rankDisplay 库内名次显示文本
     * @param rankNo      名次序号
     * @return 显示文本，两者皆空时输出 {@link #EMPTY}
     */
    public static String rankDisplay(String rankDisplay, Integer rankNo) {
        if (rankDisplay != null && !rankDisplay.trim().isEmpty()) {
            return rankDisplay.trim();
        }
        return rankNo == null ? EMPTY : String.valueOf(rankNo);
    }
}
