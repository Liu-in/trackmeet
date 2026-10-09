package com.trackmeet.modules.print.dto;

/**
 * 秩序册查询参数：字段与语义与 {@link PrintQuery} 完全一致，仅为接口可读性与后续扩展
 * （如印刷模板 ID、是否包含裁判安排表）保留的语义化子类。
 * <p>
 * {@code /api/print/order-book} 与 {@code /api/print/order-book/export} 使用本类接收参数。
 *
 * @author TrackMeet
 */
public class OrderBookQuery extends PrintQuery {

    private static final long serialVersionUID = 1L;
}
