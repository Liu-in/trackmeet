package com.trackmeet.modules.schedule.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 自动编排入参（预览与落库共用；regenerate 强制 apply=true）。
 *
 * @author TrackMeet
 */
@Data
public class ScheduleAutoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 编排范围：全部 */
    public static final String SCOPE_ALL = "ALL";
    /** 编排范围：按竞赛单元 */
    public static final String SCOPE_SESSION = "SESSION";
    /** 编排范围：按组别 */
    public static final String SCOPE_GROUP = "GROUP";
    /** 编排范围：按项目 */
    public static final String SCOPE_EVENT = "EVENT";

    /** 届次 ID（缺省取当前届次上下文） */
    private Long meetId;

    /** 范围：ALL / SESSION / GROUP / EVENT，缺省 ALL */
    private String scope;

    /** 单元 ID 列表（scope=SESSION 生效） */
    private List<Long> sessionIds;

    /** 组别 ID 列表（scope=GROUP 生效） */
    private List<Long> groupIds;

    /** 项目 ID 列表（scope=EVENT 生效） */
    private List<Long> eventIds;

    /** 是否保留已锁定日程（缺省 true，锁定项作为固定约束不重排） */
    private Boolean keepLocked;

    /** 兼项最小间隔（分钟），缺省取 meet_rule_config.conflict_interval_min（默认 30） */
    private Integer athleteIntervalMin;

    /** 是否落库（缺省 false 仅预览；regenerate 强制 true） */
    private Boolean apply;

    /** 田赛项目是否允许并行排入（缺省 false） */
    private Boolean parallelFieldEvents;
}
