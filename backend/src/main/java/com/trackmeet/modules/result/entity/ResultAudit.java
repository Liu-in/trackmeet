package com.trackmeet.modules.result.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * result_audit 成绩留痕表：成绩提交、审核、驳回、修改、撤销的完整留痕链，
 * 保证「谁 / 何时 / 改了什么」可追溯。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code result_audit} 表逐列一致。
 * {@code before_json} / {@code after_json} 为 JSON 列，实体以原始 JSON 文本（{@link String}）承载，
 * 由服务层用 Jackson 序列化成绩快照（含各次试跳）后写入。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("result_audit")
public class ResultAudit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 留痕动作：提交 */
    public static final String ACTION_SUBMIT = "SUBMIT";
    /** 留痕动作：审核通过 */
    public static final String ACTION_APPROVE = "APPROVE";
    /** 留痕动作：驳回 */
    public static final String ACTION_REJECT = "REJECT";
    /** 留痕动作：修改 */
    public static final String ACTION_MODIFY = "MODIFY";
    /** 留痕动作：撤销审核 */
    public static final String ACTION_REVOKE = "REVOKE";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 关联成绩 ID（result.id） */
    @TableField("result_id")
    private Long resultId;

    /** 项目 ID（event.id） */
    @TableField("event_id")
    private Long eventId;

    /** 轮次 ID（event_round.id） */
    @TableField("round_id")
    private Long roundId;

    /** 报名记录 ID（registration.id） */
    @TableField("registration_id")
    private Long registrationId;

    /** 留痕动作：SUBMIT/APPROVE/REJECT/MODIFY/REVOKE */
    @TableField("action")
    private String action;

    /** 变更前快照（含各次试跳） */
    @TableField("before_json")
    private String beforeJson;

    /** 变更后快照（含各次试跳） */
    @TableField("after_json")
    private String afterJson;

    /** 修改或驳回原因 */
    @TableField("reason")
    private String reason;

    /** 操作人 ID（sys_user.id） */
    @TableField("operator_id")
    private Long operatorId;

    /** 操作人姓名快照 */
    @TableField("operator_name")
    private String operatorName;

    /** 操作时间 */
    @TableField("operate_time")
    private LocalDateTime operateTime;

    /** 操作来源 IP */
    @TableField("ip")
    private String ip;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
