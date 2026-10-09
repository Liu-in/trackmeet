package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 自动分组分道入参。
 *
 * @author TrackMeet
 */
@Data
public class HeatsGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分组方式：蛇形 */
    public static final String DRAW_SERPENT = "SERPENT";
    /** 分组方式：按种子成绩段 */
    public static final String DRAW_SEED = "SEED";
    /** 分组方式：随机抽签 */
    public static final String DRAW_RANDOM = "RANDOM";
    /** 分组方式：按单位 */
    public static final String DRAW_UNIT = "UNIT";

    /** 道次分配方式：按种子 */
    public static final String LANE_SEED = "SEED";
    /** 道次分配方式：抽签 */
    public static final String LANE_DRAW = "DRAW";
    /** 道次分配方式：按顺序 */
    public static final String LANE_ORDER = "ORDER";

    /** 日程条目 ID */
    @NotNull(message = "日程条目不能为空")
    private Long scheduleId;

    /** 分组方式：SERPENT / SEED / RANDOM / UNIT，缺省 SERPENT */
    @Size(max = 32, message = "分组方式长度不能超过 32")
    private String drawMode;

    /** 分组数（缺省按 ceil(人数 / 每组人数) 计算） */
    private Integer heatCount;

    /** 每组人数（缺省取场地 lane_count，其次 8） */
    private Integer heatSize;

    /** 同单位是否避开（缺省 true） */
    private Boolean avoidSameUnit;

    /** 道次分配方式：SEED / DRAW / ORDER，为空则不分配道次 */
    @Size(max = 32, message = "道次分配方式长度不能超过 32")
    private String laneMode;

    /** 道数（缺省取场地 lane_count，其次 8） */
    private Integer laneCount;

    /** 抽签随机种子（保证可复现，缺省按当前时间） */
    private Long randomSeed;

    /** 是否落库（缺省 true；false 仅返回预览） */
    private Boolean apply;
}
