package com.trackmeet.modules.announcement.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 大屏配置新增 / 修改入参。
 * <p>
 * {@code modulesJson} / {@code themeJson} 为原始 JSON 文本，服务层会校验合法性后原样入库
 * （列类型为 MySQL {@code JSON}）。{@code themeJson} 中的保留键 {@code accessToken}
 * 由服务端维护：请求未携带时沿用既有令牌，无既有令牌时自动生成。
 *
 * @author TrackMeet
 */
@Data
public class ScreenConfigSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填；PUT 修改时可不传，取配置原有届次并校验上下文） */
    private Long meetId;

    /** 大屏方案名称 */
    @Size(max = 100, message = "大屏方案名称最长 100 个字符")
    private String screenName;

    /** 方案编码，届次内唯一 */
    @Size(max = 64, message = "方案编码最长 64 个字符")
    private String screenCode;

    /** 展示模块顺序与开关（JSON 文本） */
    private String modulesJson;

    /** 自动刷新间隔（秒），缺省 15 */
    private Integer refreshInterval;

    /** 主题色、字体、背景与 Logo 墙（JSON 文本） */
    private String themeJson;

    /** 分辨率：1920x1080 / 3840x2160，缺省 1920x1080 */
    private String resolution;

    /** 模块轮播间隔（秒），缺省 15 */
    private Integer rotateSeconds;

    /** 播放模式：CYCLE 轮播 / FIXED 固定，缺省 CYCLE */
    private String playMode;

    /** 大屏状态：ENABLED 启用 / DISABLED 停用，缺省 ENABLED */
    private String status;

    /** 是否默认大屏方案：1 是 / 0 否，缺省 1 */
    private Integer isDefault;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 个字符")
    private String remark;

    // ------------------------------------------------------------------
    // 与前端 `frontend/src/api/screen.ts` 的 ScreenConfig 字段名对齐的别名入参
    // （同时传别名与正名时，别名后应用；DDL 列以正名为准）
    // ------------------------------------------------------------------

    /** 别名：自动刷新间隔（秒），等价于 {@link #refreshInterval} */
    private Integer refreshSeconds;

    /** 别名：是否显示公告模块：1 显示 / 0 隐藏（合并进 modulesJson 的 enabled.ANNOUNCEMENT） */
    private Integer showAnnouncement;

    /** 别名：是否显示奖牌榜模块：1 显示 / 0 隐藏（合并进 modulesJson 的 enabled.MEDAL） */
    private Integer showMedal;

    /** 别名：是否显示团体总分模块：1 显示 / 0 隐藏（合并进 modulesJson 的 enabled.TEAM_SCORE） */
    private Integer showPoints;

    /** 别名：是否显示最新成绩模块：1 显示 / 0 隐藏（合并进 modulesJson 的 enabled.RESULT） */
    private Integer showResult;

    /** 别名：主题模式：DARK 深色 / LIGHT 浅色（写入 themeJson 的 mode 键） */
    private String theme;
}
