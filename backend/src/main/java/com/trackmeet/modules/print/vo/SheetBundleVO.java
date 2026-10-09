package com.trackmeet.modules.print.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用「表」结构：把秩序册各表拍平为「表名 + 标题 + 表头 + 行数据」。
 * <p>
 * 前端据此渲染 A4 表格并直接 {@code window.print()} / 另存 PDF：
 * {@code rows} 中所有单元格均为字符串，空值由后端统一输出 {@code -}。
 *
 * @author TrackMeet
 */
@Data
public class SheetBundleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 表键：schedule / heat:{eventId}:{roundId} / lane:{heatId} / calling */
    private String sheetKey;

    /** 表名（同时作为 Excel Sheet 名与 A4 打印标题），如「总日程表」 */
    private String sheetName;

    /** 表头（列名，顺序即列序） */
    private List<String> headers = new ArrayList<>();

    /** 行数据（每行与 headers 一一对应，全部为字符串） */
    private List<List<String>> rows = new ArrayList<>();

    /**
     * 构造一个表结构。
     *
     * @param sheetKey  表键
     * @param sheetName 表名
     * @param headers   表头
     * @param rows      行数据
     * @return 表结构
     */
    public static SheetBundleVO of(String sheetKey, String sheetName,
                                   List<String> headers, List<List<String>> rows) {
        SheetBundleVO bundle = new SheetBundleVO();
        bundle.setSheetKey(sheetKey);
        bundle.setSheetName(sheetName);
        bundle.setHeaders(headers == null ? new ArrayList<>() : headers);
        bundle.setRows(rows == null ? new ArrayList<>() : rows);
        return bundle;
    }

    /**
     * 行数（便于前端分页判断）。
     *
     * @return 行数
     */
    public int rowCount() {
        return rows == null ? 0 : rows.size();
    }
}
