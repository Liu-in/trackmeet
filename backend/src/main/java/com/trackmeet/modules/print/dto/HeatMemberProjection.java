package com.trackmeet.modules.print.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 组内人员投影：分组内的一名参赛者（或接力队），并带上道次、田赛出场顺序与种子成绩。
 * <p>
 * 字段来自 {@code heat_member} 左连 {@code lane}、{@code field_order}、{@code athlete}、{@code unit}：
 * <ul>
 *     <li>径赛道次：优先 {@code lane.lane_no}，为空时用冗余列 {@code heat_member.lane_no}；</li>
 *     <li>田赛顺序：优先 {@code field_order.order_no}，为空时用 {@code heat_member.order_no}；</li>
 *     <li>种子成绩：优先原文 {@code *_raw}，为空时用数值 + 单位。</li>
 * </ul>
 *
 * @author TrackMeet
 */
@Data
public class HeatMemberProjection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组内人员 ID（heat_member.id） */
    private Long heatMemberId;

    /** 分组 ID（heat_member.heat_id） */
    private Long heatId;

    /** 日程条目 ID（heat_member.schedule_id） */
    private Long scheduleId;

    /** 运动员 ID（heat_member.athlete_id；接力取队长，可为空） */
    private Long athleteId;

    /** 参赛单位 ID（heat_member.unit_id） */
    private Long unitId;

    /** 号码布号（heat_member.bib_no） */
    private String bibNo;

    /** 组内出发/出场顺序号（heat_member.order_no） */
    private Integer orderNo;

    /** 是否种子选手（heat_member.is_seed）：0 否 / 1 是 */
    private Integer seedFlag;

    /** 参赛状态（heat_member.status）：NOT_CALLED/CALLED/PRESENT/ABSENT/WITHDRAW/DNS */
    private String memberStatus;

    /** 是否弃权/弃赛（heat_member.is_scratch）：0 否 / 1 是 */
    private Integer scratchFlag;

    /** 冗余道次号（heat_member.lane_no，lane 表无记录时兜底） */
    private Integer laneNoRaw;

    /** 报名最好成绩数值（heat_member.seed_result） */
    private BigDecimal seedResult;

    /** 报名成绩原文（heat_member.seed_result_raw，如 11"23） */
    private String seedResultRaw;

    /** 成绩单位（heat_member.seed_result_unit）：s/m/cm/分 */
    private String seedResultUnit;

    /** 运动员姓名（athlete.athlete_name） */
    private String athleteName;

    /** 运动员所属组别 ID（athlete.group_id） */
    private Long athleteGroupId;

    /** 参赛单位名称（unit.unit_name） */
    private String unitName;

    /** 参赛单位简称（unit.short_name） */
    private String unitShortName;

    /** 道次号（lane.lane_no，径赛权威道次） */
    private Integer laneNo;

    /** 道次分配方式（lane.assign_mode） */
    private String assignMode;

    /** 是否最优道次（lane.is_best_lane）：0 否 / 1 是 */
    private Integer bestLaneFlag;

    /** 田赛出场顺序号（field_order.order_no） */
    private Integer fieldOrderNo;

    /** 田赛分组号（field_order.group_no） */
    private Integer fieldGroupNo;

    /** 田赛报名最好成绩数值（field_order.best_seed） */
    private BigDecimal bestSeed;

    /** 田赛最好成绩原文（field_order.best_seed_raw） */
    private String bestSeedRaw;

    /** 田赛最好成绩单位（field_order.best_seed_unit） */
    private String bestSeedUnit;
}
