package com.trackmeet.modules.athlete.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目投影视图：报名校验所需的 event 表字段子集（跨模块取数不引用其它模块实体）。
 *
 * @author TrackMeet
 */
@Data
public class EventBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private Long id;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 所属组别 ID */
    private Long groupId;

    /** 适用性别 MALE/FEMALE/MIXED */
    private String gender;

    /** 是否接力项目：0 否 / 1 是 */
    private Integer isRelay;

    /** 接力最少人数（默认 4） */
    private Integer relayMin;

    /** 接力最多人数（默认 4） */
    private Integer relayMax;

    /** 每单位每项限报人数 */
    private Integer maxPerUnit;

    /** 项目预计时长（分钟） */
    private Integer durationMin;
}
