package com.trackmeet.modules.print.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 打印留痕上报入参（{@code POST /api/print/logs}）。
 * <p>
 * 使用场景：浏览器端 A4 打印（{@code window.print()}）无法在服务端捕获，由前端上报一条留痕；
 * 服务端 PDF / Excel 生成则由后端内部直接落库（走同一套字段）。
 * <p>
 * 服务端自动补齐的字段：{@code operatorId} / {@code operatorName}（{@code SecurityUtils}）、
 * {@code ip}（{@code RequestUtils.getClientIp}，本 DTO 显式传值时优先使用）、
 * {@code printTime}（为空取当前时间）、{@code printCount}（为空取 1）、{@code status}（为空取 SUCCESS）。
 *
 * @author TrackMeet
 */
@Data
public class PrintLogSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报表类型：ORDER_BOOK / RESULTS / CALLING / LANES / POINTS / MEDALS / CERTIFICATES（也允许自定义值） */
    @NotBlank(message = "报表类型不能为空")
    @Size(max = 32, message = "报表类型最长 32 字符")
    private String reportType;

    /** 报表名称 */
    @Size(max = 100, message = "报表名称最长 100 字符")
    private String reportName;

    /** 打印查询参数快照（合法 JSON，超长时由服务端降级为摘要 JSON） */
    private String paramsJson;

    /** 格式：PDF / EXCEL / WORD（为空按 PDF 处理，入库统一大写） */
    @Size(max = 32, message = "格式最长 32 字符")
    private String format;

    /** 届次弱引用（可空；与当前届次上下文不一致时按越权处理） */
    private Long meetRefId;

    /** 生成文件 sys_file.id（可空） */
    private Long fileId;

    /** 使用的打印模板 ID（可空） */
    private Long templateId;

    /** 本次打印份数（为空按 1 处理） */
    @Min(value = 1, message = "打印份数至少 1")
    @Max(value = 999, message = "打印份数最多 999")
    private Integer printCount;

    /** 打印设备或终端标识 */
    @Size(max = 100, message = "打印设备最长 100 字符")
    private String printDevice;

    /** 客户端 IP（可空，为空时取请求 IP） */
    @Size(max = 64, message = "客户端 IP 最长 64 字符")
    private String ip;

    /** 状态：SUCCESS 成功 / FAILED 失败（为空按 SUCCESS 处理） */
    @Size(max = 32, message = "状态最长 32 字符")
    private String status;

    /** 失败原因（状态为 FAILED 时填写） */
    @Size(max = 255, message = "失败原因最长 255 字符")
    private String errorMsg;

    /** 打印发生时间（为空取当前时间） */
    private LocalDateTime printTime;
}
