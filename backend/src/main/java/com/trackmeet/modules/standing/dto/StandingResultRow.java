package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩投影行：积分计算、奖牌榜、成绩册与证书生成的统一只读数据源。
 * <p>
 * 由 {@code StandingMapper#selectResults} / {@code selectFinalResults} 以
 * {@code @Select} 联 {@code result} + {@code event} + {@code event_round} + {@code unit} +
 * {@code athlete} + {@code registration} 得到，自带 {@code meet_id} / {@code deleted} 条件。
 * <p>
 * 说明：{@code result} 无 {@code is_relay} 列，接力判定取 {@code event.is_relay}（别名 {@code relay}）。
 *
 * @author TrackMeet
 */
@Data
public class StandingResultRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID（result.id） */
    private Long resultId;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID（接力为队首人） */
    private Long athleteId;

    /** 计分单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 成绩原文 */
    private String resultRaw;

    /** 成绩数值 */
    private BigDecimal resultValue;

    /** 成绩单位 */
    private String resultUnit;

    /** 名次序号 */
    private Integer rankNo;

    /** 名次显示文本 */
    private String rankDisplay;

    /** 成绩状态：VALID / DNS / DNF / DSQ / NM / PASS / SCRATCH */
    private String status;

    /** 风速（m/s） */
    private BigDecimal windSpeed;

    /** 风速是否合法：1 合法 / 0 超风速 */
    private Integer windLegal;

    /** 是否破纪录候选：1 是 / 0 否 */
    private Integer recordCandidate;

    /** 纪录是否已认定：1 已认定 / 0 未认定 */
    private Integer recordConfirmed;

    /** 认定纪录层级：WORLD / ASIA / NATION / PROVINCE / CITY / MEET / GROUP */
    private String recordType;

    /** 备注 */
    private String remark;

    /** 审核状态 */
    private String auditStatus;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 项目类型：TRACK / FIELD / ALLROUND / RELAY */
    private String eventType;

    /** 项目性别：MALE / FEMALE / MIXED */
    private String gender;

    /** 是否接力项目（取 {@code event.is_relay}）：0 否 / 1 是 */
    private Integer eventRelay;

    /** 是否接力报名（取 {@code registration.is_relay}）：0 否 / 1 是，为空时回落 {@code eventRelay} */
    private Integer registrationRelay;

    /** 轮次码：QUALIFY / PRE / R1 / R2 / SEMI / FINAL */
    private String roundCode;

    /** 轮次名称 */
    private String roundName;

    /** 轮次顺序号 */
    private Integer roundNo;

    /** 是否决赛轮：0 否 / 1 是 */
    private Integer finalRound;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 运动员姓名 */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 接力队名（个人项为空） */
    private String teamName;

    /** 组别名称 */
    private String groupName;
}
