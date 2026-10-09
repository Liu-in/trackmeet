package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 成绩记录 —— 名次解析、晋级、积分计算的输入单位（一次报名在一个轮次的成绩）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 运动员姓名（展示用） */
    private String athleteName;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 性别（MALE/FEMALE，用于团体总分口径；由调用方填充） */
    private String gender;

    /** 组次（径赛分组号；田赛为出场组号，可空） */
    private Integer heatNo;

    /** 道次（田赛可空） */
    private Integer laneNo;

    /** 成绩原文，如 11"23、1:02.45、5.80 */
    private String resultRaw;

    /** 可比较数值：径赛秒、田赛米、全能总分（排序依据） */
    private BigDecimal resultValue;

    /** 单位：s/m/cm/pt */
    private String resultUnit;

    /** 全能单项得分 */
    private BigDecimal resultScore;

    /** 名次（由 RankResolver 写入；无效成绩为 null） */
    private Integer rankNo;

    /** 名次显示文本（如「1（并列）」） */
    private String rankDisplay;

    /** 状态：VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH */
    private String status;

    /** 风速（m/s，顺风为正） */
    private BigDecimal windSpeed;

    /** 风速是否合法（超风速为 false） */
    private Boolean windLegal;

    /** 是否接力项目 */
    private boolean relay;

    /** 是否破纪录候选 */
    private boolean recordCandidate;

    /** 对标纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    private String recordType;

    /** 田赛各次试跳/试掷成绩（按轮次顺序，失败/免跳为 null），用于平局规则 */
    @Builder.Default
    private List<BigDecimal> attempts = new ArrayList<>();
}
