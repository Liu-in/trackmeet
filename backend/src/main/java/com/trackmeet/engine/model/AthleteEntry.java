package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 分组/分道/田赛顺序/冲突检测的输入单位 —— 一条报名（个人项目 1 人，接力项目为队，成员单独传入）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AthleteEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报名记录 ID（结果落库的主键关联） */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 运动员姓名 */
    private String athleteName;

    /** 参赛单位 ID */
    private Long unitId;

    /** 参赛单位名称（展示用） */
    private String unitName;

    /** 号码布编号 */
    private String bibNo;

    /** 种子成绩数值（径赛：秒，越小越好；田赛：米，越大越好），可为空 */
    private BigDecimal seedValue;

    /** 种子成绩原文，如 11"23 */
    private String seedResult;

    /** 是否接力项目 */
    private boolean relay;
}
