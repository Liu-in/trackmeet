package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单位投影行：团体总分与奖牌榜汇总所需的「单位 ID → 单位名称」映射。
 *
 * @author TrackMeet
 */
@Data
public class StandingUnitRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单位 ID */
    private Long id;

    /** 单位名称 */
    private String unitName;

    /** 单位简称（榜单优先展示） */
    private String unitShortName;
}
