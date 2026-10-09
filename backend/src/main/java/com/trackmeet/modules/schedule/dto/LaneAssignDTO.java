package com.trackmeet.modules.schedule.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 道次分配入参：对若干分组按指定方式重新分配道次。
 *
 * @author TrackMeet
 */
@Data
public class LaneAssignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分组 ID 列表（为空时按 scheduleId 覆盖该日程全部分组） */
    private List<Long> heatIds;

    /** 日程条目 ID（heatIds 为空时生效） */
    private Long scheduleId;

    /** 分配方式：SEED / DRAW / ORDER / SERPENT，缺省 SEED */
    private String mode;

    /** 道数（缺省取场地 lane_count，其次 8） */
    private Integer laneCount;

    /** 抽签随机种子（保证可复现） */
    private Long randomSeed;
}
