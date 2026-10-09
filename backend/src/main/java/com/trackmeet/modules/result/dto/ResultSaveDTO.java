package com.trackmeet.modules.result.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 成绩录入 / 修改入参（单条）。
 * <p>
 * 成绩双列：{@link #resultRaw} 为显示原文（径赛如 {@code 11"23}、{@code 1:02.45}），
 * {@link #resultValue} 为可比较数值（径赛秒、田赛米、全能分）；
 * 未传 {@code resultValue} 时服务层按 {@code resultRaw} 解析补全。
 * <p>
 * 田赛可在同一次提交中传入 {@link #attempts}（各次试跳/试掷），服务层据此汇总最好成绩。
 *
 * @author TrackMeet
 */
@Data
public class ResultSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID：修改时必填；新增时留空 */
    private Long id;

    /** 届次 ID，缺省取当前届次上下文 */
    private Long meetId;

    /** 日程条目 ID（新增必填） */
    private Long scheduleId;

    /** 分组 ID */
    private Long heatId;

    /** 组内人员 ID */
    private Long heatMemberId;

    /** 项目 ID（新增必填） */
    private Long eventId;

    /** 轮次 ID（新增必填） */
    private Long roundId;

    /** 报名记录 ID（新增必填，成绩挂在报名行上；接力按队） */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 道次号（田赛可为空） */
    private Integer laneNo;

    /** 成绩原文（显示用），如 11"23、1:02.45、5.68 */
    @Size(max = 16, message = "成绩原文长度不能超过 16")
    private String resultRaw;

    /** 成绩数值（秒/米/分）；无效状态时服务层置空 */
    @DecimalMin(value = "0", message = "成绩数值不能为负数")
    private BigDecimal resultValue;

    /** 成绩单位：s / m / cm / pt */
    @Size(max = 8, message = "成绩单位长度不能超过 8")
    private String resultUnit;

    /** 全能单项得分，非全能项留空 */
    @DecimalMin(value = "0", message = "全能单项得分不能为负数")
    private BigDecimal resultScore;

    /** 田赛高度项最终过杆高度（米） */
    @DecimalMin(value = "0", message = "最终过杆高度不能为负数")
    private BigDecimal finalHeight;

    /** 成绩状态：VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH，缺省 VALID */
    @Pattern(regexp = "^(VALID|DNS|DNF|DSQ|NM|PASS|SCRATCH)?$",
            message = "成绩状态只能是 VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH")
    private String status;

    /** 犯规类型码，DSQ 必填 */
    @Size(max = 32, message = "犯规类型长度不能超过 32")
    private String foulType;

    /** 判罚依据说明，DSQ 必填 */
    @Size(max = 200, message = "判罚依据说明长度不能超过 200")
    private String foulReason;

    /** 风速（m/s），顺风为正、逆风为负 */
    @DecimalMin(value = "-99.99", message = "风速超出取值范围")
    @DecimalMax(value = "99.99", message = "风速超出取值范围")
    private BigDecimal windSpeed;

    /** 是否破纪录候选：1 是 / 0 否 */
    private Integer isRecordCandidate;

    /** 是否个人最好成绩：1 是 / 0 否 */
    private Integer isPb;

    /** 是否个人赛季最好成绩：1 是 / 0 否 */
    private Integer isSb;

    /** 全能归并标识，同一全能用同值 */
    private Long allroundGroupId;

    /** 幂等令牌，重复提交返回首次结果 */
    @Size(max = 64, message = "幂等令牌长度不能超过 64")
    private String clientToken;

    /** 备注 */
    @Size(max = 200, message = "备注长度不能超过 200")
    private String remark;

    /** 田赛各次试跳/试掷（可选，与成绩同批提交） */
    @Valid
    private List<ResultAttemptDTO> attempts;
}
