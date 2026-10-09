package com.trackmeet.modules.print.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 届次信息（秩序册封面/页眉）：日期统一 {@code yyyy-MM-dd} 字符串输出。
 *
 * @author TrackMeet
 */
@Data
public class MeetInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID */
    private Long meetId;

    /** 运动会名称 */
    private String meetName;

    /** 届次标识（如「2025年第12届」） */
    private String edition;

    /** 简称（如「第12届市中运会」，文件命名与页眉使用） */
    private String shortName;

    /** 比赛开始日期 yyyy-MM-dd */
    private String startDate;

    /** 比赛结束日期 yyyy-MM-dd */
    private String endDate;

    /** 举办地点 */
    private String place;

    /** 主办单位 */
    private String host;

    /** 承办单位 */
    private String organizer;
}
