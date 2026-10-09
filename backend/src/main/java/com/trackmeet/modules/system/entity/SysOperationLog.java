package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志表 sys_operation_log。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_operation_log")
public class SysOperationLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 操作人 sys_user.id，匿名写 0 */
    @TableField("user_id")
    private Long userId;

    /** 操作人登录名快照 */
    @TableField("username")
    private String username;

    /** 业务模块 */
    @TableField("module")
    private String module;

    /** 动作码 */
    @TableField("action")
    private String action;

    /** 动作中文名 */
    @TableField("action_name")
    private String actionName;

    /** 目标类型，如 meet */
    @TableField("target_type")
    private String targetType;

    /** 目标主键 */
    @TableField("target_id")
    private Long targetId;

    /** 修改前值快照（JSON 文本） */
    @TableField("before_json")
    private String beforeJson;

    /** 修改后值快照（JSON 文本） */
    @TableField("after_json")
    private String afterJson;

    /** 请求参数摘要 */
    @TableField("params_summary")
    private String paramsSummary;

    /** 请求链路 ID */
    @TableField("request_id")
    private String requestId;

    /** 客户端 IP */
    @TableField("ip")
    private String ip;

    /** IP 归属地 */
    @TableField("ip_region")
    private String ipRegion;

    /** User-Agent 原文 */
    @TableField("ua")
    private String ua;

    /** 操作系统 */
    @TableField("os")
    private String os;

    /** 浏览器 */
    @TableField("browser")
    private String browser;

    /** 处理耗时（毫秒） */
    @TableField("cost_ms")
    private Integer costMs;

    /** 结果：SUCCESS 成功 / FAIL 失败 */
    @TableField("result")
    private String result;

    /** 失败异常摘要 */
    @TableField("error_msg")
    private String errorMsg;

    /** 操作发生时间 */
    @TableField("operate_time")
    private LocalDateTime operateTime;

    /** 1 已归档，可被清理任务删除 */
    @TableField("is_archived")
    private Integer isArchived;
}
