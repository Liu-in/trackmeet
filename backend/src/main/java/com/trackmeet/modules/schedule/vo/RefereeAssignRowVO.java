package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 裁判分工投影（跨模块取数：referee_assignment），为裁判冲突检测提供人员集合。
 *
 * @author TrackMeet
 */
@Data
public class RefereeAssignRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long scheduleId;
    private Long refereeId;
    private String dutyCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
