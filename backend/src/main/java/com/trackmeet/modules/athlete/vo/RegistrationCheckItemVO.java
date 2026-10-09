package com.trackmeet.modules.athlete.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 报名限制校验项：校验引擎输出的最小问题单元（同一结构同时用于未落库校验与已落库快照）。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationCheckItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 级别：错误（阻断报名） */
    public static final String LEVEL_ERROR = "ERROR";
    /** 级别：警告（提示，不阻断） */
    public static final String LEVEL_WARN = "WARN";

    /** 请求在 items 数组中的下标（0 起） */
    private Integer rowIndex;

    /** 运动员 ID */
    private Long athleteId;

    /** 运动员姓名 */
    private String athleteName;

    /** 项目 ID */
    private Long eventId;

    /** 项目名称 */
    private String eventName;

    /** 级别：ERROR/WARN */
    private String level;

    /** 问题码：REG_CLOSED/REG_DUPLICATE/REG_LIMIT_EXCEEDED/... */
    private String code;

    /** 中文问题描述 */
    private String message;
}
