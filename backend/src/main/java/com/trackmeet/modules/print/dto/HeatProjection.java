package com.trackmeet.modules.print.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分组（heat）投影：日程条目下的一个比赛组，径赛为组次、田赛为出场批次。
 * <p>
 * 字段全部来自 {@code heat} 表投影；检录时间与检录处取自本表，
 * {@code call_time} 为空时由服务层用「日程计划开始时间 - 20 分钟」兜底。
 *
 * @author TrackMeet
 */
@Data
public class HeatProjection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分组 ID（heat.id） */
    private Long heatId;

    /** 日程条目 ID（heat.schedule_id） */
    private Long scheduleId;

    /** 组号，从 1 开始（heat.heat_no） */
    private Integer heatNo;

    /** 组名（heat.heat_name，如「预赛第1组」） */
    private String heatName;

    /** 本组可用道数/出场位数（heat.lane_count，分道表道数首选） */
    private Integer laneCount;

    /** 本组实际人数（heat.member_count） */
    private Integer memberCount;

    /** 检录时间（heat.call_time，可为空） */
    private LocalDateTime callTime;

    /** 检录地点（heat.call_place，可为空） */
    private String callPlace;

    /** 本组计划开始时间（heat.start_time） */
    private LocalDateTime heatStartTime;

    /** 本组计划结束时间（heat.end_time） */
    private LocalDateTime heatEndTime;

    /** 分组状态（heat.status）：PLANNED/CALLING/RUNNING/FINISHED */
    private String heatStatus;

    /** 是否锁定（heat.is_locked）：0 否 / 1 是 */
    private Integer lockedFlag;

    /** 分组备注（heat.remark） */
    private String heatRemark;
}
