package com.trackmeet.modules.print.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 日程条目投影：一次查询把「日程 + 单元 + 项目 + 轮次 + 场地 + 组别」装入内存，
 * 供总日程表、分组表、分道表、检录表共用（避免逐条查询造成 N+1）。
 * <p>
 * 全部字段来自 {@code schedule}、{@code schedule_session}、{@code event}、
 * {@code event_round}、{@code meet_group}、{@code venue} 的 {@code @Select} 投影。
 *
 * @author TrackMeet
 */
@Data
public class ScheduleProjection implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---------------- schedule ----------------

    /** 日程条目 ID（schedule.id） */
    private Long scheduleId;

    /** 竞赛单元 ID（schedule.session_id） */
    private Long sessionId;

    /** 项目 ID（schedule.event_id） */
    private Long eventId;

    /** 轮次 ID（schedule.round_id） */
    private Long roundId;

    /** 比赛场地 ID（schedule.venue_id，可为空表示沿用单元主场地） */
    private Long venueId;

    /** 计划开始时间（schedule.plan_start_time） */
    private LocalDateTime planStartTime;

    /** 计划结束时间（schedule.plan_end_time） */
    private LocalDateTime planEndTime;

    /** 组数（schedule.heat_count） */
    private Integer heatCount;

    /** 日程预计时长分钟（schedule.duration_min） */
    private Integer durationMin;

    /** 单元内排序号（schedule.order_no） */
    private Integer orderNo;

    /** 发布状态（schedule.publish_status）：DRAFT/PUBLISHED */
    private String publishStatus;

    /** 是否存在冲突（schedule.conflict_flag）：0 无 / 1 有 */
    private Integer conflictFlag;

    /** 日程备注（schedule.remark） */
    private String scheduleRemark;

    // ---------------- event ----------------

    /** 项目名称（event.event_name） */
    private String eventName;

    /** 项目编码（event.event_code） */
    private String eventCode;

    /** 项目类型（event.event_type）：TRACK/FIELD/ALLROUND/RELAY */
    private String eventType;

    /** 项目子类型（event.sub_type） */
    private String subType;

    /** 适用性别（event.gender） */
    private String gender;

    /** 是否接力（event.is_relay）：0 否 / 1 是 */
    private Integer relayFlag;

    /** 项目所属组别 ID（event.group_id） */
    private Long groupId;

    /** 项目预计时长分钟（event.duration_min，schedule.duration_min 为空时兜底） */
    private Integer eventDurationMin;

    // ---------------- meet_group ----------------

    /** 组别名称（meet_group.group_name） */
    private String groupName;

    // ---------------- event_round ----------------

    /** 轮次码（event_round.round_code） */
    private String roundCode;

    /** 轮次名称（event_round.round_name，可为空时用轮次码兜底） */
    private String roundName;

    /** 轮次序号（event_round.round_no） */
    private Integer roundNo;

    /** 是否决赛轮（event_round.is_final）：0 否 / 1 是 */
    private Integer finalFlag;

    // ---------------- schedule_session ----------------

    /** 单元名称（schedule_session.session_name） */
    private String sessionName;

    /** 单元日期（schedule_session.session_date） */
    private LocalDate sessionDate;

    /** 赛期第几天（schedule_session.day_no） */
    private Integer dayNo;

    /** 时段（schedule_session.period）：MORNING/AFTERNOON/EVENING */
    private String period;

    /** 单元开始时刻（schedule_session.start_time） */
    private LocalTime sessionStartTime;

    /** 单元结束时刻（schedule_session.end_time） */
    private LocalTime sessionEndTime;

    /** 单元排序号（schedule_session.sort_no） */
    private Integer sessionSortNo;

    /** 单元发布状态（schedule_session.publish_status） */
    private String sessionPublishStatus;

    /**
     * 单元同时可用场地 ID 列表原始 JSON（{@code schedule_session.venue_ids_json}）。
     * <p>
     * 仅用于服务层按 {@code venueId} 过滤：日程自身未指定场地时，
     * 只要该场地在单元可用场地列表内即视为命中。解析失败按空列表处理。
     */
    private String sessionVenueIdsJson;

    // ---------------- venue ----------------

    /** 日程场地名称（venue.venue_name，取自 schedule.venue_id） */
    private String venueName;

    /** 日程场地道数（venue.lane_count，分道表道数兜底） */
    private Integer venueLaneCount;

    /** 单元主场地名称（venue.venue_name，取自 schedule_session.venue_id） */
    private String sessionVenueName;

    /** 单元主场地道数（venue.lane_count，分道表道数兜底） */
    private Integer sessionVenueLaneCount;
}
