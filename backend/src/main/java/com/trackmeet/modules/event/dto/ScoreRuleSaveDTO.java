package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 名次分方案新增 / 修改入参（对应 {@code score_rule}）。
 * <p>
 * {@code ruleCode} 在届次内唯一（{@code uk_scorerule_meet_code}）。
 *
 * @author TrackMeet
 */
@Data
public class ScoreRuleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID，未传取当前届次上下文 */
    private Long meetId;

    /** 方案名称（如「单项前八名 9-7-6-5」） */
    @NotBlank(message = "方案名称不能为空")
    @Size(max = 100, message = "方案名称长度不能超过 100")
    private String ruleName;

    /** 方案编码，届次内唯一 */
    @NotBlank(message = "方案编码不能为空")
    @Size(max = 64, message = "方案编码长度不能超过 64")
    private String ruleCode;

    /** 并列计分：AVERAGE 均分 / FULL 满额 / ORDER 顺延 */
    @Pattern(regexp = "^(AVERAGE|FULL|ORDER)?$", message = "并列计分方式只能是 AVERAGE/FULL/ORDER")
    private String tiePolicy;

    /** 计分对象：EVENT 单项 / TEAM 团体 */
    @Pattern(regexp = "^(EVENT|TEAM)?$", message = "计分对象只能是 EVENT/TEAM")
    private String scoreTarget;

    /** 计名次数量（明细行数）；保存明细接口会按其实际行数回写覆盖 */
    private Integer rankCount;

    /** 计分基准分（用于系数换算） */
    private BigDecimal pointBase;

    /** 是否届次默认方案：0 否 / 1 是 */
    private Integer isDefault;

    /** 适用项目类型：TRACK/FIELD/ALLROUND/RELAY/ALL */
    @Pattern(regexp = "^(ALL|TRACK|FIELD|ALLROUND|RELAY)?$", message = "适用项目类型取值不正确")
    private String applyEventType;

    /** 适用性别：MALE/FEMALE/MIXED/ALL */
    @Pattern(regexp = "^(ALL|MALE|FEMALE|MIXED)?$", message = "适用性别取值不正确")
    private String applyGender;

    /** 适用组别 ID，空为全部组别 */
    private Long groupId;

    /** 生效轮次码：QUALIFY/PRE/R1/R2/SEMI/FINAL，空默认决赛 */
    @Pattern(regexp = "^(QUALIFY|PRE|R1|R2|SEMI|FINAL)?$", message = "生效轮次码取值不正确")
    private String effectiveRoundCode;

    /** 是否计入团体总分：0 否 / 1 是 */
    private Integer isTeamScore;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @Pattern(regexp = "^(ENABLED|DISABLED)?$", message = "状态只能是 ENABLED/DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
