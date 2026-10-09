package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 届次投影行：榜单/成绩册打印的标题信息来源（成绩册表头、证书签发单位与编号前缀）。
 * <p>
 * {@code meet} 为主数据表（无 {@code meet_id}），只按 {@code id} + {@code deleted} 查询。
 *
 * @author TrackMeet
 */
@Data
public class StandingMeetRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID */
    private Long id;

    /** 运动会名称 */
    private String meetName;

    /** 届次标识（如「2025年第19届」） */
    private String edition;

    /** 简称（大屏、证书使用） */
    private String shortName;

    /** 主办单位 */
    private String host;

    /** 承办单位 */
    private String organizer;

    /** 比赛开始日期 */
    private LocalDate startDate;

    /** 比赛结束日期 */
    private LocalDate endDate;

    /** 举办地点 */
    private String place;
}
