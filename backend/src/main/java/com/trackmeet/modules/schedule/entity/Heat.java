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
 * 分组表 heat：一个日程条目下的一个比赛组，径赛为组次、田赛为出场批次。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("heat")
public class Heat extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：已计划 */
    public static final String STATUS_PLANNED = "PLANNED";
    /** 状态：检录中 */
    public static final String STATUS_CALLING = "CALLING";
    /** 状态：比赛中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 状态：已完成 */
    public static final String STATUS_FINISHED = "FINISHED";

    /** 编排来源：自动 */
    public static final String SOURCE_AUTO = "AUTO";
    /** 编排来源：手动 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 未锁定 */
    public static final Integer UNLOCKED = 0;
    /** 已锁定（重排时跳过本组） */
    public static final Integer LOCKED = 1;

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 日程条目 ID schedule.id */
    @NotNull(message = "日程条目不能为空")
    @TableField("schedule_id")
    private Long scheduleId;

    /** 组号，从 1 开始 */
    @NotNull(message = "组号不能为空")
    @TableField("heat_no")
    private Integer heatNo;

    /** 组名，如「预赛第 1 组」 */
    @Size(max = 50, message = "组名长度不能超过 50")
    @TableField("heat_name")
    private String heatName;

    /** 本组可用道数/出场位数 */
    @TableField("lane_count")
    private Integer laneCount;

    /** 本组实际人数 */
    @TableField("member_count")
    private Integer memberCount;

    /** 本组种子选手数 */
    @TableField("seed_count")
    private Integer seedCount;

    /** 检录时间 */
    @TableField("call_time")
    private LocalDateTime callTime;

    /** 检录地点 */
    @Size(max = 100, message = "检录地点长度不能超过 100")
    @TableField("call_place")
    private String callPlace;

    /** 本组计划开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 本组计划结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 分组状态：PLANNED 已计划 / CALLING 检录中 / RUNNING 比赛中 / FINISHED 已完成 */
    @Size(max = 32, message = "状态长度不能超过 32")
    @TableField("status")
    private String status;

    /** 是否锁定：0 否 / 1 是（重排时跳过本组） */
    @TableField("is_locked")
    private Integer isLocked;

    /** 锁定人 sys_user.id */
    @TableField("locked_by")
    private Long lockedBy;

    /** 锁定时间 */
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /** 编排来源：AUTO 自动 / MANUAL 手动 / IMPORT 导入 */
    @Size(max = 32, message = "编排来源长度不能超过 32")
    @TableField("source_type")
    private String sourceType;

    /** 编排批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 分组方式：SEED 按种子 / DRAW 抽签 / SERPENT 蛇形 / ORDER 按顺序 */
    @Size(max = 32, message = "分组方式长度不能超过 32")
    @TableField("draw_mode")
    private String drawMode;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
