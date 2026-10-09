package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目报名人数汇总投影（跨模块取数：registration）。
 * 非决赛轮次人数 = approvedCount - finalOnlyCount（FINAL_ONLY 报名只参加决赛）。
 *
 * @author TrackMeet
 */
@Data
public class EventCountRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long eventId;
    private Integer approvedCount;
    private Integer finalOnlyCount;
}
