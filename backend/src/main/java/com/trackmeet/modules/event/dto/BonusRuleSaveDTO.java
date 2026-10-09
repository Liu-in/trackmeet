package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 加分规则新增 / 修改入参（对应 {@code bonus_rule}）。
 * <p>
 * {@code ruleCode} 在届次内唯一（{@code uk_bonus_meet_code}）；
 * {@code bonusType} 取值 RECORD 破纪录 / RELAY 接力双倍 / BREAK_TIE 破平局。
 *
 * @author TrackMeet
 */
@Data
public class BonusRuleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID，未传取当前届次上下文 */
    private Long meetId;

    /** 加分规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称长度不能超过 100")
    private String ruleName;

    /** 规则编码，届次内唯一 */
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64")
    private String ruleCode;

    /** 加分类型：RECORD 破纪录 / RELAY 接力双倍 / BREAK_TIE 破平局 */
    @NotBlank(message = "加分类型不能为空")
    @Pattern(regexp = "^(RECORD|RELAY|BREAK_TIE)$", message = "加分类型只能是 RECORD/RELAY/BREAK_TIE")
    private String bonusType;

    /** 纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    @Pattern(regexp = "^(WORLD|ASIA|NATION|PROVINCE|CITY|MEET|GROUP)?$", message = "纪录层级取值不正确")
    private String recordType;

    /** 破纪录加分值（默认赛会纪录 +9） */
    private BigDecimal bonusPoint;

    /** 多层级纪录加分是否叠加：0 否 / 1 是 */
    private Integer isStackable;

    /** 倍数（接力双倍为 2.00） */
    private BigDecimal factor;

    /** 适用项目 ID，空为全部项目 */
    private Long eventId;

    /** 适用组别 ID，空为全部组别 */
    private Long groupId;

    /** 适用项目类型：ALL/TRACK/FIELD/ALLROUND/RELAY */
    @Pattern(regexp = "^(ALL|TRACK|FIELD|ALLROUND|RELAY)?$", message = "适用项目类型取值不正确")
    private String applyEventType;

    /** 适用性别：ALL/MALE/FEMALE */
    @Pattern(regexp = "^(ALL|MALE|FEMALE)?$", message = "适用性别取值不正确")
    private String applyGender;

    /** 生效轮次码，逗号分隔（QUALIFY/PRE/R1/R2/SEMI/FINAL），空为全部 */
    @Size(max = 100, message = "生效轮次码长度不能超过 100")
    private String effectiveRoundCodes;

    /** 加分是否计入团体总分：0 否 / 1 是 */
    private Integer isInTeamScore;

    /** 多条命中时的优先顺序 */
    private Integer priority;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @Pattern(regexp = "^(ENABLED|DISABLED)?$", message = "状态只能是 ENABLED/DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
