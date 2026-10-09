package com.trackmeet.modules.result.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * result_attempt 田赛试跳试掷表：田赛项目每一次试跳/试掷的明细，
 * 用于取名次前的平局比较（次优成绩 / 最远成功轮次）与成绩复核。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code result_attempt} 表逐列一致。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("result_attempt")
public class ResultAttempt extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 布尔真 */
    public static final Integer YES = 1;
    /** 布尔假 */
    public static final Integer NO = 0;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 所属成绩 ID（result.id） */
    @TableField("result_id")
    private Long resultId;

    /** 报名记录 ID（registration.id） */
    @TableField("registration_id")
    private Long registrationId;

    /** 试跳/试掷总序号，从 1 递增 */
    @TableField("attempt_no")
    private Integer attemptNo;

    /** 轮次序号（第几轮次试跳） */
    @TableField("attempt_round")
    private Integer attemptRound;

    /** 单次成绩原文，如 5.68 */
    @TableField("attempt_raw")
    private String attemptRaw;

    /** 单次成绩数值（米/厘米） */
    @TableField("attempt_value")
    private BigDecimal attemptValue;

    /** 单次成绩单位：m 米 / cm 厘米 */
    @TableField("attempt_unit")
    private String attemptUnit;

    /** 本次横杆高度（高度项，米） */
    @TableField("height_value")
    private BigDecimal heightValue;

    /** 本次是否成功：1 成功 / 0 失败 */
    @TableField("is_success")
    private Integer isSuccess;

    /** 是否免跳：1 免跳（PASS，不消耗试跳次数）/ 0 否 */
    @TableField("is_pass")
    private Integer isPass;

    /** 是否犯规：1 犯规 / 0 未犯规 */
    @TableField("is_foul")
    private Integer isFoul;

    /** 犯规类型码，取自犯规类型字典 */
    @TableField("foul_type")
    private String foulType;

    /** 是否为该运动员最好一次：1 是 / 0 否 */
    @TableField("is_best")
    private Integer isBest;

    /** 记录裁判 ID（referee.id） */
    @TableField("judge_id")
    private Long judgeId;

    /** 测距/计时设备编号 */
    @TableField("device_no")
    private String deviceNo;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /**
     * 本次是否为有效成功成绩（成功且非犯规、非免跳且有数值）。
     *
     * @return 有效成功返回 true
     */
    public boolean validSuccess() {
        return YES.equals(this.isSuccess) && !YES.equals(this.isPass) && !YES.equals(this.isFoul)
                && this.attemptValue != null;
    }
}
