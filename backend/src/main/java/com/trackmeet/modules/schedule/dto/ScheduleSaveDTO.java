package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 日程条目新增/修改入参。
 *
 * @author TrackMeet
 */
@Data
public class ScheduleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（修改时必填，新增时忽略） */
    private Long id;

    /** 届次 ID（缺省取当前届次上下文） */
    private Long meetId;

    @NotNull(message = "竞赛单元不能为空")
    private Long sessionId;

    @NotNull(message = "项目不能为空")
    private Long eventId;

    @NotNull(message = "轮次不能为空")
    private Long roundId;

    /** 比赛场地 venue.id */
    private Long venueId;

    /** 计划开始时间 */
    private LocalDateTime planStartTime;

    /** 计划结束时间（缺省按计划开始 + 预计时长推导） */
    private LocalDateTime planEndTime;

    /** 预计时长（分钟） */
    private Integer durationMin;

    /** 组数 */
    private Integer heatCount;

    /** 参赛人数/队数 */
    private Integer memberCount;

    /** 单元内排序号（缺省追加到单元末尾） */
    private Integer orderNo;

    /** 编排来源：AUTO / MANUAL / IMPORT（缺省 MANUAL） */
    @Size(max = 32, message = "编排来源长度不能超过 32")
    private String sourceType;

    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
