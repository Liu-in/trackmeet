package com.trackmeet.modules.athlete.vo;

import com.trackmeet.modules.athlete.entity.RelayMember;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报名列表/详情视图：报名主体字段 + 运动员姓名、单位名、项目名、组别名 + 校验状态。
 * <p>
 * 字段名与前端契约严格一致（{@code seedUnit} 对应列 {@code seed_result_unit}，
 * {@code memberNames} 为逗号分隔的接力队员姓名）。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 校验状态：通过（无 ERROR 项） */
    public static final String CHECK_STATUS_PASS = "PASS";
    /** 校验状态：未通过（存在 ERROR 项） */
    public static final String CHECK_STATUS_FAIL = "FAIL";
    /** 校验状态：未校验（无校验快照） */
    public static final String CHECK_STATUS_UNCHECKED = "UNCHECKED";

    /** 报名 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 项目名称 */
    private String eventName;

    /** 运动员 ID（接力报名为空） */
    private Long athleteId;

    /** 运动员姓名（接力报名为空，队员看 members/memberNames） */
    private String athleteName;

    /** 号码布 */
    private String bibNo;

    /** 单位 ID */
    private Long unitId;

    /** 单位名称 */
    private String unitName;

    /** 组别 ID */
    private Long groupId;

    /** 组别名称 */
    private String groupName;

    /** 是否接力项目报名：0 否 / 1 是 */
    private Integer isRelay;

    /** 接力队名 */
    private String relayName;

    /** 状态码：PENDING/APPROVED/REJECTED/CANCELLED */
    private String status;

    /** 状态中文名 */
    private String statusName;

    /** 退回原因 */
    private String rejectReason;

    /** 种子成绩原文 */
    private String seedResult;

    /** 种子成绩可比较数值 */
    private BigDecimal seedValue;

    /** 种子成绩单位 */
    private String seedUnit;

    /** 种子来源 */
    private String seedSource;

    /** 报名提交时间 */
    private LocalDateTime submitTime;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 限报校验快照 JSON */
    private String checkResultJson;

    /** 接力队员姓名（逗号分隔） */
    private String memberNames;

    /** 校验状态：PASS/FAIL/UNCHECKED */
    private String checkStatus;

    /** 校验摘要：优先第一条 ERROR，其次第一条 WARN */
    private String checkMessage;

    /** 报名范围 WHOLE/FINAL_ONLY */
    private String roundScope;

    /** 同单位同项目接力队序号 */
    private Integer relayTeamSeq;

    /** 种子成绩产生时间 */
    private LocalDateTime seedTime;

    /** 上届成绩来源届次 */
    private Long seedSourceMeetId;

    /** 审核人 */
    private Long auditBy;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;

    /** 接力队员明细（详情/列表按需返回） */
    private List<RelayMember> members;
}
