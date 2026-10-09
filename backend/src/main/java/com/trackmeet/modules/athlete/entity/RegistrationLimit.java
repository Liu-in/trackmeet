package com.trackmeet.modules.athlete.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * registration_limit 报名限制计数表：按「单位 × 项目」维护限报快照与实时计数。
 * <p>
 * 字段与 {@code sql/02-schema-meet-athlete.sql} 的 registration_limit 建表语句逐列一致。
 * 该表为派生数据，刷新策略为「先物理删除本键行，再按实时统计插入」的幂等写法。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("registration_limit")
public class RegistrationLimit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 限值来源：届次级规则 */
    public static final String LIMIT_SOURCE_MEET = "MEET";
    /** 限值来源：项目级规则 */
    public static final String LIMIT_SOURCE_EVENT = "EVENT";
    /** 限值来源：字典 */
    public static final String LIMIT_SOURCE_DICT = "DICT";

    /** 届次 ID（业务表多租户隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 参赛单位 ID */
    @TableField("unit_id")
    private Long unitId;

    /** 项目 ID */
    @TableField("event_id")
    private Long eventId;

    /** 组别 ID */
    @TableField("group_id")
    private Long groupId;

    /** 本单位该项目已报人数（待审 + 已通过） */
    @TableField("registered_count")
    private Integer registeredCount;

    /** 每单位每项限报人数（生效值，null 或 0 视为不限） */
    @TableField("max_count")
    private Integer maxCount;

    /** 已报接力队数 */
    @TableField("relay_registered_count")
    private Integer relayRegisteredCount;

    /** 接力限报队数 */
    @TableField("relay_max_count")
    private Integer relayMaxCount;

    /** 剩余可报人数 */
    @TableField("remain_count")
    private Integer remainCount;

    /** 是否已达上限：0 否 / 1 是 */
    @TableField("is_full")
    private Integer isFull;

    /** 是否超限（含已批准超额）：0 否 / 1 是 */
    @TableField("over_limit")
    private Integer overLimit;

    /** 单位内各运动员项数明细快照 JSON */
    @TableField("athlete_event_count_json")
    private String athleteEventCountJson;

    /** 限值来源：MEET 届次 / EVENT 项目 / DICT 字典 */
    @TableField("limit_source")
    private String limitSource;

    /** 最近一次校验时间 */
    @TableField("limit_check_time")
    private LocalDateTime limitCheckTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
