package com.trackmeet.modules.print.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.R;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.print.dto.OrderBookQuery;
import com.trackmeet.modules.print.dto.PrintQuery;
import com.trackmeet.modules.print.service.OrderBookService;
import com.trackmeet.modules.print.vo.OrderBookVO;
import com.trackmeet.modules.print.vo.SheetBundleVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 秩序册/分组表/分道表/检录表输出接口。
 * <p>
 * 双通道输出（与《17-自动编排模块说明与测试用例》4.2 一致）：
 * <ol>
 *     <li>{@code format=json}（默认）→ 返回 {@code SheetBundleVO} 表结构，前端按 A4 渲染并 {@code window.print()}；</li>
 *     <li>{@code format=xlsx} → 直接输出含 4 个 Sheet（总日程表/分组表/分道表/检录表）的 xlsx 文件流。</li>
 * </ol>
 * 权限编码：秩序册/总日程/分组/分道为 {@code print:orderbook}，检录表为 {@code print:calling}。
 * 只读接口不记 {@code @OpLog}，仅显式导出入口记录一条导出日志。
 *
 * @author TrackMeet
 */
@Slf4j
@RestController
@RequestMapping("/api/print")
@RequiredArgsConstructor
@Validated
public class PrintController {

    /** Excel 响应类型 */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final OrderBookService orderBookService;

    /**
     * 秩序册：{@code format=xlsx} 输出 4 Sheet Excel 文件流，否则返回秩序册 JSON（届次信息 + 四张表）。
     *
     * @param query    查询条件（meetId/sessionId/groupId/eventId/unitId/venueId/publishOnly/format）
     * @param response HTTP 响应（Excel 输出时写文件流）
     * @return 秩序册 JSON（Excel 场景返回 null，响应体已写出）
     */
    @GetMapping("/order-book")
    @PreAuthorize("@perm.has('print:orderbook')")
    public R<OrderBookVO> orderBook(@Valid OrderBookQuery query, HttpServletResponse response) {
        OrderBookVO orderBook = orderBookService.getOrderBook(query);
        if (query.isXlsx()) {
            writeXlsx(orderBook, response);
            return null;
        }
        return R.ok(orderBook);
    }

    /**
     * 总日程表（单张表，按日期 + 时段 + 时间排序）。
     *
     * @param query 查询条件
     * @return 表结构列表（恒为一个元素）
     */
    @GetMapping("/order-book/schedule")
    @PreAuthorize("@perm.has('print:orderbook')")
    public R<List<SheetBundleVO>> scheduleSheets(@Valid PrintQuery query) {
        return R.ok(orderBookService.listScheduleSheets(query));
    }

    /**
     * 分组表（按「项目 × 轮次」拆分为多张表，表内按组次、道次排序）。
     *
     * @param query 查询条件
     * @return 表结构列表
     */
    @GetMapping("/heats")
    @PreAuthorize("@perm.has('print:orderbook')")
    public R<List<SheetBundleVO>> heatSheets(@Valid PrintQuery query) {
        return R.ok(orderBookService.listHeatSheets(query));
    }

    /**
     * 分道表（径赛/接力按分组逐组列出 1~N 道，空道补占位行）。
     *
     * @param query 查询条件
     * @return 表结构列表
     */
    @GetMapping("/lanes")
    @PreAuthorize("@perm.has('print:orderbook')")
    public R<List<SheetBundleVO>> laneSheets(@Valid PrintQuery query) {
        return R.ok(orderBookService.listLaneSheets(query));
    }

    /**
     * 检录表（检录时间优先分组检录时间，缺失时按计划开始时间提前 20 分钟兜底，按检录时间排序）。
     *
     * @param query 查询条件
     * @return 表结构列表（恒为一个元素）
     */
    @GetMapping("/calling-sheets")
    @PreAuthorize("@perm.has('print:calling')")
    public R<List<SheetBundleVO>> callingSheets(@Valid PrintQuery query) {
        return R.ok(orderBookService.listCallingSheets(query));
    }

    /**
     * 秩序册显式导出入口：与 {@code /order-book} 等价，{@code format=xlsx} 下载 Excel，否则返回 JSON。
     *
     * @param query    查询条件（{@code format=xlsx|json}）
     * @param response HTTP 响应（Excel 输出时写文件流）
     * @return 秩序册 JSON（Excel 场景返回 null，响应体已写出）
     */
    @GetMapping("/order-book/export")
    @PreAuthorize("@perm.has('print:orderbook')")
    @OpLog(module = "PRINT", action = "EXPORT", actionName = "导出秩序册", targetType = "print")
    public R<OrderBookVO> export(@Valid OrderBookQuery query, HttpServletResponse response) {
        OrderBookVO orderBook = orderBookService.getOrderBook(query);
        if (query.isXlsx()) {
            writeXlsx(orderBook, response);
            return null;
        }
        return R.ok(orderBook);
    }

    /**
     * 写出 Excel 文件流。
     * <p>
     * 先在内存中生成完整工作簿再设置响应头并整体写出：即使生成过程失败也不会向客户端
     * 输出半截损坏的 xlsx（此时由全局异常处理器返回统一 JSON）。文件名取自
     * {@link OrderBookService#buildOrderBookFileName}，按 RFC 5987 百分号编码写入
     * {@code Content-Disposition}。
     *
     * @param orderBook 秩序册数据
     * @param response  HTTP 响应
     */
    private void writeXlsx(OrderBookVO orderBook, HttpServletResponse response) {
        String fileName = orderBookService.buildOrderBookFileName(orderBook);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        orderBookService.writeOrderBookExcel(orderBook, buffer);
        byte[] content = buffer.toByteArray();

        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(content.length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=UTF-8''" + encodedName);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(content);
            outputStream.flush();
        } catch (IOException ex) {
            log.error("输出秩序册 Excel 失败：{}", fileName, ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "输出秩序册 Excel 失败：" + ex.getMessage(), ex);
        }
    }
}
