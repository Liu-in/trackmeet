package com.trackmeet.modules.print.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * sys_print_log 打印留痕表：每次 PDF / Excel 生成（或前端打印上报）写一条记录，用于重打与审计。
 * <p>
 * 字段与 {@code sql/01-schema-system.sql} 的 {@code sys_print_log} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, report_type, report_name, params_json, format, meet_ref_id, file_id, template_id,
 * operator_id, operator_name, print_count, print_device, ip, status, error_msg, print_time,
 * create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * 说明：本表为系统表，<b>不参与 {@code meet_id} 多租户隔离</b>（{@code MybatisPlusConfig} 对 {@code sys_*}
 * 前缀表已忽略），届次仅以弱引用列 {@code meet_ref_id} 记录，筛选留痕时由服务层显式追加该条件。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_print_log")
public class PrintLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 输出格式：PDF */
    public static final String FORMAT_PDF = "PDF";
    /** 输出格式：EXCEL */
    public static final String FORMAT_EXCEL = "EXCEL";

    /** 状态：成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 状态：失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 报表类型（打印中心目录的 reportCode，如 ORDER_BOOK / RESULTS） */
    @TableField("report_type")
    private String reportType;

    /** 报表名称 */
    @TableField("report_name")
    private String reportName;

    /** 打印查询参数快照（合法 JSON，用于重打） */
    @TableField("params_json")
    private String paramsJson;

    /** 格式：PDF / EXCEL / WORD */
    @TableField("format")
    private String format;

    /** 届次弱引用（仅用于筛选留痕） */
    @TableField("meet_ref_id")
    private Long meetRefId;

    /** 生成文件 sys_file.id（本期不落地文件，暂为空） */
    @TableField("file_id")
    private Long fileId;

    /** 使用的打印模板 print_template.id */
    @TableField("template_id")
    private Long templateId;

    /** 操作人 sys_user.id */
    @TableField("operator_id")
    private Long operatorId;

    /** 操作人姓名快照 */
    @TableField("operator_name")
    private String operatorName;

    /** 本次打印份数 */
    @TableField("print_count")
    private Integer printCount;

    /** 打印设备或终端标识 */
    @TableField("print_device")
    private String printDevice;

    /** 客户端 IP */
    @TableField("ip")
    private String ip;

    /** 状态：SUCCESS 成功 / FAILED 失败 */
    @TableField("status")
    private String status;

    /** 失败原因 */
    @TableField("error_msg")
    private String errorMsg;

    /** 打印发生时间 */
    @TableField("print_time")
    private LocalDateTime printTime;

    /**
     * 是否成功（{@code status} 为空视为成功）。
     *
     * @return {@code true} 表示成功
     */
    public boolean success() {
        return status == null || STATUS_SUCCESS.equalsIgnoreCase(status.trim());
    }
}
