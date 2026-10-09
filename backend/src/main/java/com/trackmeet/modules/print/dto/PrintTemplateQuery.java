package com.trackmeet.modules.print.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 打印模板分页查询条件：{@code reportType}（报表类型）、{@code status}（启用状态）与
 * 基类 {@code keyword}（模糊匹配模板编码 / 模板名称）。
 * <p>
 * 届次取自基类 {@code meetId}（为空时用当前届次上下文 {@code MeetContext}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PrintTemplateQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 报表类型：PROGRAM / START_LIST / RESULT / POINTS / MEDAL / CERT（可空） */
    private String reportType;

    /** 模板状态：ENABLED / DISABLED（可空） */
    private String status;

    /** 是否默认模板：1 是 / 0 否（可空） */
    private Integer isDefault;
}
