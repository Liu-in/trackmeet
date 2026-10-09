package com.trackmeet.modules.announcement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 大屏届次信息 VO：{@code meetName / meetShortName / venue / dateRange / status}，
 * 另附届次标识与起止日期原始值，便于大屏自行排版。
 *
 * @author TrackMeet
 */
@Data
public class ScreenMeetInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID */
    private Long meetId;

    /** 运动会名称 */
    private String meetName;

    /** 运动会简称（大屏标题优先使用） */
    private String meetShortName;

    /** 届次标识，如「2025年第19届」 */
    private String edition;

    /** 场馆 / 举办地点（取自 {@code meet.place}） */
    private String venue;

    /** 比赛开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 比赛结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 日期区间显示文本，如 2025-05-01 ~ 2025-05-03 */
    private String dateRange;

    /** 届次状态码：DRAFT / REG_OPEN / REG_CLOSED / SCHEDULING / SCHEDULED / RUNNING / FINISHED / ARCHIVED */
    private String status;

    /** 届次状态显示名 */
    private String statusText;
}
