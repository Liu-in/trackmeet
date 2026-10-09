package com.trackmeet.modules.meet.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参赛单位列表查询：支持 meetId、keyword（均继承自 BaseQuery，keyword 模糊匹配单位名称/编码/简称）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UnitQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;
}
