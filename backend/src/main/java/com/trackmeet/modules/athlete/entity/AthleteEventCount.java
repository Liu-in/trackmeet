package com.trackmeet.modules.athlete.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * athlete_event_count 每人项数计数表：承载「每人限报项数」校验的实时计数。
 * <p>
 * 字段与 {@code sql/02-schema-meet-athlete.sql} 的 athlete_event_count 建表语句逐列一致。
 * 该表为派生数据，刷新策略为「先物理删除该运动员行，再按实时统计插入」的幂等写法。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("athlete_event_count")
public class AthleteEventCount extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上限来源：届次级规则 */
    public static final String COUNT_SOURCE_MEET = "MEET";
    /** 上限来源：项目级规则 */
    public static final String COUNT_SOURCE_EVENT = "EVENT";

    /** 届次 ID（业务表多租户隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 运动员 ID */
    @TableField("athlete_id")
    private Long athleteId;

    /** 所属单位 ID */
    @TableField("unit_id")
    private Long unitId;

    /** 所属组别 ID */
    @TableField("group_id")
    private Long groupId;

    /** 已报项数合计 */
    @TableField("event_count")
    private Integer eventCount;

    /** 个人项目项数 */
    @TableField("individual_event_count")
    private Integer individualEventCount;

    /** 接力项目项数 */
    @TableField("relay_event_count")
    private Integer relayEventCount;

    /** 全能项目项数 */
    @TableField("allround_event_count")
    private Integer allroundEventCount;

    /** 每人限报项数上限（生效值，null 或 0 视为不限） */
    @TableField("max_events")
    private Integer maxEvents;

    /** 剩余可报项数 */
    @TableField("remain_count")
    private Integer remainCount;

    /** 接力是否计入限报项数：0 不计入 / 1 计入 */
    @TableField("is_relay_counted")
    private Integer isRelayCounted;

    /** 是否超项：0 否 / 1 是 */
    @TableField("over_limit")
    private Integer overLimit;

    /** 已报项目 ID 明细快照 JSON */
    @TableField("event_ids_json")
    private String eventIdsJson;

    /** 上限来源：MEET 届次 / EVENT 项目 / DICT 字典 */
    @TableField("count_source")
    private String countSource;

    /** 最近一次报名时间 */
    @TableField("last_register_time")
    private LocalDateTime lastRegisterTime;

    /** 计数刷新时间 */
    @TableField("check_time")
    private LocalDateTime checkTime;
}
