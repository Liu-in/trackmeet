package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 成绩留痕视图：{@code result_audit} 表全字段 + 项目 / 运动员展示字段。
 * <p>
 * {@code before_json} / {@code after_json} 为 JSON 原始文本，前端可直接解析渲染「改了哪一列、原值、新值」。
 *
 * @author TrackMeet
 */
@Data
public class ResultAuditVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 留痕 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 关联成绩 ID */
    private Long resultId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 报名记录 ID */
    private Long registrationId;

    /** 留痕动作：SUBMIT/APPROVE/REJECT/MODIFY/REVOKE */
    private String action;

    /** 变更前快照 JSON */
    private String beforeJson;

    /** 变更后快照 JSON */
    private String afterJson;

    /** 修改或驳回原因 */
    private String reason;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime operateTime;

    /** 操作来源 IP */
    private String ip;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /* ======================= 关联展示字段 ======================= */

    /** 项目名称 */
    private String eventName;

    /** 轮次名称 */
    private String roundName;

    /** 运动员姓名（接力为队名） */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 参赛单位名称 */
    private String unitName;

    /** 成绩原文（当前值，便于留痕列表直接展示） */
    private String resultRaw;
}
