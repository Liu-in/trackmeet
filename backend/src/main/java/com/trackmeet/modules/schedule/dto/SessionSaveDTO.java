package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 竞赛单元新增/修改入参。
 *
 * @author TrackMeet
 */
@Data
public class SessionSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（修改时必填，新增时忽略） */
    private Long id;

    /** 届次 ID（缺省取当前届次上下文） */
    private Long meetId;

    @NotBlank(message = "单元名称不能为空")
    @Size(max = 100, message = "单元名称长度不能超过 100")
    private String sessionName;

    @NotNull(message = "单元日期不能为空")
    private LocalDate sessionDate;

    /** 赛期第几天，从 1 开始 */
    private Integer dayNo;

    @NotBlank(message = "时段不能为空")
    @Size(max = 32, message = "时段长度不能超过 32")
    private String period;

    /** 单元开始时刻 */
    private LocalTime startTime;

    /** 单元结束时刻 */
    private LocalTime endTime;

    /** 主场地 venue.id */
    private Long venueId;

    /** 同时可用场地 ID 列表（落库为 venue_ids_json） */
    private List<Long> venueIds;

    /** 单元可用总时长（分钟），缺省按起止时刻计算 */
    private Integer totalMinutes;

    /** 是否锁定：0 否 / 1 是 */
    private Integer isLocked;

    /** 单元排序号 */
    private Integer sortNo;

    @Size(max = 64, message = "批次号长度不能超过 64")
    private String batchNo;

    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
