package com.trackmeet.modules.athlete.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 报名统计视图：按单位/项目/组别维度聚合的报名分布。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计维度：UNIT/EVENT/GROUP */
    private String dimension;

    /** 维度对象 ID（单位 ID / 项目 ID / 组别 ID） */
    private Long refId;

    /** 维度名称（单位名 / 项目名 / 组别名） */
    private String name;

    /** 报名总数（不含已取消） */
    private Integer total;

    /** 待审数 */
    private Integer pending;

    /** 已通过数 */
    private Integer approved;

    /** 已退回数 */
    private Integer rejected;
}
