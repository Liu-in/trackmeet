package com.trackmeet.modules.print.vo;

import java.util.List;

/**
 * 可转换为报表行的 VO（供 OrderBookServiceImpl 统一出表）。
 *
 * @author TrackMeet
 */
public interface RowConvertible {

    /**
     * 转换为一行单元格（全部为字符串，空值用 - 表示）。
     */
    List<String> toRow();
}
