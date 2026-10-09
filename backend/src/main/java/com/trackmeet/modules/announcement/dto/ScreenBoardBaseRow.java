package com.trackmeet.modules.announcement.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 大屏基础投影：{@code meet} 届次信息 + 该届次的活动大屏配置（{@code screen_config}）。
 * <p>
 * 用一条 SQL 把「届次信息」与「大屏配置」一起取出（LEFT JOIN 子查询取
 * {@code is_default DESC, id ASC} 的第一行），使大屏聚合一次请求内的取数控制在 5 条以内。
 * 配置不存在时 {@code configId} 及配置列全部为 {@code null}，由服务层回落到默认配置
 * （轮播 15 秒、展示模块全开）。
 *
 * @author TrackMeet
 */
@Data
public class ScreenBoardBaseRow implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------------- 届次（meet） ----------------

    /** 届次 ID */
    private Long meetId;

    /** 运动会名称 */
    private String meetName;

    /** 运动会简称（大屏使用） */
    private String meetShortName;

    /** 届次标识，如「2025年第19届」 */
    private String edition;

    /** 举办地点（{@code meet.place}，大屏作为场馆显示） */
    private String venue;

    /** 比赛开始日期 */
    private LocalDate startDate;

    /** 比赛结束日期 */
    private LocalDate endDate;

    /** 届次状态：DRAFT / REG_OPEN / REG_CLOSED / SCHEDULING / SCHEDULED / RUNNING / FINISHED / ARCHIVED */
    private String meetStatus;

    // ---------------- 大屏配置（screen_config） ----------------

    /** 大屏配置 ID（无配置时为 null） */
    private Long configId;

    /** 大屏方案名称 */
    private String screenName;

    /** 方案编码 */
    private String screenCode;

    /** 展示模块顺序与开关 JSON */
    private String modulesJson;

    /** 自动刷新间隔（秒） */
    private Integer refreshInterval;

    /** 主题 JSON（含保留键 accessToken） */
    private String themeJson;

    /** 分辨率 */
    private String resolution;

    /** 模块轮播间隔（秒） */
    private Integer rotateSeconds;

    /** 播放模式：CYCLE / FIXED */
    private String playMode;

    /** 大屏状态：ENABLED / DISABLED */
    private String configStatus;

    /** 是否默认方案：1 是 / 0 否 */
    private Integer isDefault;

    /** 配置备注 */
    private String configRemark;
}
