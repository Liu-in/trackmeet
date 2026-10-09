package com.trackmeet.modules.announcement.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 公告分页查询条件。
 * <p>
 * {@code meetId} 继承自 {@link BaseQuery}：传入时必须与 {@code MeetContext.get()} 一致，
 * 未传时取当前届次上下文（见 {@code AnnouncementServiceImpl#resolveMeetId}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnnouncementQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 公告类型：NOTICE 通知 / RESULT 成绩 / SCHEDULE 日程 / URGENT 紧急 */
    private String noticeType;

    /** 公告状态：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线 */
    private String status;

    /** 可见范围：PUBLIC 公开 / LEADER 领队可见 / REFEREE 裁判可见 */
    private String scope;

    /** 是否置顶：1 置顶 / 0 不置顶 */
    private Integer isTop;

    /** 发布时间起（含），格式 yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    /** 发布时间止（含），格式 yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
