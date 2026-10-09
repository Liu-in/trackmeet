package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 参赛人员投影行：{@code PARTICIPATION} 参赛证书的只读数据源。
 * <p>
 * 由 {@code StandingMapper#selectParticipants} 以 {@code @Select} 联
 * {@code registration} + {@code athlete} + {@code unit} + {@code event} + {@code meet_group} 得到。
 *
 * @author TrackMeet
 */
@Data
public class StandingParticipantRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报名记录 ID */
    private Long registrationId;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 运动员 ID（接力报名为空） */
    private Long athleteId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 运动员姓名（接力为空） */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 接力队名 */
    private String teamName;

    /** 是否接力报名：0 否 / 1 是 */
    private Integer relay;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 组别名称 */
    private String groupName;

    /** 报名状态 */
    private String status;
}
