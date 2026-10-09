package com.trackmeet.modules.system.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型分页查询条件：关键字匹配 dict_type / dict_name。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictTypeQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 字典分组，如 TRACK_FIELD */
    private String dictGroup;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;
}
