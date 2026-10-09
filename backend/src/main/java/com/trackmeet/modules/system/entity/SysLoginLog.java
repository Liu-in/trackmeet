package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录日志表 sys_login_log。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class SysLoginLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户 sys_user.id，失败可为空 */
    @TableField("user_id")
    private Long userId;

    /** 登录名快照 */
    @TableField("username")
    private String username;

    /** 类型：LOGIN 登录 / LOGOUT 登出 / REFRESH 刷新令牌 */
    @TableField("login_type")
    private String loginType;

    /** 1 成功，0 失败 */
    @TableField("success")
    private Integer success;

    /** 失败原因 */
    @TableField("fail_reason")
    private String failReason;

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

    /** 设备类型：PC / MOBILE / PAD */
    @TableField("device_type")
    private String deviceType;

    /** 会话标识，用于强制下线 */
    @TableField("session_id")
    private String sessionId;

    /** 登录/登出发生时间 */
    @TableField("login_time")
    private LocalDateTime loginTime;
}
