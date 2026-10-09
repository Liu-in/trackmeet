package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目参赛运动员投影（跨模块取数：registration × relay_member）。
 * 接力项目按队员展开为多行，用于冲突检测的运动人员集合。
 *
 * @author TrackMeet
 */
@Data
public class EventAthleteRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long eventId;
    private Long registrationId;
    private Long athleteId;
}
