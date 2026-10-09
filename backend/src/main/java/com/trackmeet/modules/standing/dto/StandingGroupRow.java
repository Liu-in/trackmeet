package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 组别投影行：榜单按组别分组展示时的名称来源。
 *
 * @author TrackMeet
 */
@Data
public class StandingGroupRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组别 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 组别名称 */
    private String groupName;

    /** 组别编码 */
    private String groupCode;

    /** 性别限制：MALE / FEMALE / MIXED */
    private String gender;
}
