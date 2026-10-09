package com.trackmeet.modules.event.vo;

import com.trackmeet.modules.event.dto.ScoreRuleItemDTO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 名次分方案视图对象：方案主表 + 明细列表。
 * <p>
 * 字段名与前端契约严格一致；{@code items} 为 {@link ScoreRuleItemDTO} 列表，按 {@code rank_no} 升序。
 * 契约未列出的适用/统计字段（scoreTarget、rankCount、pointBase 等）作为附加字段一并返回，不影响前端契约。
 *
 * @author TrackMeet
 */
@Data
public class ScoreRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 方案名称 */
    private String ruleName;

    /** 方案编码，届次内唯一 */
    private String ruleCode;

    /** 并列计分：AVERAGE/FULL/ORDER */
    private String tiePolicy;

    /** 是否届次默认方案：0 否 / 1 是 */
    private Integer isDefault;

    /** 适用项目类型：ALL/TRACK/FIELD/ALLROUND/RELAY */
    private String applyEventType;

    /** 适用性别：ALL/MALE/FEMALE/MIXED */
    private String applyGender;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 名次分明细列表（按 rank_no 升序），仅在详情接口返回 */
    private List<ScoreRuleItemDTO> items = new ArrayList<>();

    /** 计分对象：EVENT 单项 / TEAM 团体 */
    private String scoreTarget;

    /** 计名次数量（明细行数，保存明细时回写） */
    private Integer rankCount;

    /** 计分基准分 */
    private BigDecimal pointBase;

    /** 适用组别 ID，空为全部组别 */
    private Long groupId;

    /** 生效轮次码，空默认决赛 */
    private String effectiveRoundCode;

    /** 是否计入团体总分：0 否 / 1 是 */
    private Integer isTeamScore;

    /** 备注 */
    private String remark;
}
