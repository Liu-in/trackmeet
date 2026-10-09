package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目简要信息投影（跨模块取数：event）。
 *
 * @author TrackMeet
 */
@Data
public class EventBriefRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long eventId;
    private String eventName;
    private String eventType;
    private String subType;
    private String gender;
    private Integer isRelay;
    private Long groupId;
    private Integer sortNo;
    private Integer durationMin;
    private String status;
}
