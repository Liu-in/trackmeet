package com.trackmeet.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 日程条目表 schedule：某项目某轮次在某场地某时间窗内的比赛安排。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule")
public class Schedule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 编排来源：自动 */
    public static final String SOURCE_AUTO = "AUTO";
    /** 编排来源：手动 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 编排来源：导入 */
    public static final String SOURCE_IMPORT = "IMPORT";

    /** 发布状态：草稿 */
    public static final String PUBLISH_DRAFT = "DRAFT";
    /** 发布状态：已发布 */
    public static final String PUBLISH_PUBLISHED = "PUBLISHED";

    /** 未锁定 */
    public static final Integer UNLOCKED = 0;
    /** 已锁定（重排时跳过） */
    public static final Integer LOCKED = 1;

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 竞赛单元 ID schedule_session.id */
    @NotNull(message = "竞赛单元不能为空")
    @TableField("session_id")
    private Long sessionId;

    /** 项目 ID event.id */
    @NotNull(message = "项目不能为空")
    @TableField("event_id")
    private Long eventId;

    /** 轮次 ID event_round.id */
    @NotNull(message = "轮次不能为空")
    @TableField("round_id")
    private Long roundId;

    /** 比赛场地 ID venue.id */
    @TableField("venue_id")
    private Long venueId;

    /** 计划开始时间 */
    @TableField("plan_start_time")
    private LocalDateTime planStartTime;

    /** 计划结束时间 */
    @TableField("plan_end_time")
    private LocalDateTime planEndTime;

    /** 实际开始时间，现场回填 */
    @TableField("actual_start_time")
    private LocalDateTime actualStartTime;

    /** 实际结束时间，现场回填 */
    @TableField("actual_end_time")
    private LocalDateTime actualEndTime;

    /** 预计时长（分钟） */
    @TableField("duration_min")
    private Integer durationMin;

    /** 实际时长（分钟） */
    @TableField("actual_duration_min")
    private Integer actualDurationMin;

    /** 组数（田赛为轮次组数） */
    @TableField("heat_count")
    private Integer heatCount;

    /** 参赛人数/队数 */
    @TableField("member_count")
    private Integer memberCount;

    /** 单元内排序号，决定出场先后 */
    @TableField("order_no")
    private Integer orderNo;

    /** 编排来源：AUTO 自动 / MANUAL 手动 / IMPORT 导入 */
    @Size(max = 32, message = "编排来源长度不能超过 32")
    @TableField("source_type")
    private String sourceType;

    /** 编排批次号，同一次生成共享 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 是否锁定：0 否 / 1 是（重排时跳过锁定项） */
    @TableField("is_locked")
    private Integer isLocked;

    /** 锁定人 sys_user.id */
    @TableField("locked_by")
    private Long lockedBy;

    /** 锁定时间 */
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /** 被移动次数，拖拽调整累加 */
    @TableField("move_count")
    private Integer moveCount;

    /** 最近一次调整原因 */
    @Size(max = 100, message = "调整原因长度不能超过 100")
    @TableField("adjust_reason")
    private String adjustReason;

    /** 发布状态：DRAFT 草稿 / PUBLISHED 已发布 */
    @Size(max = 32, message = "发布状态长度不能超过 32")
    @TableField("publish_status")
    private String publishStatus;

    /** 发布时间 */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /** 发布人 sys_user.id */
    @TableField("publisher_id")
    private Long publisherId;

    /** 是否存在冲突：0 无 / 1 有（冲突检测后回填） */
    @TableField("conflict_flag")
    private Integer conflictFlag;

    /** 未处理冲突条数 */
    @TableField("conflict_count")
    private Integer conflictCount;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
