package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 日程发布/撤回入参。
 *
 * @author TrackMeet
 */
@Data
public class SchedulePublishDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发布状态：PUBLISHED 已发布 / DRAFT 草稿 */
    @NotBlank(message = "发布状态不能为空")
    @Size(max = 32, message = "发布状态长度不能超过 32")
    private String publishStatus;
}
