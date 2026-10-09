package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 道次视图对象：既是查询结果，也作为「手动改道次」接口的请求体（取 laneNo）。
 *
 * @author TrackMeet
 */
@Data
public class LaneVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private Long heatId;
    private Long heatMemberId;
    private Long scheduleId;
    private Integer laneNo;
    private String assignMode;
    private String laneOrderSource;
    private String laneOrderJson;
    private Integer seedRank;
    private Integer isBestLane;
    private Integer isLocked;
    private Long lockedBy;
    private LocalDateTime lockTime;
    private String swapGroup;
    private Integer swapFromLane;
    private String swapReason;
    private LocalDateTime swapTime;
    private Long swapBy;
    private String batchNo;
    private String remark;

    /** 展示字段：组内人员 */
    private Long registrationId;
    private Long athleteId;
    private String athleteName;
    private String bibNo;
    private Long unitId;
    private String unitName;
    private Integer heatNo;
    private Long eventId;
    private String eventName;
}
