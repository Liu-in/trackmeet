package com.trackmeet.modules.result.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 纪录查询条件。
 * <p>
 * 继承 {@link BaseQuery}（分页 + keyword）：keyword 匹配纪录保持者姓名 / 单位 / 项目名称。
 * 额外条件：项目、组别、纪录层级、性别、是否现行、是否接力。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecordQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private Long eventId;

    /** 组别 ID（组别纪录使用） */
    private Long groupId;

    /** 组别归并码 */
    private String groupCode;

    /** 纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    private String recordType;

    /** 性别：MALE/FEMALE/MIXED */
    private String gender;

    /** 是否接力项目纪录：0 否 / 1 是 */
    private Integer isRelay;

    /** 是否现行纪录：1 现行 / 0 历史 */
    private Integer isCurrent;
}
