package com.trackmeet.modules.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * event 项目表：项目主数据 —— 类型、组别、性别、赛制、接力人数、限报与计分属性。
 * <p>
 * 字段与 {@code sql/03-schema-event-schedule.sql} 的 {@code event} 表逐列一致；
 * 通用字段（id、create_by、create_time、update_by、update_time、deleted、version）继承自 {@link BaseEntity}。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("event")
public class Event extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 项目类型：径赛 */
    public static final String TYPE_TRACK = "TRACK";
    /** 项目类型：田赛 */
    public static final String TYPE_FIELD = "FIELD";
    /** 项目类型：全能 */
    public static final String TYPE_ALLROUND = "ALLROUND";
    /** 项目类型：接力 */
    public static final String TYPE_RELAY = "RELAY";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 项目名称 */
    @TableField("event_name")
    private String eventName;

    /** 项目编码，届次内唯一 */
    @TableField("event_code")
    private String eventCode;

    /** 项目类型：TRACK 径赛 / FIELD 田赛 / ALLROUND 全能 / RELAY 接力 */
    @TableField("event_type")
    private String eventType;

    /** 项目子类型：SPRINT/MIDDLE/LONG/HURDLE/STEEPLE/WALK/JUMP/THROW/RELAY */
    @TableField("sub_type")
    private String subType;

    /** 所属组别 ID，关联 meet_group.id */
    @TableField("group_id")
    private Long groupId;

    /** 适用性别：MALE 男 / FEMALE 女 / MIXED 混合 */
    @TableField("gender")
    private String gender;

    /** 轮次数量 */
    @TableField("round_count")
    private Integer roundCount;

    /** 是否接力项目：0 否 / 1 是 */
    @TableField("is_relay")
    private Integer isRelay;

    /** 接力最少人数（默认 4） */
    @TableField("relay_min")
    private Integer relayMin;

    /** 接力最多人数（默认 4） */
    @TableField("relay_max")
    private Integer relayMax;

    /** 每单位每项限报人数（默认 3） */
    @TableField("max_per_unit")
    private Integer maxPerUnit;

    /** 接力得分是否双倍：0 否 / 1 是（关联 bonus_rule.factor） */
    @TableField("is_relay_double")
    private Integer isRelayDouble;

    /** 是否记录并判定风速：0 否 / 1 是 */
    @TableField("wind_applicable")
    private Integer windApplicable;

    /** 是否计入团体总分：0 否 / 1 是 */
    @TableField("is_team_score")
    private Integer isTeamScore;

    /** 项目预计时长（分钟） */
    @TableField("duration_min")
    private Integer durationMin;

    /** 采用名次分方案 ID，关联 score_rule.id */
    @TableField("score_rule_id")
    private Long scoreRuleId;

    /** 计量单位：s 秒 / m 米 / cm 厘米 / pt 分 */
    @TableField("unit_of_measure")
    private String unitOfMeasure;

    /** 成绩小数位，覆盖 sys_config 精度配置 */
    @TableField("result_precision")
    private Integer resultPrecision;

    /** 显示排序号 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
