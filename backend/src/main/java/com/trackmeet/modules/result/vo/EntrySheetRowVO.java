package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 成绩录入表行视图：一行对应一名（或一支接力队）参赛者的「组次 / 道次 / 号码 / 姓名 / 单位 /
 * 种子成绩 / 已有成绩 / 状态」。
 * <p>
 * 数据来源为 {@code heat_member}（编排结果，保证未录成绩者也出现在录入表上）
 * 左连 {@code result}（已有成绩，未录入时成绩字段为空）；
 * 排序规则：组次升序 → 道次（空道次排后）→ 出场顺序 → 组内人员 ID。
 *
 * @author TrackMeet
 */
@Data
public class EntrySheetRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /* ======================= 定位字段 ======================= */

    /** 日程条目 ID */
    private Long scheduleId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 分组 ID */
    private Long heatId;

    /** 组内人员 ID */
    private Long heatMemberId;

    /** 报名记录 ID */
    private Long registrationId;

    /** 已有成绩 ID（未录入时为空） */
    private Long resultId;

    /** 运动员 ID */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /* ======================= 录入表展示字段 ======================= */

    /** 组号（组次） */
    private Integer heatNo;

    /** 组名 */
    private String heatName;

    /** 道次号（田赛为空，按出场顺序） */
    private Integer laneNo;

    /** 组内出发 / 出场顺序号 */
    private Integer orderNo;

    /** 号码布编号 */
    private String bibNo;

    /** 姓名（接力为队名） */
    private String athleteName;

    /** 参赛单位名称 */
    private String unitName;

    /** 组别名称 */
    private String groupName;

    /** 是否种子选手：0 否 / 1 是 */
    private Integer isSeed;

    /** 是否弃权/弃赛：0 否 / 1 是 */
    private Integer isScratch;

    /** 报名种子成绩数值 */
    private BigDecimal seedResult;

    /** 报名种子成绩原文 */
    private String seedResultRaw;

    /** 报名种子成绩单位 */
    private String seedResultUnit;

    /** 已有成绩原文（未录入为空） */
    private String resultRaw;

    /** 已有成绩数值 */
    private BigDecimal resultValue;

    /** 已有成绩单位 */
    private String resultUnit;

    /** 已有成绩状态（未录入为空） */
    private String resultStatus;

    /** 已有成绩审核状态（未录入为空） */
    private String auditStatus;

    /** 名次序号 */
    private Integer rankNo;

    /** 名次显示文本 */
    private String rankDisplay;

    /** 风速（m/s） */
    private BigDecimal windSpeed;

    /** 风速是否合法：1 合法 / 0 超风速 */
    private Integer isWindLegal;

    /** 组内人员参赛状态（检录进度） */
    private String memberStatus;
}
