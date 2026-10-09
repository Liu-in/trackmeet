package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分组视图对象：径赛为组次、田赛为出场批次，含组内人员与道次/出场顺序。
 *
 * @author TrackMeet
 */
@Data
public class HeatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private Long scheduleId;
    private Integer heatNo;
    private String heatName;
    private Integer laneCount;
    private Integer memberCount;
    private Integer seedCount;
    private LocalDateTime callTime;
    private String callPlace;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer isLocked;
    private Long lockedBy;
    private LocalDateTime lockTime;
    private String sourceType;
    private String batchNo;
    private String drawMode;
    private String remark;

    /** 组内人员 */
    private List<HeatMemberVO> members;
    /** 道次分配结果 */
    private List<LaneVO> lanes;
    /** 田赛出场顺序 */
    private List<FieldOrderVO> fieldOrders;
}
