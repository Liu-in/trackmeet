package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 名次分明细入参（对应 {@code score_rule_item}）。
 * <p>
 * 名次区间语义：单名次行填 {@code rankNo}；区间行用 {@code rankFrom} / {@code rankTo} 表示「从第 A 名到第 B 名」。
 * {@code rankNo} 在方案内唯一（{@code uk_scoreitem_rule_rank}）。
 *
 * @author TrackMeet
 */
@Data
public class ScoreRuleItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 名次序号（区间行填区间起点），方案内唯一 */
    @NotNull(message = "名次序号不能为空")
    @Min(value = 1, message = "名次序号必须大于 0")
    private Integer rankNo;

    /** 名次区间起（含） */
    private Integer rankFrom;

    /** 名次区间止（含） */
    private Integer rankTo;

    /** 名次显示名（如「第一名」） */
    @Size(max = 50, message = "名次显示名长度不能超过 50")
    private String rankLabel;

    /** 该名次 / 区间对应分值 */
    private BigDecimal pointValue;

    /** 该名次附加分 */
    private BigDecimal bonusPoint;

    /** 该名次并列处理：AVERAGE/FULL/ORDER，空继承主表 */
    @Pattern(regexp = "^(AVERAGE|FULL|ORDER)?$", message = "并列处理只能是 AVERAGE/FULL/ORDER")
    private String tiePolicy;

    /** 该名次分是否计入团体总分：0 否 / 1 是 */
    private Integer isTeamScore;

    /** 显示排序号，未传按 rankNo 顺序生成 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
