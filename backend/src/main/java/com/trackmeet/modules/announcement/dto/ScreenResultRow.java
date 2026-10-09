package com.trackmeet.modules.announcement.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大屏最新成绩投影：{@code result} 取 {@code audit_status = 'APPROVED'} 且 {@code status = 'VALID'}
 * 的成绩，联 {@code event} / {@code event_round} / {@code athlete} / {@code unit} / {@code meet_group}
 * 取名，按 {@code update_time} 降序取前 N。
 *
 * @author TrackMeet
 */
@Data
public class ScreenResultRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID */
    private Long resultId;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 运动员 ID */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 成绩原文（显示用），如 11"23 */
    private String resultRaw;

    /** 成绩数值（秒/米/分） */
    private BigDecimal resultValue;

    /** 成绩单位：s / m / cm / pt */
    private String resultUnit;

    /** 名次序号 */
    private Integer rankNo;

    /** 名次显示文本（含并列） */
    private String rankDisplay;

    /** 成绩状态：VALID / DNS / DNF / DSQ / NM / PASS / SCRATCH */
    private String status;

    /** 风速（m/s） */
    private BigDecimal windSpeed;

    /** 纪录是否已认定：1 已认定 / 0 未认定 */
    private Integer isRecordConfirmed;

    /** 成绩最近更新时间（大屏「最新成绩」排序依据） */
    private LocalDateTime updateTime;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 项目类型 */
    private String eventType;

    /** 性别：MALE / FEMALE / MIXED */
    private String gender;

    /** 轮次名称 */
    private String roundName;

    /** 轮次序号 */
    private Integer roundNo;

    /** 运动员姓名 */
    private String athleteName;

    /** 号码布 */
    private String bibNo;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 组别名称 */
    private String groupName;
}
