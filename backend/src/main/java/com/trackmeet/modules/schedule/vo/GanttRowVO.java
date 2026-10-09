package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 甘特/时间轴行数据：一行 = 一个竞赛单元，bars 为该单元内的日程条目。
 *
 * @author TrackMeet
 */
@Data
public class GanttRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sessionId;
    private String sessionName;
    private LocalDate sessionDate;
    private String period;
    private Integer dayNo;
    private Long venueId;
    private String venueName;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer totalMinutes;
    private Integer usedMinutes;
    private Integer itemCount;
    private Integer isLocked;
    private String publishStatus;
    private Integer sortNo;
    private String remark;
    private List<Bar> bars = new ArrayList<>();

    /**
     * 时间轴条目。
     */
    @Data
    public static class Bar implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long scheduleId;
        private Long eventId;
        private String eventName;
        private String eventType;
        private Long roundId;
        private String roundCode;
        private Long venueId;
        private String venueName;
        private LocalDateTime planStartTime;
        private LocalDateTime planEndTime;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer durationMin;
        private Integer heatCount;
        private Integer memberCount;
        private Integer orderNo;
        private Integer isLocked;
        private String publishStatus;
        private Integer conflictFlag;
        private Integer conflictCount;
        private Boolean track;
    }
}
