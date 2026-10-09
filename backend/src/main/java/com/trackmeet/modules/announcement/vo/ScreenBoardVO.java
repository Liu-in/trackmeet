package com.trackmeet.modules.announcement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 大屏聚合 VO：一次请求返回大屏轮播所需的全部数据。
 * <p>
 * 字段与交付约定一致：{@code meetInfo}（届次信息）、{@code announcements}（已发布且推送大屏的公告）、
 * {@code medals}（奖牌榜前 20）、{@code teamScores}（总团体前 20）、{@code latestResults}
 * （已审核有效成绩最近 20 条）、{@code config}（大屏配置）、{@code updatedAt}（服务端刷新时间）、
 * {@code message}（提示语）。
 *
 * @author TrackMeet
 */
@Data
public class ScreenBoardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID */
    private Long meetId;

    /** 届次信息（届次名 / 简称 / 场馆 / 日期区间 / 状态） */
    private ScreenMeetInfoVO meetInfo;

    /** 大屏公告（已发布 + screen_show=1 + 未失效，置顶优先） */
    private List<AnnouncementVO> announcements = new ArrayList<>();

    /** 奖牌榜（sort_score 降序前 20） */
    private List<ScreenMedalRowVO> medals = new ArrayList<>();

    /** 团体总分（score_type=TOTAL 降序前 20） */
    private List<ScreenTeamScoreRowVO> teamScores = new ArrayList<>();

    /** 最新成绩（audit_status=APPROVED 且 status=VALID，update_time 降序前 20） */
    private List<ScreenResultRowVO> latestResults = new ArrayList<>();

    /** 大屏配置（无配置时为默认值：轮播 15 秒、展示模块全开） */
    private ScreenConfigVO config;

    /** 服务端刷新时间（大屏用于展示「最后更新」与断网提示） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 提示语（如令牌校验结果、数据条数摘要） */
    private String message;
}
