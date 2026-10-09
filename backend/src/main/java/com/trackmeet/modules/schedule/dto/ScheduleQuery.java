package com.trackmeet.modules.schedule.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 日程列表查询：sessionId / venueId / date / groupId / eventId / roundId / publishStatus / isLocked /
 * conflictFlag / sourceType + keyword（项目名称、轮次码）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 竞赛单元 ID */
    private Long sessionId;

    /** 比赛场地 ID */
    private Long venueId;

    /** 单元日期（按 schedule_session.session_date 过滤） */
    private LocalDate date;

    /** 组别 ID（按 event.group_id 过滤） */
    private Long groupId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 发布状态：DRAFT / PUBLISHED */
    private String publishStatus;

    /** 是否锁定：0 否 / 1 是 */
    private Integer isLocked;

    /** 是否存在冲突：0 无 / 1 有 */
    private Integer conflictFlag;

    /** 编排来源：AUTO / MANUAL / IMPORT */
    private String sourceType;
}
