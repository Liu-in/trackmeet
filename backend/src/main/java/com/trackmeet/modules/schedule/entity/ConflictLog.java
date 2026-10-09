package com.trackmeet.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 冲突检测记录表 conflict_log：覆盖兼项、场地、裁判、间隔四类。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conflict_log")
public class ConflictLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 冲突类型：运动员兼项 */
    public static final String TYPE_ATHLETE = "ATHLETE";
    /** 冲突类型：场地 */
    public static final String TYPE_VENUE = "VENUE";
    /** 冲突类型：裁判 */
    public static final String TYPE_REFEREE = "REFEREE";
    /** 冲突类型：间隔不足 */
    public static final String TYPE_INTERVAL = "INTERVAL";

    /** 级别：阻断 */
    public static final String LEVEL_ERROR = "ERROR";
    /** 级别：提醒 */
    public static final String LEVEL_WARN = "WARN";

    /** 未处理 */
    public static final Integer UNRESOLVED = 0;
    /** 已处理 */
    public static final Integer RESOLVED = 1;

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 冲突类型：ATHLETE 兼项 / VENUE 场地 / REFEREE 裁判 / INTERVAL 间隔不足 */
    @Size(max = 32, message = "冲突类型长度不能超过 32")
    @TableField("conflict_type")
    private String conflictType;

    /** 冲突级别：ERROR 阻断 / WARN 提醒 */
    @Size(max = 32, message = "冲突级别长度不能超过 32")
    @TableField("level")
    private String level;

    /** 主体日程条目 ID schedule.id */
    @TableField("schedule_id")
    private Long scheduleId;

    /** 关联冲突日程条目 ID schedule.id */
    @TableField("related_schedule_id")
    private Long relatedScheduleId;

    /** 主体分组 ID heat.id */
    @TableField("heat_id")
    private Long heatId;

    /** 关联分组 ID heat.id */
    @TableField("related_heat_id")
    private Long relatedHeatId;

    /** 涉及运动员 ID athlete.id */
    @TableField("athlete_id")
    private Long athleteId;

    /** 涉及报名 ID registration.id */
    @TableField("registration_id")
    private Long registrationId;

    /** 涉及裁判 ID referee.id */
    @TableField("referee_id")
    private Long refereeId;

    /** 涉及场地 ID venue.id */
    @TableField("venue_id")
    private Long venueId;

    /** 涉及竞赛单元 ID schedule_session.id */
    @TableField("session_id")
    private Long sessionId;

    /** 冲突区间开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 冲突区间结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 关联对象区间开始时间 */
    @TableField("related_start_time")
    private LocalDateTime relatedStartTime;

    /** 关联对象区间结束时间 */
    @TableField("related_end_time")
    private LocalDateTime relatedEndTime;

    /** 重叠分钟数，0 表示无重叠 */
    @TableField("overlap_minutes")
    private Integer overlapMinutes;

    /** 实际间隔分钟数，小于阈值即冲突 */
    @TableField("interval_minutes")
    private Integer intervalMinutes;

    /** 规则要求的兼项间隔分钟数 */
    @TableField("required_interval_min")
    private Integer requiredIntervalMin;

    /** 冲突详情：涉及人员、时间区间、场地、间隔分钟数（JSON 字符串） */
    @TableField("detail_json")
    private String detailJson;

    /** 触发本次检测的编排批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 检测时间 */
    @TableField("detect_time")
    private LocalDateTime detectTime;

    /** 是否已处理：0 未处理 / 1 已处理 */
    @TableField("resolved")
    private Integer resolved;

    /** 处理说明，如「已调整至下一单元」 */
    @Size(max = 100, message = "处理说明长度不能超过 100")
    @TableField("resolve_remark")
    private String resolveRemark;

    /** 处理人 sys_user.id */
    @TableField("resolve_by")
    private Long resolveBy;

    /** 处理时间 */
    @TableField("resolve_time")
    private LocalDateTime resolveTime;
}
