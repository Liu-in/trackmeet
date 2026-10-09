package com.trackmeet.modules.result.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * result 成绩表：一名运动员（或一支接力队）在一个日程条目（项目 × 轮次）上的最终成绩记录，
 * 是名次、积分、晋级、纪录与证书的唯一数据源。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code result} 表逐列一致；
 * 通用字段（id、create_by、create_time、update_by、update_time、deleted、version）继承自 {@link BaseEntity}。
 * <p>
 * 成绩双列建模：{@link #resultRaw}（原文，显示用，如 {@code 11"23}、{@code 1:02.45}）
 * 与 {@link #resultValue}（数值，排序用，径赛为秒、田赛为米、全能总分为分）；
 * 无效状态（DNS/DNF/DSQ/NM/PASS/SCRATCH）时 {@code result_value} 置空且不参与名次。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("result")
public class Result extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /* ============================ 成绩状态 ============================ */

    /** 成绩状态：有效 */
    public static final String STATUS_VALID = "VALID";
    /** 成绩状态：未出发 */
    public static final String STATUS_DNS = "DNS";
    /** 成绩状态：未完成 */
    public static final String STATUS_DNF = "DNF";
    /** 成绩状态：取消资格 */
    public static final String STATUS_DSQ = "DSQ";
    /** 成绩状态：无有效成绩 */
    public static final String STATUS_NM = "NM";
    /** 成绩状态：免跳 */
    public static final String STATUS_PASS = "PASS";
    /** 成绩状态：弃权 */
    public static final String STATUS_SCRATCH = "SCRATCH";

    /* ============================ 审核状态 ============================ */

    /** 审核状态：草稿 */
    public static final String AUDIT_DRAFT = "DRAFT";
    /** 审核状态：待审（已提交） */
    public static final String AUDIT_SUBMITTED = "SUBMITTED";
    /** 审核状态：已审通过 */
    public static final String AUDIT_APPROVED = "APPROVED";
    /** 审核状态：驳回 */
    public static final String AUDIT_REJECTED = "REJECTED";

    /* ============================ 纪录层级 ============================ */

    /** 纪录层级：世界 */
    public static final String RECORD_WORLD = "WORLD";
    /** 纪录层级：亚洲 */
    public static final String RECORD_ASIA = "ASIA";
    /** 纪录层级：全国 */
    public static final String RECORD_NATION = "NATION";
    /** 纪录层级：省 */
    public static final String RECORD_PROVINCE = "PROVINCE";
    /** 纪录层级：市 */
    public static final String RECORD_CITY = "CITY";
    /** 纪录层级：赛会 */
    public static final String RECORD_MEET = "MEET";
    /** 纪录层级：组别 */
    public static final String RECORD_GROUP = "GROUP";

    /** 锁定标记：未锁定 */
    public static final Integer UNLOCKED = 0;
    /** 锁定标记：已锁定（须撤销审核才能改） */
    public static final Integer LOCKED = 1;
    /** 布尔真 */
    public static final Integer YES = 1;
    /** 布尔假 */
    public static final Integer NO = 0;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 日程条目 ID（schedule.id） */
    @TableField("schedule_id")
    private Long scheduleId;

    /** 所在分组 ID（heat.id） */
    @TableField("heat_id")
    private Long heatId;

    /** 组内人员 ID（heat_member.id） */
    @TableField("heat_member_id")
    private Long heatMemberId;

    /** 项目 ID（event.id） */
    @TableField("event_id")
    private Long eventId;

    /** 轮次 ID（event_round.id） */
    @TableField("round_id")
    private Long roundId;

    /** 报名记录 ID（registration.id） */
    @TableField("registration_id")
    private Long registrationId;

    /** 运动员 ID（athlete.id，接力为队首人） */
    @TableField("athlete_id")
    private Long athleteId;

    /** 参赛单位 ID（unit.id） */
    @TableField("unit_id")
    private Long unitId;

    /** 组别 ID（meet_group.id） */
    @TableField("group_id")
    private Long groupId;

    /** 道次号，田赛与长跑可为空 */
    @TableField("lane_no")
    @TableField(exist = false)
    private Integer heatNo;

    private Integer laneNo;

    /** 成绩原文（显示用），如 11"23、1:02.45 */
    @TableField("result_raw")
    private String resultRaw;

    /** 成绩数值（秒/米/分），用于排序比较；无效成绩为空 */
    @TableField("result_value")
    private BigDecimal resultValue;

    /** 成绩单位：s 秒 / m 米 / cm 厘米 / pt 分（全能总分与单项分） */
    @TableField("result_unit")
    private String resultUnit;

    /** 全能单项得分，非全能项为空 */
    @TableField("result_score")
    private BigDecimal resultScore;

    /** 田赛高度项最终过杆高度（米） */
    @TableField("final_height")
    private BigDecimal finalHeight;

    /** 名次序号，并列时取相同值 */
    @TableField("rank_no")
    private Integer rankNo;

    /** 名次显示文本，如 1、2（并列） */
    @TableField("rank_display")
    private String rankDisplay;

    /** 成绩状态：VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH */
    @TableField("status")
    private String status;

    /** 犯规类型码，取自犯规类型字典 */
    @TableField("foul_type")
    private String foulType;

    /** 判罚依据说明，DSQ 时必填 */
    @TableField("foul_reason")
    private String foulReason;

    /** 风速（m/s），顺风为正、逆风为负 */
    @TableField("wind_speed")
    private BigDecimal windSpeed;

    /** 风速是否合法：1 合法 / 0 超风速 */
    @TableField("is_wind_legal")
    private Integer isWindLegal;

    /** 是否破纪录候选：1 是 / 0 否 */
    @TableField("is_record_candidate")
    private Integer isRecordCandidate;

    /** 纪录是否已认定：1 已认定 / 0 未认定 */
    @TableField("is_record_confirmed")
    private Integer isRecordConfirmed;

    /** 对标纪录 ID（record.id） */
    @TableField("record_id")
    private Long recordId;

    /** 认定纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    @TableField("record_type")
    private String recordType;

    /** 是否个人最好成绩：1 是 / 0 否 */
    @TableField("is_pb")
    private Integer isPb;

    /** 是否个人赛季最好成绩：1 是 / 0 否 */
    @TableField("is_sb")
    private Integer isSb;

    /** 是否手工录入：1 手工录入 / 0 设备导入 */
    @TableField("is_manual")
    private Integer isManual;

    /** 全能归并标识，同一全能用同值 */
    @TableField("allround_group_id")
    private Long allroundGroupId;

    /** 审核状态：DRAFT/SUBMITTED/APPROVED/REJECTED */
    @TableField("audit_status")
    private String auditStatus;

    /** 审核人 ID（sys_user.id） */
    @TableField("audit_by")
    private Long auditBy;

    /** 审核时间 */
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /** 驳回原因，REJECTED 时必填 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 成绩是否锁定：1 锁定（须撤销审核才能改）/ 0 未锁定 */
    @TableField("is_locked")
    private Integer isLocked;

    /** 幂等令牌，重复提交返回首次结果 */
    @TableField("client_token")
    private String clientToken;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /**
     * 是否为需要置空成绩数值的无效状态。
     *
     * @param status 成绩状态
     * @return 无效状态（DNS/DNF/DSQ/NM/PASS/SCRATCH）返回 true
     */
    public static boolean isInvalidStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return switch (status) {
            case STATUS_DNS, STATUS_DNF, STATUS_DSQ, STATUS_NM, STATUS_PASS, STATUS_SCRATCH -> true;
            default -> false;
        };
    }

    /**
     * 是否已审核锁定。
     *
     * @return 已锁定返回 true
     */
    public boolean locked() {
        return LOCKED.equals(this.isLocked);
    }
}
