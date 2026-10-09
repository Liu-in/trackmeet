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
 * 组内人员表 heat_member：一名（或一支接力队）在某组中的参赛身份与出发顺序，
 * 是检录与成绩录入的衔接点。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("heat_member")
public class HeatMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 参赛状态：未检录 */
    public static final String STATUS_NOT_CALLED = "NOT_CALLED";

    /** 未锁定 */
    public static final Integer UNLOCKED = 0;
    /** 已锁定 */
    public static final Integer LOCKED = 1;

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 分组 ID heat.id */
    @NotNull(message = "分组不能为空")
    @TableField("heat_id")
    private Long heatId;

    /** 日程条目 ID schedule.id，便于检录与成绩查询 */
    @NotNull(message = "日程条目不能为空")
    @TableField("schedule_id")
    private Long scheduleId;

    /** 报名 ID registration.id */
    @NotNull(message = "报名记录不能为空")
    @TableField("registration_id")
    private Long registrationId;

    /** 运动员 ID athlete.id；接力取队长 */
    @TableField("athlete_id")
    private Long athleteId;

    /** 参赛单位 ID unit.id */
    @TableField("unit_id")
    private Long unitId;

    /** 号码布号，冗余自 athlete.bib_no */
    @Size(max = 32, message = "号码布号长度不能超过 32")
    @TableField("bib_no")
    private String bibNo;

    /** 组别 ID meet_group.id，用于分组均衡校验 */
    @TableField("group_id")
    private Long groupId;

    /** 组内出发/出场顺序号 */
    @TableField("order_no")
    private Integer orderNo;

    /** 是否种子选手：0 否 / 1 是（用于种子分散） */
    @TableField("is_seed")
    private Integer isSeed;

    /** 是否弃权/弃赛：0 否 / 1 是 */
    @TableField("is_scratch")
    private Integer isScratch;

    /** 参赛状态：同 calling.status */
    @Size(max = 32, message = "参赛状态长度不能超过 32")
    @TableField("status")
    private String status;

    /** 报名最好成绩数值，用于排序比较 */
    @TableField("seed_result")
    private BigDecimal seedResult;

    /** 报名成绩原文，用于显示（如 11"23） */
    @Size(max = 16, message = "报名成绩原文长度不能超过 16")
    @TableField("seed_result_raw")
    private String seedResultRaw;

    /** 成绩单位：s 秒 / m 米 / cm 厘米 / 分 */
    @Size(max = 16, message = "成绩单位长度不能超过 16")
    @TableField("seed_result_unit")
    private String seedResultUnit;

    /** 道次号（径赛冗余，便于检录表输出） */
    @TableField("lane_no")
    private Integer laneNo;

    /** 本人在组内的位置是否锁定：0 否 / 1 是 */
    @TableField("is_locked")
    private Integer isLocked;

    /** 锁定时间 */
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /** 编排批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 备注，如递补来源说明 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
