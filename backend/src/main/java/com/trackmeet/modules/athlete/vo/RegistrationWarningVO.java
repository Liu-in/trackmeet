package com.trackmeet.modules.athlete.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 报名不足预警视图：已通过报名数低于预警线的项目。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationWarningVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private Long eventId;

    /** 项目名称 */
    private String eventName;

    /** 该项目有效报名总数（不含已取消） */
    private Integer total;

    /** 已通过报名数 */
    private Integer approved;

    /** 预警说明 */
    private String message;
}
