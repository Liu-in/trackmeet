package com.trackmeet.modules.meet.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组别列表查询：支持 meetId（继承）、gender、keyword（分页参数继承自 BaseQuery）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MeetGroupQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 性别限制：MALE 男 / FEMALE 女 / MIXED 混合 */
    private String gender;
}
