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
 * 道次表 lane：径赛分组内的道次分配结果，含分配方式与交换留痕。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lane")
public class Lane extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 分配方式：按种子 */
    public static final String MODE_SEED = "SEED";
    /** 分配方式：抽签 */
    public static final String MODE_DRAW = "DRAW";
    /** 分配方式：蛇形 */
    public static final String MODE_SERPENT = "SERPENT";
    /** 分配方式：按顺序 */
    public static final String MODE_ORDER = "ORDER";

    /** 道次顺序来源：届次 */
    public static final String ORDER_SOURCE_MEET = "MEET";
    /** 道次顺序来源：项目 */
    public static final String ORDER_SOURCE_EVENT = "EVENT";
    /** 道次顺序来源：自定义 */
    public static final String ORDER_SOURCE_CUSTOM = "CUSTOM";

    /** 未锁定 */
    public static final Integer UNLOCKED = 0;
    /** 已锁定（重排时保持本道次不变） */
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

    /** 日程条目 ID schedule.id，便于按项目查分道表 */
    @NotNull(message = "日程条目不能为空")
    @TableField("schedule_id")
    private Long scheduleId;

    /** 道次号，从 1 开始 */
    @NotNull(message = "道次号不能为空")
    @TableField("lane_no")
    private Integer laneNo;

    /** 分配方式：SEED 按种子 / DRAW 抽签 / SERPENT 蛇形 / ORDER 按顺序 */
    @Size(max = 32, message = "分配方式长度不能超过 32")
    @TableField("assign_mode")
    private String assignMode;

    /** 道次顺序来源：MEET 届次 / EVENT 项目 / CUSTOM 自定义 */
    @Size(max = 32, message = "道次顺序来源长度不能超过 32")
    @TableField("lane_order_source")
    private String laneOrderSource;

    /** 本次实际使用的道次优先顺序快照（JSON 数组字符串） */
    @TableField("lane_order_json")
    private String laneOrderJson;

    /** 成绩排序位次，决定优先选道次序 */
    @TableField("seed_rank")
    private Integer seedRank;

    /** 是否最优道次（如 4 道）：0 否 / 1 是 */
    @TableField("is_best_lane")
    private Integer isBestLane;

    /** 是否锁定：0 否 / 1 是（重排时保持本道次不变） */
    @TableField("is_locked")
    private Integer isLocked;

    /** 锁定人 sys_user.id */
    @TableField("locked_by")
    private Long lockedBy;

    /** 锁定时间 */
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /** 交换批次号，同一次道次交换共享 */
    @Size(max = 64, message = "交换批次号长度不能超过 64")
    @TableField("swap_group")
    private String swapGroup;

    /** 交换前道次号 */
    @TableField("swap_from_lane")
    private Integer swapFromLane;

    /** 交换原因，如申诉调整 */
    @Size(max = 100, message = "交换原因长度不能超过 100")
    @TableField("swap_reason")
    private String swapReason;

    /** 交换时间 */
    @TableField("swap_time")
    private LocalDateTime swapTime;

    /** 交换操作人 sys_user.id */
    @TableField("swap_by")
    private Long swapBy;

    /** 编排批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
