package com.trackmeet.modules.result.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * wind_reading 风速读数表：风速仪读数留档，作为超风速标记与纪录认定的原始依据，一成绩一条。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code wind_reading} 表逐列一致。
 * 表上唯一键 {@code uk_result (result_id)} 要求「一成绩一条」，重复录入风速时按成绩覆盖同一条读数。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wind_reading")
public class WindReading extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 风向：逆风 */
    public static final String DIRECTION_HEAD = "HEAD";
    /** 风向：顺风 */
    public static final String DIRECTION_TAIL = "TAIL";
    /** 风向：侧风 */
    public static final String DIRECTION_CROSS = "CROSS";

    /** 布尔真 */
    public static final Integer YES = 1;
    /** 布尔假 */
    public static final Integer NO = 0;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 关联成绩 ID（result.id） */
    @TableField("result_id")
    private Long resultId;

    /** 日程条目 ID（schedule.id） */
    @TableField("schedule_id")
    private Long scheduleId;

    /** 分组 ID（heat.id） */
    @TableField("heat_id")
    private Long heatId;

    /** 项目 ID（event.id） */
    @TableField("event_id")
    private Long eventId;

    /** 风速读数（m/s），顺风为正、逆风为负 */
    @TableField("wind_speed")
    private BigDecimal windSpeed;

    /** 风向：HEAD 逆风 / TAIL 顺风 / CROSS 侧风 */
    @TableField("wind_direction")
    private String windDirection;

    /** 是否在合法上限内：1 合法 / 0 超风速 */
    @TableField("is_wind_legal")
    private Integer isWindLegal;

    /** 本次判定采用的合法上限值（m/s） */
    @TableField("legal_limit")
    private BigDecimal legalLimit;

    /** 风速仪设备编号 */
    @TableField("wind_device_no")
    private String windDeviceNo;

    /** 读数时间 */
    @TableField("read_time")
    private LocalDateTime readTime;

    /** 记录人 ID（sys_user.id） */
    @TableField("recorder_id")
    private Long recorderId;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
