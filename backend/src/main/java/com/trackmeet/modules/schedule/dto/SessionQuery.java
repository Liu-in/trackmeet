package com.trackmeet.modules.schedule.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 竞赛单元列表查询：date / period / venueId / publishStatus / isLocked / dayNo + keyword（单元名称）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 单元日期 */
    private LocalDate date;

    /** 时段：MORNING / AFTERNOON / EVENING */
    private String period;

    /** 主场地 ID */
    private Long venueId;

    /** 赛期第几天 */
    private Integer dayNo;

    /** 发布状态：DRAFT / PUBLISHED */
    private String publishStatus;

    /** 是否锁定：0 否 / 1 是 */
    private Integer isLocked;
}
