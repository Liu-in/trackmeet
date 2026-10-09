package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 导入导出任务表 sys_import_task。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_import_task")
public class SysImportTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务名称，如 运动员导入 */
    @TableField("task_name")
    private String taskName;

    /** 业务类型：ATHLETE / REGISTRATION / RESULT */
    @TableField("biz_type")
    private String bizType;

    /** 方向：IMPORT 导入 / EXPORT 导出 */
    @TableField("task_direction")
    private String taskDirection;

    /** 源文件 sys_file.id */
    @TableField("file_id")
    private Long fileId;

    /** 模式：INSERT 仅新增 / UPSERT 覆盖 / VALIDATE 仅校验 */
    @TableField("import_mode")
    private String importMode;

    /** 总记录数 */
    @TableField("total_count")
    private Integer totalCount;

    /** 成功条数 */
    @TableField("success_count")
    private Integer successCount;

    /** 失败条数 */
    @TableField("fail_count")
    private Integer failCount;

    /** 进度百分比 0-100 */
    @TableField("progress")
    private Integer progress;

    /** 错误明细文件 sys_file.id */
    @TableField("error_file_id")
    private Long errorFileId;

    /** 状态：PENDING / RUNNING / SUCCESS / PARTIAL / FAILED */
    @TableField("status")
    private String status;

    /** 发起人 sys_user.id */
    @TableField("operator_id")
    private Long operatorId;

    /** 开始执行时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /** 耗时（毫秒） */
    @TableField("cost_ms")
    private Integer costMs;

    /** 结果说明或失败摘要 */
    @TableField("message")
    private String message;
}
