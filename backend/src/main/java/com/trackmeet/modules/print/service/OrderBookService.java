package com.trackmeet.modules.print.service;

import com.trackmeet.modules.print.dto.OrderBookQuery;
import com.trackmeet.modules.print.dto.PrintQuery;
import com.trackmeet.modules.print.vo.OrderBookVO;
import com.trackmeet.modules.print.vo.SheetBundleVO;

import java.io.OutputStream;
import java.util.List;

/**
 * 秩序册/分组表/分道表/检录表输出服务。
 * <p>
 * 数据装配策略：一次查询把「单元 + 日程 + 项目 + 轮次 + 场地 + 组别」装入内存 Map，
 * 再按需批量装配分组与道次（不做逐条查询），保证四张表口径一致、无 N+1。
 *
 * @author TrackMeet
 */
public interface OrderBookService {

    /**
     * 秩序册（届次信息 + 四张表的结构化行 + 便于 A4 渲染的表结构）。
     *
     * @param query 查询条件（可为 null，等价于默认条件）
     * @return 秩序册
     */
    OrderBookVO getOrderBook(OrderBookQuery query);

    /**
     * 总日程表（单张表，按日期 + 时段 + 时间排序）。
     *
     * @param query 查询条件
     * @return 表结构列表（恒为一个元素）
     */
    List<SheetBundleVO> listScheduleSheets(PrintQuery query);

    /**
     * 分组表（按项目 + 轮次拆分为多张表，表内按组次、道次排序）。
     *
     * @param query 查询条件
     * @return 表结构列表
     */
    List<SheetBundleVO> listHeatSheets(PrintQuery query);

    /**
     * 分道表（径赛/接力项目按轮次逐组列出 1~N 道，空道补占位行）。
     *
     * @param query 查询条件
     * @return 表结构列表（每张表对应一个分组）
     */
    List<SheetBundleVO> listLaneSheets(PrintQuery query);

    /**
     * 检录表（含检录时间、检录处、项目、轮次、组次、号码、姓名、单位、道次，按检录时间排序）。
     *
     * @param query 查询条件（可按 sessionId 或 venueId 过滤）
     * @return 表结构列表（恒为一个元素）
     */
    List<SheetBundleVO> listCallingSheets(PrintQuery query);

    /**
     * 写出秩序册 Excel（4 个 Sheet：总日程表 / 分组表 / 分道表 / 检录表）。
     * <p>
     * 调用方负责设置响应头（Content-Type、Content-Disposition）与关闭/刷新流。
     *
     * @param orderBook    秩序册数据（由 {@link #getOrderBook(OrderBookQuery)} 产生）
     * @param outputStream 输出流
     */
    void writeOrderBookExcel(OrderBookVO orderBook, OutputStream outputStream);

    /**
     * 生成秩序册 Excel 文件名：{@code 秩序册_第12届市中运会_20251018.xlsx}。
     * <p>
     * 取名优先级：届次简称 → 届次标识 → 运动会名称；日期取届次开始日期（{@code yyyyMMdd}），
     * 缺失时取当天。
     *
     * @param orderBook 秩序册数据
     * @return 文件名（已过滤 Windows 非法字符）
     */
    String buildOrderBookFileName(OrderBookVO orderBook);
}
