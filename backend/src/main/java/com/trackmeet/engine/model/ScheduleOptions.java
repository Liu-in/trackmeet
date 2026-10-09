package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 自动编排参数（全部来自规则配置，不硬编码阈值）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 运动员兼项最小间隔（分钟） */
    @Builder.Default
    private int athleteIntervalMin = 30;

    /** 同项目轮次最小间隔（分钟），防止预赛与决赛间隔过短 */
    @Builder.Default
    private int minGapMin = 30;

    /** 田赛是否可与径赛并行 */
    @Builder.Default
    private boolean parallelFieldEvents = true;

    /** 单单元可同时使用的田赛场地数上限（<=0 表示不限） */
    @Builder.Default
    private int maxFieldVenues = 2;
}
