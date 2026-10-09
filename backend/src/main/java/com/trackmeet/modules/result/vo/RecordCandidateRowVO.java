package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 破纪录候选扫描投影行（模块内部投影，不对外暴露为接口返回体）。
 * <p>
 * 供 {@code RecordService} 扫描「有效成绩 + 项目/组别归并码/性别/接力属性 + 现有纪录对标信息」，
 * 逐条与 {@code record} 表的现行纪录比较，得出破纪录候选（{@code is_record_candidate}）。
 * 列名与 {@code result} / {@code registration} / {@code athlete} / {@code unit} / {@code meet_group} /
 * {@code event} 表逐字对应。
 *
 * @author TrackMeet
 */
@Data
public class RecordCandidateRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩 ID */
    private Long resultId;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 日程条目 ID */
    private Long scheduleId;

    /** 分组 ID */
    private Long heatId;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 成绩原文 */
    private String resultRaw;

    /** 成绩数值（秒/米/分） */
    private BigDecimal resultValue;

    /** 成绩单位 */
    private String resultUnit;

    /** 名次序号 */
    private Integer rankNo;

    /** 名次显示文本 */
    private String rankDisplay;

    /** 成绩状态 */
    private String status;

    /** 风速（m/s） */
    private BigDecimal windSpeed;

    /** 风速是否合法：1 合法 / 0 超风速 */
    private Integer isWindLegal;

    /** 是否破纪录候选：1 是 / 0 否 */
    private Integer isRecordCandidate;

    /** 纪录是否已认定：1 已认定 / 0 未认定 */
    private Integer isRecordConfirmed;

    /** 对标纪录 ID */
    private Long recordId;

    /** 认定纪录层级 */
    private String recordType;

    /** 运动员姓名（接力为队名） */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 运动员性别 */
    private String gender;

    /** 参赛单位名称 */
    private String unitName;

    /** 组别名称 */
    private String groupName;

    /** 组别纪录归并码（对应 {@code meet_group.record_group_code}） */
    private String groupCode;

    /** 项目名称 */
    private String eventName;

    /** 项目类型 */
    private String eventType;

    /** 项目子类型 */
    private String subType;

    /** 项目适用性别 */
    private String eventGender;

    /** 是否接力报名：0 否 / 1 是 */
    private Integer relayFlag;
}
