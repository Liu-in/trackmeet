package com.trackmeet.modules.result.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 成绩列表查询条件。
 * <p>
 * 继承 {@link BaseQuery}，具备分页（page/size）、关键字（keyword）、排序（sortField/sortOrder）能力；
 * 额外条件：项目、轮次、分组、单位、审核状态、成绩状态。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResultQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 日程条目 ID */
    private Long scheduleId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 分组 ID */
    private Long heatId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 参赛运动员 ID */
    private Long athleteId;

    /** 审核状态：DRAFT/SUBMITTED/APPROVED/REJECTED */
    private String auditStatus;

    /** 成绩状态：VALID/DNS/DNF/DSQ/NM/PASS/SCRATCH */
    private String status;

    /** 是否锁定：0 否 / 1 是 */
    private Integer isLocked;

    /**
     * 关键字命中的范围（默认同时匹配成绩原文 / 号码 / 姓名）。
     * 说明：{@code keyword} 由基类提供，本字段用于前端显式声明匹配范围，服务层暂按「三列模糊」实现。
     */
    private String keywordScope;
}
