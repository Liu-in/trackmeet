package com.trackmeet.modules.schedule.service.support;

import com.trackmeet.modules.schedule.entity.FieldOrder;
import com.trackmeet.modules.schedule.entity.Heat;
import com.trackmeet.modules.schedule.entity.HeatMember;
import com.trackmeet.modules.schedule.entity.Lane;
import com.trackmeet.modules.schedule.entity.Schedule;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 编排快照的 JSON 载荷：当届「日程 + 分组 + 组内人员 + 道次 + 出场顺序」的完整留档。
 *
 * @author TrackMeet
 */
@Data
public class SnapshotHolder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 序列化格式版本，便于后续兼容处理 */
    private int formatVersion = 1;

    /** 届次 ID */
    private Long meetId;

    /** 快照类型：AUTO / MANUAL / RESTORE */
    private String snapshotType;

    /** 快照范围：ALL / SESSION / EVENT */
    private String scopeType;

    /** 对应编排批次号 */
    private String batchNo;

    /** 日程条目 */
    private List<Schedule> schedules = new ArrayList<>();

    /** 分组 */
    private List<Heat> heats = new ArrayList<>();

    /** 组内人员 */
    private List<HeatMember> members = new ArrayList<>();

    /** 道次 */
    private List<Lane> lanes = new ArrayList<>();

    /** 田赛出场顺序 */
    private List<FieldOrder> fieldOrders = new ArrayList<>();
}
