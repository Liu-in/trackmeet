package com.trackmeet.modules.announcement.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 大屏配置 VO。
 * <p>
 * <b>令牌说明</b>：交付要求令牌字段名为 {@code accessToken}（{@code screen_config.access_token}），
 * 但建表脚本中并无该列，故实际持久化在 {@code theme_json} 的保留键 {@code accessToken} 上，
 * 本 VO 对外仍以 {@code accessToken} 字段返回（32 位 UUID 去横线）。
 *
 * @author TrackMeet
 */
@Data
public class ScreenConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 大屏配置 ID（无配置且未自动落库时为 null） */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 大屏方案名称 */
    private String screenName;

    /** 方案编码，届次内唯一 */
    private String screenCode;

    /** 展示模块顺序与开关（JSON 文本） */
    private String modulesJson;

    /** 自动刷新间隔（秒） */
    private Integer refreshInterval;

    /** 主题色、字体、背景与 Logo 墙（JSON 文本，含保留键 accessToken） */
    private String themeJson;

    /** 分辨率：1920x1080 / 3840x2160 */
    private String resolution;

    /** 模块轮播间隔（秒），默认 15 */
    private Integer rotateSeconds;

    /** 播放模式：CYCLE 轮播 / FIXED 固定 */
    private String playMode;

    /** 大屏状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 是否默认大屏方案：1 是 / 0 否 */
    private Integer isDefault;

    /** 大屏访问令牌（32 位，匿名大屏 /api/screen/public/board 使用） */
    private String accessToken;

    /** 备注 */
    private String remark;

    // ------------------------------------------------------------------
    // 与前端 `frontend/src/api/screen.ts` 的 ScreenConfig 字段名对齐的派生字段
    // （DDL 列名以 refresh_interval / modules_json / theme_json 为准，下列字段仅作同值别名）
    // ------------------------------------------------------------------

    /** 别名：自动刷新间隔（秒），与 {@link #refreshInterval} 同值 */
    private Integer refreshSeconds;

    /** 别名：是否显示公告模块：1 显示 / 0 隐藏（由 modulesJson 的 enabled.ANNOUNCEMENT 派生） */
    private Integer showAnnouncement;

    /** 别名：是否显示奖牌榜模块：1 显示 / 0 隐藏（由 modulesJson 的 enabled.MEDAL 派生） */
    private Integer showMedal;

    /** 别名：是否显示团体总分模块：1 显示 / 0 隐藏（由 modulesJson 的 enabled.TEAM_SCORE 派生） */
    private Integer showPoints;

    /** 别名：是否显示最新成绩模块：1 显示 / 0 隐藏（由 modulesJson 的 enabled.RESULT 派生） */
    private Integer showResult;

    /** 别名：主题模式：DARK 深色 / LIGHT 浅色（存于 themeJson 的 mode 键） */
    private String theme;
}
