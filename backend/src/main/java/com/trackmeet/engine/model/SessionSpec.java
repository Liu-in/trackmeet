package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 竞赛单元（自动编排的时间容器）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单元 ID */
    private Long sessionId;

    /** 单元名称 */
    private String sessionName;

    /** 单元日期 */
    private LocalDate date;

    /** 时段：MORNING / AFTERNOON / EVENING */
    private String period;

    /** 单元开始时刻 */
    private LocalTime startTime;

    /** 单元结束时刻 */
    private LocalTime endTime;

    /** 主场地（径赛跑道） */
    private Long mainVenueId;

    /** 可用田赛场地列表 */
    @Builder.Default
    private List<Long> fieldVenueIds = new ArrayList<>();

    /** 单元开始时刻 */
    public LocalDateTime startDateTime() {
        return LocalDateTime.of(date, startTime == null ? LocalTime.of(8, 0) : startTime);
    }

    /** 单元结束时刻 */
    public LocalDateTime endDateTime() {
        return LocalDateTime.of(date, endTime == null ? LocalTime.of(18, 0) : endTime);
    }

    /** 时段排序权重（上午 → 下午 → 晚上） */
    public int periodOrder() {
        if ("MORNING".equalsIgnoreCase(period)) {
            return 1;
        }
        if ("AFTERNOON".equalsIgnoreCase(period)) {
            return 2;
        }
        if ("EVENING".equalsIgnoreCase(period)) {
            return 3;
        }
        return 9;
    }
}
