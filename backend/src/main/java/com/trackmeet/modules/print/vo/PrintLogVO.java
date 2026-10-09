package com.trackmeet.modules.print.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 打印留痕 VO：与 {@code sys_print_log} 列一一对应，供留痕列表与重打使用。
 *
 * @author TrackMeet
 */
@Data
public class PrintLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 留痕 ID */
    private Long id;

    /** 报表类型：ORDER_BOOK / RESULTS / CALLING / LANES / POINTS / MEDALS / CERTIFICATES */
    private String reportType;

    /** 报表名称 */
    private String reportName;

    /** 打印查询参数快照（JSON，可直接用于重打） */
    private String paramsJson;

    /** 格式：PDF / EXCEL / WORD */
    private String format;

    /** 届次弱引用 */
    private Long meetRefId;

    /** 生成文件 sys_file.id */
    private Long fileId;

    /** 使用的打印模板 ID */
    private Long templateId;

    /** 操作人 sys_user.id */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 本次打印份数 */
    private Integer printCount;

    /** 打印设备或终端标识 */
    private String printDevice;

    /** 客户端 IP */
    private String ip;

    /** 状态：SUCCESS / FAILED */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    /** 打印发生时间 */
    private LocalDateTime printTime;

    /** 记录创建时间 */
    private LocalDateTime createTime;
}
