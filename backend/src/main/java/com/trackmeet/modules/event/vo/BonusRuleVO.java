package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 加分规则视图对象（对应 {@code bonus_rule}）。
 * <p>
 * 字段名与前端契约严格一致（契约所列字段 + 主键 id）。
 *
 * @author TrackMeet
 */
@Data
public class BonusRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 加分规则名称 */
    private String ruleName;

    /** 规则编码，届次内唯一 */
    private String ruleCode;

    /** 加分类型：RECORD 破纪录 / RELAY 接力双倍 / BREAK_TIE 破平局 */
    private String bonusType;

    /** 纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    private String recordType;

    /** 破纪录加分值 */
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
    private String applyEventType;

    /** 适用性别：ALL/MALE/FEMALE */
    private String applyGender;

    /** 生效轮次码，逗号分隔，空为全部 */
    private String effectiveRoundCodes;

    /** 加分是否计入团体总分：0 否 / 1 是 */
    private Integer isInTeamScore;

    /** 多条命中时的优先顺序 */
    private Integer priority;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}
