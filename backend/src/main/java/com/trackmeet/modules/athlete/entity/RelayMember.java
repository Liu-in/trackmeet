package com.trackmeet.modules.athlete.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * relay_member 接力队员表：接力报名下的队员与棒次明细。
 * <p>
 * 字段与 {@code sql/02-schema-meet-athlete.sql} 的 relay_member 建表语句逐列一致。
 * 该实体同时作为报名请求中 {@code members} 数组的元素载体（前端只传 athleteId 与 legNo）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("relay_member")
public class RelayMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：正常 */
    public static final String STATUS_NORMAL = "NORMAL";
    /** 状态：已换人 */
    public static final String STATUS_CHANGED = "CHANGED";
    /** 状态：已取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 届次 ID（业务表多租户隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 接力报名记录 registration.id */
    @TableField("registration_id")
    private Long registrationId;

    /** 队员运动员 ID */
    @TableField("athlete_id")
    private Long athleteId;

    /** 队员姓名冗余，便于打印 */
    @TableField("athlete_name")
    private String athleteName;

    /** 棒次号，1 起 */
    @TableField("leg_no")
    private Integer legNo;

    /** 队员所属单位 ID */
    @TableField("unit_id")
    private Long unitId;

    /** 队员所属组别 ID */
    @TableField("group_id")
    private Long groupId;

    /** 队员号码布（冗余快照） */
    @TableField("bib_no")
    private String bibNo;

    /** 是否替补队员：0 否 / 1 是 */
    @TableField("is_reserve")
    private Integer isReserve;

    /** 状态：NORMAL 正常 / CHANGED 已换人 / CANCELLED 已取消 */
    @TableField("status")
    private String status;

    /** 换人/调整原因 */
    @TableField("change_reason")
    private String changeReason;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
