package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 竞赛单元视图对象。
 *
 * @author TrackMeet
 */
@Data
public class SessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long meetId;
    private String sessionName;
    private LocalDate sessionDate;
    private Integer dayNo;
    private String period;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long venueId;
    private String venueName;

    /** 同时可用场地 ID 列表 */
    private List<Long> venueIds;
    /** 同时可用场地名称列表（展示用） */
    private List<String> venueNames;

    private String venueIdsJson;
    private Integer totalMinutes;
    private Integer usedMinutes;
    private Integer itemCount;
    private Integer isLocked;
    private Long lockedBy;
    private LocalDateTime lockTime;
    private String publishStatus;
    private LocalDateTime publishTime;
    private Long publisherId;
    private Integer sortNo;
    private String batchNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
