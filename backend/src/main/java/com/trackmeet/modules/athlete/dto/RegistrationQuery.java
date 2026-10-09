package com.trackmeet.modules.athlete.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报名列表查询条件。
 * 继承 {@link BaseQuery} 的分页（page/size）、keyword、meetId 能力；
 * keyword 支持运动员姓名 / 单位名 / 项目名 / 接力队名模糊匹配。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RegistrationQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private Long eventId;

    /** 报名单位 ID */
    private Long unitId;

    /** 报名组别 ID */
    private Long groupId;

    /** 运动员 ID */
    private Long athleteId;

    /** 状态：PENDING/APPROVED/REJECTED/CANCELLED */
    private String status;

    /** 是否接力项目：0 否 / 1 是 */
    private Integer isRelay;
}
