package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 田赛单次试跳/试掷入参（result_attempt）。
 * <p>
 * 试跳序号 {@link #attemptNo} 在「同一成绩」内唯一（对应 {@code uk_result_attempt}）；
 * 免跳（{@code pass=1}）与犯规（{@code foul=1}）不消耗成绩数值，{@link #attemptValue} 可为空。
 * 高度项（跳高/撑竿跳高）另填 {@link #heightValue}。
 *
 * @author TrackMeet
 */
@Data
public class ResultAttemptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 试跳/试掷记录 ID：修改时必填；新增时留空 */
    private Long id;

    /** 届次 ID，缺省取当前届次上下文 */
    private Long meetId;

    /** 所属成绩 ID（新增必填；无成绩行时先录成绩再录试跳） */
    private Long resultId;

    /** 报名记录 ID（可选，缺省取所属成绩的报名） */
    private Long registrationId;

    /** 试跳/试掷总序号，从 1 递增 */
    @NotNull(message = "试跳序号不能为空")
    private Integer attemptNo;

    /** 轮次序号（第几轮次试跳） */
    private Integer attemptRound;

    /** 单次成绩原文，如 5.68 */
    @Size(max = 16, message = "单次成绩原文长度不能超过 16")
    private String attemptRaw;

    /** 单次成绩数值（米/厘米） */
    @DecimalMin(value = "0", message = "单次成绩数值不能为负数")
    private BigDecimal attemptValue;

    /** 单次成绩单位：m 米 / cm 厘米 */
    @Size(max = 8, message = "单次成绩单位长度不能超过 8")
    private String attemptUnit;

    /** 本次横杆高度（高度项，米） */
    @DecimalMin(value = "0", message = "横杆高度不能为负数")
    private BigDecimal heightValue;

    /** 本次是否成功：1 成功 / 0 失败（缺省 1） */
    private Integer isSuccess;

    /** 是否免跳：1 免跳 / 0 否（缺省 0） */
    private Integer isPass;

    /** 是否犯规：1 犯规 / 0 未犯规（缺省 0） */
    private Integer isFoul;

    /** 犯规类型码 */
    @Size(max = 32, message = "犯规类型长度不能超过 32")
    private String foulType;

    /** 记录裁判 ID */
    private Long judgeId;

    /** 测距/计时设备编号 */
    @Size(max = 32, message = "设备编号长度不能超过 32")
    private String deviceNo;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;
}
