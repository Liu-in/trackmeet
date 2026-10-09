package com.trackmeet.modules.meet.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运动会（届次）列表查询：支持 status、year、keyword（分页参数继承自 BaseQuery）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MeetQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 届次状态：DRAFT/REG_OPEN/REG_CLOSED/SCHEDULING/SCHEDULED/RUNNING/FINISHED/ARCHIVED */
    private String status;

    /** 举办年份（按比赛开始日期所在年份过滤） */
    private Integer year;
}
