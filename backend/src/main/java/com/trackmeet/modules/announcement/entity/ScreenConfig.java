package com.trackmeet.modules.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * screen_config 大屏配置表：大屏展示方案的模块组合、刷新频率与主题。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code screen_config} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, meet_id, screen_name, screen_code, modules_json, refresh_interval, theme_json, resolution,
 * rotate_seconds, play_mode, status, is_default, remark,
 * create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * <p>
 * <b>关于大屏访问令牌</b>：交付要求以 {@code screen_config.access_token} 承载令牌，但
 * {@code sql/04-schema-result-standing.sql}（第 494~517 行）与
 * {@code docs/12-05-字段清单-成绩与统计输出层.md}（第 321~338 行）中
 * <b>均无 {@code access_token} 列</b>，且本模块不允许改动 SQL 脚本。为保持
 * 「实体列与 DDL 逐字一致」的硬约束，令牌改为持久化在既有的 {@code theme_json}
 * 列中的保留键 {@code accessToken} 上（见 {@code ScreenServiceImpl}），
 * 对外接口仍以 {@code accessToken} 字段返回；待 SQL 补列后仅需把该键切换为独立列。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("screen_config")
public class ScreenConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 大屏状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 大屏状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 播放模式：轮播 */
    public static final String PLAY_MODE_CYCLE = "CYCLE";
    /** 播放模式：固定 */
    public static final String PLAY_MODE_FIXED = "FIXED";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 大屏方案名称 */
    @TableField("screen_name")
    private String screenName;

    /** 方案编码，届次内唯一（UK(meet_id, screen_code)） */
    @TableField("screen_code")
    private String screenCode;

    /** 展示模块顺序与开关（JSON 文本，原样存取） */
    @TableField("modules_json")
    private String modulesJson;

    /** 自动刷新间隔（秒） */
    @TableField("refresh_interval")
    private Integer refreshInterval;

    /** 主题色、字体、背景与 Logo 墙（JSON 文本，原样存取；保留键 accessToken 存大屏访问令牌） */
    @TableField("theme_json")
    private String themeJson;

    /** 分辨率：1920x1080 / 3840x2160 */
    @TableField("resolution")
    private String resolution;

    /** 模块轮播间隔（秒） */
    @TableField("rotate_seconds")
    private Integer rotateSeconds;

    /** 播放模式：CYCLE 轮播 / FIXED 固定 */
    @TableField("play_mode")
    private String playMode;

    /** 大屏状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 是否默认大屏方案：1 是 / 0 否 */
    @TableField("is_default")
    private Integer isDefault;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
