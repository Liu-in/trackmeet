package com.trackmeet.modules.schedule.service.support;

import java.util.List;
import java.util.function.Function;

/**
 * 顺序号重排工具。
 * <p>
 * {@code heat_member(heat_id, order_no)} 与 {@code field_order(heat_id, order_no)} 上都有唯一键，
 * 直接把顺序号往前挪会与「尚未处理的成员」撞唯一键。统一采用两段式写法：
 * 先把全部记录挪到 {@code +OFFSET} 的空档区间腾出 1..N，再按目标顺序回写，全程无冲突。
 *
 * @author TrackMeet
 */
public final class OrderResequencer {

    /** 腾挪偏移量（远大于任何合理顺序号，同时不超过 SMALLINT 上限） */
    public static final int OFFSET = 1000;

    private OrderResequencer() {
    }

    /**
     * 按给定顺序把顺序号重写为 1..N。
     *
     * @param ordered    已按目标顺序排好的列表
     * @param idGetter   主键取值器
     * @param noSetter   顺序号写入器（写库动作）
     * @param <T>        记录类型
     */
    public static <T> void resequence(List<T> ordered, Function<T, Long> idGetter,
                                      OrderWriter noSetter) {
        if (ordered == null || ordered.isEmpty()) {
            return;
        }
        for (T item : ordered) {
            noSetter.write(idGetter.apply(item), OFFSET);
        }
        int orderNo = 1;
        for (T item : ordered) {
            noSetter.write(idGetter.apply(item), orderNo++);
        }
    }

    /**
     * 顺序号写入回调。
     */
    @FunctionalInterface
    public interface OrderWriter {

        /**
         * 写入顺序号。
         *
         * @param id      记录主键
         * @param orderNo 顺序号
         */
        void write(Long id, Integer orderNo);
    }
}
