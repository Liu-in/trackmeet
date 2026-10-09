package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * event_round 轮次表：项目内的比赛轮次定义与编排计划值。
 * <p>
 * 字段与 {@code sql/03-schema-event-schedule.sql} 的 {@code event_round} 表逐列一致。
 * {@code plan_*} 列为编排计划值，规则值以 {@link EventRoundRule} 为准。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event_round")
public class EventRound extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 轮次码：及格赛 */
    public static final String CODE_QUALIFY = "QUALIFY";
    /** 轮次码：预赛 */
    public static final String CODE_PRE = "PRE";
    /** 轮次码：次赛 */
    public static final String CODE_R1 = "R1";
    /** 轮次码：复赛 */
    public static final String CODE_R2 = "R2";
    /** 轮次码：半决赛 */
    public static final String CODE_SEMI = "SEMI";
    /** 轮次码：决赛 */
    public static final String CODE_FINAL = "FINAL";

    /** 状态：已计划 */
    public static final String STATUS_PLANNED = "PLANNED";
    /** 状态：就绪 */
    public static final String STATUS_READY = "READY";
    /** 状态：进行中 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 状态：已完成 */
    public static final String STATUS_FINISHED = "FINISHED";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 所属项目 ID，关联 event.id */
    @TableField("event_id")
    private Long eventId;

    /** 轮次码：QUALIFY/PRE/R1/R2/SEMI/FINAL */
    @TableField("round_code")
    private String roundCode;

    /** 轮次名称（如「预赛」「决赛」） */
    @TableField("round_name")
    private String roundName;

    /** 轮次顺序号，从 1 起 */
    @TableField("round_no")
    private Integer roundNo;

    /** 是否决赛轮：0 否 / 1 是 */
    @TableField("is_final")
    private Integer isFinal;

    /** 计划分组数 */
    @TableField("plan_heat_count")
    private Integer planHeatCount;

    /** 每组计划人数 */
    @TableField("plan_heat_size")
    private Integer planHeatSize;

    /** 计划晋级总人数 */
    @TableField("plan_advance_count")
    private Integer planAdvanceCount;

    /** 计划每组晋级人数 */
    @TableField("plan_advance_group_top")
    private Integer planAdvanceGroupTop;

    /** 计划递补人数（剩余最好成绩） */
    @TableField("plan_best_loser_count")
    private Integer planBestLoserCount;

    /** 本轮实际参与人数（快照） */
    @TableField("entry_count")
    private Integer entryCount;

    /** 计划比赛日期 */
    @TableField("round_date")
    private LocalDate roundDate;

    /** 状态：PLANNED 已计划 / READY 就绪 / RUNNING 进行中 / FINISHED 已完成 */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
