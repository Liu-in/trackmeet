package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 场地简要信息投影（跨模块取数：venue）。
 *
 * @author TrackMeet
 */
@Data
public class VenueBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private String venueName;
    private String venueCode;
    private String venueType;
    private Integer laneCount;
    private String laneOrderJson;
    private Integer isWindMeasured;
    private Integer isElectricTiming;
    private String status;
}
