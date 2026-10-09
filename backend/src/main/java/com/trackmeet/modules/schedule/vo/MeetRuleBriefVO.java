package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 届次级规则简要投影（跨模块取数：meet_rule_config）。
 * 仅取编排相关列，未配置时由服务层回退到系统默认值。
 *
 * @author TrackMeet
 */
@Data
public class MeetRuleBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long meetId;
    private Integer conflictIntervalMin;
    private Integer refereeIntervalMin;
    private Integer defaultLaneCount;
    private String defaultLaneOrderJson;
    private String laneAssignMode;
    private Integer fieldAttemptCount;
}
