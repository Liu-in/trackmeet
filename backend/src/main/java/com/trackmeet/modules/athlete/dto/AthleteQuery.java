package com.trackmeet.modules.athlete.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运动员列表查询条件。
 * 继承 {@link BaseQuery} 的分页与关键字能力，keyword 支持姓名/证件号/号码模糊匹配。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AthleteQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 性别码 MALE/FEMALE */
    private String gender;

    /** 资格状态 PENDING/QUALIFIED/DISQUALIFIED */
    private String qualificationStatus;

    /** 号码布编号（精确匹配） */
    private String bibNo;
}
