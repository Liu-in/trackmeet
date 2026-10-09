package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 名次行视图（预览与生成结果共用）。
 * <p>
 * 名次由成绩引擎 {@code RankResolver.resolveRank} 计算：无效状态不排名，成绩相同按平局规则
 * （次优成绩 → 最远成功轮次 → 抽签）比较，仍相同则并列且名次顺延。
 * 本视图同时给出田赛平局比较所需的 {@code bestAttempt} / {@code secondBest} / {@code bestAttemptRound}。
 *
 * @author TrackMeet
 */
@Data
public class RankRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID */
    private Long id;

    /** 名次序号（无效状态为空） */
    private Integer rankNo;

    /** 名次显示文本，如 1、2（并列） */
    private String rankDisplay;

    /** 是否并列名次：0 否 / 1 是 */
    private Integer isTie;

    /** 成绩原文 */
    private String resultRaw;

    /** 成绩数值（秒/米/分） */
    private BigDecimal resultValue;

    /** 成绩单位 */
    private String resultUnit;

    /** 全能单项得分 */
    private BigDecimal resultScore;

    /** 田赛最终过杆高度（米） */
    private BigDecimal finalHeight;

    /** 成绩状态 */
    private String status;

    /** 审核状态 */
    private String auditStatus;

    /** 风速（m/s） */
    private BigDecimal windSpeed;

    /** 风速是否合法：1 合法 / 0 超风速 */
    private Integer isWindLegal;

    /** 是否破纪录已认定：1 是 / 0 否 */
    private Integer isRecordConfirmed;

    /** 名次是否已回写库（预览为 0，生成后为 1）：0 否 / 1 是 */
    private Integer generated;

    /* ======================= 关联展示字段 ======================= */

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 号码布编号 */
    private String bibNo;

    /** 姓名（接力为队名） */
    private String athleteName;

    /** 参赛单位 ID */
    private Long unitId;

    /** 参赛单位名称 */
    private String unitName;

    /** 组别 ID */
    private Long groupId;

    /** 组别名称 */
    private String groupName;

    /** 项目 ID */
    private Long eventId;

    /** 项目名称 */
    private String eventName;

    /** 轮次 ID */
    private Long roundId;

    /** 组号 */
    private Integer heatNo;

    /** 是否接力报名：0 否 / 1 是 */
    private Integer relayFlag;

    /* ======================= 田赛平局比较 ======================= */

    /** 最好一次成绩（米） */
    private BigDecimal bestAttempt;

    /** 次优一次成绩（米），平局规则「次优成绩」比较用 */
    private BigDecimal secondBest;

    /** 最远 / 最高成功轮次（平局规则「最远成功轮次」比较用） */
    private Integer bestAttemptRound;

    /** 有效成功试跳次数 */
    private Integer successAttemptCount;
}
