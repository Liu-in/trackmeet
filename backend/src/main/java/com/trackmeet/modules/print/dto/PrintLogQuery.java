package com.trackmeet.modules.print.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 打印留痕分页查询条件：{@code reportType} / {@code format} / {@code status} / 日期区间 / {@code keyword}。
 * <p>
 * 日期区间两套写法都支持（同时存在时 {@code beginTime}/{@code endTime} 优先）：
 * <ol>
 *     <li>{@code startDate} + {@code endDate}：{@code yyyy-MM-dd}，按自然日闭区间（结束日含 23:59:59）；</li>
 *     <li>{@code beginTime} + {@code endTime}：字符串，服务层宽容解析 {@code yyyy-MM-dd}、
 *         {@code yyyy-MM-dd HH:mm}、{@code yyyy-MM-dd HH:mm:ss}（含 ISO 的 {@code T} 分隔）三种写法。</li>
 * </ol>
 * 说明：精确时间用 {@code String} 接收而非 {@code LocalDateTime}，避免 {@code @DateTimeFormat} 对
 * {@code LocalDateTime} 只认单一 pattern 而导致「传日期即 400」；字段名与前端 {@code api/print.ts}
 * 的 {@code PrintLogQuery} 一致。
 * <p>
 * {@code sys_print_log} 为系统表（无 {@code meet_id}），届次筛选使用 {@code meetRefId} 弱引用；
 * 未显式传值时默认按当前届次上下文（{@code MeetContext}）过滤。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PrintLogQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 留痕报表类型：ORDER_BOOK / RESULTS / ... （可空；兼容传入 URL 键 order-book） */
    private String reportType;

    /** 输出格式：PDF / EXCEL / WORD（可空） */
    private String format;

    /** 状态：SUCCESS / FAILED（可空） */
    private String status;

    /** 届次弱引用（可空；为空时取当前届次上下文） */
    private Long meetRefId;

    /** 打印时间起（自然日，含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 打印时间止（自然日，含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 打印时间起（含）：支持 {@code yyyy-MM-dd} 与 {@code yyyy-MM-dd HH:mm[:ss]} 两种写法；优先于 startDate */
    private String beginTime;

    /** 打印时间止（含）：支持 {@code yyyy-MM-dd} 与 {@code yyyy-MM-dd HH:mm[:ss]} 两种写法；优先于 endDate */
    private String endTime;
}
