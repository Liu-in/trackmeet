package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 田赛出场顺序视图对象。
 *
 * @author TrackMeet
 */
@Data
public class FieldOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private Long heatId;
    private Long heatMemberId;
    private Long scheduleId;
    private Long eventId;
    private Integer orderNo;
    private Integer groupNo;
    private String orderMode;
    private BigDecimal bestSeed;
    private String bestSeedRaw;
    private String bestSeedUnit;
    private BigDecimal startHeight;
    private Integer passCount;
    private Integer isScratch;
    private Integer isPass;
    private Integer isLocked;
    private Long lockedBy;
    private LocalDateTime lockTime;
    private Integer moveCount;
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
    private String eventName;
}
