package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 日程拖拽移动入参：目标单元、开始时间、场地与调整原因。
 *
 * @author TrackMeet
 */
@Data
public class ScheduleMoveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标竞赛单元 ID */
    private Long sessionId;

    /** 目标计划开始时间 */
    private LocalDateTime startTime;

    /** 目标场地 ID */
    private Long venueId;

    /** 调整原因 */
    @Size(max = 100, message = "调整原因长度不能超过 100")
    private String adjustReason;
}
