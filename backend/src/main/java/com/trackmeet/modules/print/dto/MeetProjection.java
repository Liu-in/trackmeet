package com.trackmeet.modules.print.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 届次（meet）投影：秩序册封面/页眉信息。
 * <p>
 * 由 {@code PrintMapper.selectMeetInfo} 直接从 {@code meet} 表投影，
 * 不依赖其他模块的实体与 Mapper。
 *
 * @author TrackMeet
 */
@Data
public class MeetProjection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（meet.id） */
    private Long meetId;

    /** 运动会名称（meet.meet_name） */
    private String meetName;

    /** 届次标识（meet.edition） */
    private String edition;

    /** 简称（meet.short_name，用于文件名与页眉） */
    private String shortName;

    /** 比赛开始日期（meet.start_date） */
    private LocalDate startDate;

    /** 比赛结束日期（meet.end_date） */
    private LocalDate endDate;

    /** 举办地点（meet.place） */
    private String place;

    /** 主办单位（meet.host） */
    private String host;

    /** 承办单位（meet.organizer） */
    private String organizer;
}
