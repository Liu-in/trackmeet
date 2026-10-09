package com.trackmeet.modules.athlete.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * registration 报名表：运动员 × 项目的报名关系。
 * <p>
 * 个人项目 1 行 1 人（{@code athlete_id} 非空），接力项目 1 行 N 人（{@code is_relay = 1}，
 * {@code athlete_id} 为空，队员明细落在 relay_member）。
 * 字段与 {@code sql/02-schema-meet-athlete.sql} 的 registration 建表语句逐列一致；
 * 通用字段（id/create_by/create_time/update_by/update_time/deleted/version）继承自 {@link BaseEntity}。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("registration")
public class Registration extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：待审 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：审核通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：退回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 报名范围：全程 */
    public static final String ROUND_SCOPE_WHOLE = "WHOLE";
    /** 报名范围：只报决赛 */
    public static final String ROUND_SCOPE_FINAL_ONLY = "FINAL_ONLY";

    /** 种子来源：手工录入（默认值） */
    public static final String SEED_SOURCE_MANUAL = "MANUAL";

    /** 届次 ID（业务表多租户隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 报名项目 event.id */
    @TableField("event_id")
    private Long eventId;

    /** 个人项目运动员 ID，接力为空 */
    @TableField("athlete_id")
    private Long athleteId;

    /** 报名单位 ID */
    @TableField("unit_id")
    private Long unitId;

    /** 报名组别 ID */
    @TableField("group_id")
    private Long groupId;

    /** 报名范围 WHOLE 全程 / FINAL_ONLY 只报决赛 */
    @TableField("round_scope")
    private String roundScope;

    /** 是否接力项目报名：0 否 / 1 是（布尔列用 Integer） */
    @TableField("is_relay")
    private Integer isRelay;

    /** 接力队名（如「XX 单位 A 队」） */
    @TableField("relay_name")
    private String relayName;

    /** 同单位同项目接力队序号 */
    @TableField("relay_team_seq")
    private Integer relayTeamSeq;

    /** 种子成绩原文，如 11"23、1:02.45 */
    @TableField("seed_result")
    private String seedResult;

    /** 种子成绩可比较数值（秒/米） */
    @TableField("seed_value")
    private BigDecimal seedValue;

    /** 种子成绩单位：s 秒 / m 米 / cm 厘米 / pt 分 */
    @TableField("seed_result_unit")
    private String seedResultUnit;

    /** 种子来源：PB 个人最好 / SB 赛季最好 / LAST_MEET 上届成绩 / CUSTOM 手工录入 / MANUAL */
    @TableField("seed_source")
    private String seedSource;

    /** 上届成绩来源届次 meet.id */
    @TableField("seed_source_meet_id")
    private Long seedSourceMeetId;

    /** 种子成绩产生时间 */
    @TableField("seed_time")
    private LocalDateTime seedTime;

    /** 状态：PENDING 待审 / APPROVED 通过 / REJECTED 退回 / CANCELLED 取消 */
    @TableField("status")
    private String status;

    /** 退回原因 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 限报校验快照 JSON：超项/超人数/重复/组别不符 */
    @TableField("check_result_json")
    private String checkResultJson;

    /** 报名提交时间 */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /** 审核人 sys_user.id */
    @TableField("audit_by")
    private Long auditBy;

    /** 审核时间 */
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /** 幂等令牌，防重复提交 */
    @TableField("client_token")
    private String clientToken;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
