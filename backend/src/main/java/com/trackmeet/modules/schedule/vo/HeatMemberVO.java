package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 组内人员视图对象：既是查询结果，也作为「添加人员到组」接口的请求体。
 *
 * @author TrackMeet
 */
@Data
public class HeatMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private Long heatId;
    private Long scheduleId;
    private Long registrationId;
    private Long athleteId;
    private String athleteName;
    private Long unitId;
    private String unitName;
    private String bibNo;
    private Long groupId;
    private Integer orderNo;
    private Integer isSeed;
    private Integer isScratch;
    private String status;
    private BigDecimal seedResult;
    private String seedResultRaw;
    private String seedResultUnit;
    private Integer laneNo;
    private Integer isLocked;
    private LocalDateTime lockTime;
    private String batchNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
