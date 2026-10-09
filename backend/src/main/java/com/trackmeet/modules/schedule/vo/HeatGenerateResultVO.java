package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分组分道生成结果视图对象。
 * <p>
 * {@code heats} 中每一项的 {@code members} 即组内成员（按 {@code order_no} 升序）；
 * {@code lanes} 为本次生成/分配的道次结果（未分配道次时为空列表）。
 *
 * @author TrackMeet
 */
@Data
public class HeatGenerateResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分组结果（含组内成员） */
    private List<HeatVO> heats = new ArrayList<>();

    /** 道次分配结果 */
    private List<LaneVO> lanes = new ArrayList<>();

    /** 是否已落库（false 为纯预览） */
    private Boolean applied;

    /** 日程条目 ID */
    private Long scheduleId;

    /** 分组方式：SERPENT / SEED / RANDOM / UNIT */
    private String drawMode;

    /** 道次分配方式：SEED / DRAW / ORDER（为空表示未分配道次） */
    private String laneMode;

    /** 分组数 */
    private Integer heatCount;

    /** 参赛人数（报名条目数） */
    private Integer participantCount;
}
