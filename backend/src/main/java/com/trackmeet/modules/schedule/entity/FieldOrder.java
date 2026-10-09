package com.trackmeet.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 田赛出场顺序表 field_order：按成绩/抽签/单位/号码排序与免跳、弃权标记。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("field_order")
public class FieldOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 排序方式：按成绩 */
    public static final String MODE_SEED = "SEED";
    /** 排序方式：随机抽签 */
    public static final String MODE_RANDOM = "RANDOM";
    /** 排序方式：按单位 */
    public static final String MODE_UNIT = "UNIT";
    /** 排序方式：按号码 */
    public static final String MODE_BIB = "BIB";
    /** 排序方式：按顺序 */
    public static final String MODE_ORDER = "ORDER";

    /** 未锁定 */
    public static final Integer UNLOCKED = 0;
    /** 已锁定（重排时保持本顺序） */
    public static final Integer LOCKED = 1;

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 分组 ID heat.id */
    @NotNull(message = "分组不能为空")
    @TableField("heat_id")
    private Long heatId;

    /** 组内人员 ID heat_member.id */
    @NotNull(message = "组内人员不能为空")
    @TableField("heat_member_id")
    private Long heatMemberId;

    /** 日程条目 ID schedule.id */
    @NotNull(message = "日程条目不能为空")
    @TableField("schedule_id")
    private Long scheduleId;

    /** 项目 ID event.id，区分跳高/跳远/铅球等 */
    @TableField("event_id")
    private Long eventId;

    /** 出场顺序号，从 1 开始 */
    @NotNull(message = "出场顺序号不能为空")
    @TableField("order_no")
    private Integer orderNo;

    /** 田赛分组号（人数多时分批出场） */
    @TableField("group_no")
    private Integer groupNo;

    /** 排序方式：SEED 按成绩 / DRAW 抽签 / UNIT 按单位 / ORDER 按号码 */
    @Size(max = 32, message = "排序方式长度不能超过 32")
    @TableField("order_mode")
    private String orderMode;

    /** 报名最好成绩数值，用于排序比较 */
    @TableField("best_seed")
    private BigDecimal bestSeed;

    /** 报名最好成绩原文，用于显示 */
    @Size(max = 16, message = "最好成绩原文长度不能超过 16")
    @TableField("best_seed_raw")
    private String bestSeedRaw;

    /** 成绩单位：m 米 / cm 厘米 / 分 */
    @Size(max = 16, message = "成绩单位长度不能超过 16")
    @TableField("best_seed_unit")
    private String bestSeedUnit;

    /** 起跳高度/起始重量（m 或 kg） */
    @TableField("start_height")
    private BigDecimal startHeight;

    /** 免跳次数累计（PASS） */
    @TableField("pass_count")
    private Integer passCount;

    /** 是否弃权/弃赛：0 否 / 1 是（出场时跳过） */
    @TableField("is_scratch")
    private Integer isScratch;

    /** 是否本轮免跳：0 否 / 1 是 */
    @TableField("is_pass")
    private Integer isPass;

    /** 是否锁定：0 否 / 1 是（重排时保持本顺序） */
    @TableField("is_locked")
    private Integer isLocked;

    /** 锁定人 sys_user.id */
    @TableField("locked_by")
    private Long lockedBy;

    /** 锁定时间 */
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /** 顺序被调整次数 */
    @TableField("move_count")
    private Integer moveCount;

    /** 编排批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
