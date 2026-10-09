package com.trackmeet.modules.print.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.print.constant.PrintReportType;
import com.trackmeet.modules.print.dto.PrintLogQuery;
import com.trackmeet.modules.print.dto.PrintLogSaveDTO;
import com.trackmeet.modules.print.entity.PrintLog;
import com.trackmeet.modules.print.vo.PrintLogVO;

/**
 * 打印留痕服务：留痕分页查询与写入。
 * <p>
 * 写入口径（{@code sys_print_log}）：
 * <ul>
 *     <li>{@code report_type} 取报表目录的留痕类型（{@code ORDER_BOOK} / {@code RESULTS} / ...）；</li>
 *     <li>{@code params_json} 为打印查询参数快照（<b>始终是合法 JSON</b>，超长时降级为摘要 JSON）；</li>
 *     <li>{@code format} = {@code PDF} / {@code EXCEL}；{@code status} = {@code SUCCESS} / {@code FAILED}；</li>
 *     <li>{@code meet_ref_id} 为届次弱引用；{@code operator_id/operator_name} 取 {@code SecurityUtils}；
 *         {@code ip} 取 {@code RequestUtils.getClientIp}；{@code print_time} 取当前时间。</li>
 * </ul>
 * 失败留痕使用 {@code REQUIRES_NEW} 独立事务：即使外层渲染事务回滚，{@code FAILED} 记录也会落库。
 *
 * @author TrackMeet
 */
public interface PrintLogService extends IService<PrintLog> {

    /**
     * 打印留痕分页查询（条件：reportType / format / status / 日期区间 / keyword）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PrintLogVO> pageLogs(PrintLogQuery query);

    /**
     * 保存一条留痕（{@code POST /api/print/logs}）：服务端补齐操作人、IP、打印时间、状态与份数。
     *
     * @param dto 留痕入参
     * @param ip  请求 IP（DTO 未显式传 {@code ip} 时使用）
     * @return 留痕 ID
     */
    Long saveLog(PrintLogSaveDTO dto, String ip);

    /**
     * 记录一条成功留痕（服务端 PDF / Excel 生成完成后调用）。
     *
     * @param type       报表类型
     * @param format     输出格式（PDF / EXCEL）
     * @param params     打印参数快照对象（序列化为 {@code params_json}）
     * @param meetRefId  届次弱引用
     * @param templateId 使用的模板 ID（可空）
     * @param printCount 打印份数（可空，默认 1）
     * @param ip         请求 IP
     * @return 留痕 ID
     */
    Long recordSuccess(PrintReportType type, String format, Object params, Long meetRefId, Long templateId,
                       Integer printCount, String ip);

    /**
     * 记录一条失败留痕（{@code status = FAILED}）：内部吞掉所有异常，绝不影响主流程对外抛出的异常。
     *
     * @param type      报表类型
     * @param format    输出格式（PDF / EXCEL）
     * @param params    打印参数快照对象
     * @param meetRefId 届次弱引用
     * @param templateId 使用的模板 ID（可空）
     * @param ip        请求 IP
     * @param errorMsg  失败原因（服务层按 255 字符截断）
     */
    void recordFailure(PrintReportType type, String format, Object params, Long meetRefId, Long templateId,
                       String ip, String errorMsg);

    /**
     * 实体 → VO。
     *
     * @param entity 留痕实体
     * @return 留痕 VO
     */
    PrintLogVO toVO(PrintLog entity);
}
