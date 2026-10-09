package com.trackmeet.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 竞赛单元表 schedule_session：某半天（上午/下午/晚上）在一个场地上的比赛时段，日程条目的时间容器。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_session")
public class ScheduleSession extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 时段：上午 */
    public static final String PERIOD_MORNING = "MORNING";
    /** 时段：下午 */
    public static final String PERIOD_AFTERNOON = "AFTERNOON";
    /** 时段：晚上 */
    public static final String PERIOD_EVENING = "EVENING";

    /** 发布状态：草稿 */
    public static final String PUBLISH_DRAFT = "DRAFT";
    /** 发布状态：已发布 */
    public static final String PUBLISH_PUBLISHED = "PUBLISHED";

    /** 未锁定 */
    public static final Integer UNLOCKED = 0;
    /** 已锁定 */
    public static final Integer LOCKED = 1;

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 单元名称，如「第一天上午」 */
    @NotBlank(message = "单元名称不能为空")
    @Size(max = 100, message = "单元名称长度不能超过 100")
    @TableField("session_name")
    private String sessionName;

    /** 单元日期 */
    @NotNull(message = "单元日期不能为空")
    @TableField("session_date")
    private LocalDate sessionDate;

    /** 赛期第几天，从 1 开始 */
    @TableField("day_no")
    private Integer dayNo;

    /** 时段：MORNING 上午 / AFTERNOON 下午 / EVENING 晚上 */
    @NotBlank(message = "时段不能为空")
    @Size(max = 32, message = "时段长度不能超过 32")
    @TableField("period")
    private String period;

    /** 单元开始时刻 */
    @TableField("start_time")
    private LocalTime startTime;

    /** 单元结束时刻 */
    @TableField("end_time")
    private LocalTime endTime;

    /** 主场地 venue.id */
    @TableField("venue_id")
    private Long venueId;

    /** 同时可用场地 ID 列表（JSON 数组字符串） */
    @TableField("venue_ids_json")
    private String venueIdsJson;

    /** 单元可用总时长（分钟） */
    @TableField("total_minutes")
    private Integer totalMinutes;

    /** 已排入时长（分钟），编排后回填 */
    @TableField("used_minutes")
    private Integer usedMinutes;

    /** 单元内日程条目数 */
    @TableField("item_count")
    private Integer itemCount;

    /** 单元是否锁定：0 未锁定 / 1 已锁定（锁定后不接受自动排入） */
    @TableField("is_locked")
    private Integer isLocked;

    /** 锁定人 sys_user.id */
    @TableField("locked_by")
    private Long lockedBy;

    /** 锁定时间 */
    @TableField("lock_time")
    private LocalDateTime lockTime;

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

    /** 单元排序号，决定日程显示顺序 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 生成本单元的编排批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 备注，如开幕式占用说明 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
