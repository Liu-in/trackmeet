package com.trackmeet.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 添加人员到分组入参：以报名记录为锚点。
 *
 * @author TrackMeet
 */
@Data
public class HeatMemberSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 报名记录 ID registration.id */
    @NotNull(message = "报名记录不能为空")
    private Long registrationId;

    /** 组内出发/出场顺序号（从 1 开始；为空则追加到组末） */
    private Integer orderNo;
}
