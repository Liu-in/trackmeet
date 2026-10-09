package com.trackmeet.modules.event.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目列表查询条件。
 * <p>
 * 分页、{@code meetId}、{@code keyword} 继承自 {@link BaseQuery}；
 * keyword 支持项目名称 / 项目编码模糊匹配。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 所属组别 ID，关联 meet_group.id */
    private Long groupId;

    /** 项目类型：TRACK 径赛 / FIELD 田赛 / ALLROUND 全能 / RELAY 接力 */
    private String eventType;

    /** 适用性别：MALE 男 / FEMALE 女 / MIXED 混合 */
    private String gender;

    /** 项目子类型：SPRINT/MIDDLE/LONG/HURDLE/STEEPLE/WALK/JUMP/THROW/RELAY */
    private String subType;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;
}
