package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩列表 / 详情视图：{@code result} 表全字段 + 运动员、单位、组别、项目、轮次、分组、日程展示字段。
 * <p>
 * 列名与 {@code sql/04-schema-result-standing.sql} 的 {@code result} 表逐字一致（自定义 SQL 以别名投影，
 * 由 {@code map-underscore-to-camel-case} 完成下划线到驼峰的映射）。
 *
 * @author TrackMeet
 */
@Data
public class ResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 日程条目 ID */
    private Long scheduleId;

    /** 分组 ID */
    private Long heatId;

    /** 组内人员 ID */
    private Long heatMemberId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 道次号 */
    private Integer laneNo;

    /** 成绩原文（显示用） */
    private String resultRaw;

    /** 成绩数值（秒/米/分） */
    private BigDecimal resultValue;

    /** 成绩单位：s/m/cm/pt */
    private String resultUnit;

    /** 全能单项得分 */
    private BigDecimal resultScore;

    /** 田赛高度项最终过杆高度（米） */
    private BigDecimal finalHeight;

    /** 名次序号 */
    private Integer rankNo;

    /** 名次显示文本 */
    private String rankDisplay;

    /** 成绩状态：VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH */
    private String status;

    /** 犯规类型码 */
    private String foulType;

    /** 判罚依据说明 */
    private String foulReason;

    /** 风速（m/s） */
    private BigDecimal windSpeed;

    /** 风速是否合法：1 合法 / 0 超风速 */
    private Integer isWindLegal;

    /** 是否破纪录候选：1 是 / 0 否 */
    private Integer isRecordCandidate;

    /** 纪录是否已认定：1 已认定 / 0 未认定 */
    private Integer isRecordConfirmed;

    /** 对标纪录 ID */
    private Long recordId;

    /** 认定纪录层级 */
    private String recordType;

    /** 是否个人最好成绩：1 是 / 0 否 */
    private Integer isPb;

    /** 是否个人赛季最好成绩：1 是 / 0 否 */
    private Integer isSb;

    /** 是否手工录入：1 手工 / 0 设备导入 */
    private Integer isManual;

    /** 全能归并标识 */
    private Long allroundGroupId;

    /** 审核状态：DRAFT/SUBMITTED/APPROVED/REJECTED */
    private String auditStatus;

    /** 审核人 ID */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 驳回原因 */
    private String rejectReason;

    /** 是否锁定：1 锁定 / 0 未锁定 */
    private Integer isLocked;

    /** 幂等令牌 */
    private String clientToken;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;

    /* ======================= 关联展示字段 ======================= */

    /** 运动员姓名（接力取队名 / 队首人） */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 参赛单位名称 */
    private String unitName;

    /** 组别名称 */
    private String groupName;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 项目类型：TRACK/FIELD/ALLROUND/RELAY */
    private String eventType;

    /** 项目子类型 */
    private String subType;

    /** 轮次码 */
    private String roundCode;

    /** 轮次名称 */
    private String roundName;

    /** 组号 */
    private Integer heatNo;

    /** 组名 */
    private String heatName;

    /** 是否接力报名：0 否 / 1 是 */
    private Integer relayFlag;
}
