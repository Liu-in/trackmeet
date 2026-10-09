package com.trackmeet.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 编排快照表 schedule_snapshot：一次编排结果的完整版本留档，用于版本快照与回滚。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("schedule_snapshot")
public class ScheduleSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 快照类型：自动编排 */
    public static final String TYPE_AUTO = "AUTO";
    /** 快照类型：手动 */
    public static final String TYPE_MANUAL = "MANUAL";
    /** 快照类型：回滚备份 */
    public static final String TYPE_RESTORE = "RESTORE";

    /** 快照范围：全部 */
    public static final String SCOPE_ALL = "ALL";
    /** 快照范围：按单元 */
    public static final String SCOPE_SESSION = "SESSION";
    /** 快照范围：按项目 */
    public static final String SCOPE_EVENT = "EVENT";

    /** 当前生效版本 */
    public static final Integer CURRENT = 1;
    /** 非当前生效版本 */
    public static final Integer NOT_CURRENT = 0;

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 快照名称，如「自动编排 v3」 */
    @NotBlank(message = "快照名称不能为空")
    @Size(max = 100, message = "快照名称长度不能超过 100")
    @TableField("snapshot_name")
    private String snapshotName;

    /** 对应编排批次号 */
    @Size(max = 64, message = "批次号长度不能超过 64")
    @TableField("batch_no")
    private String batchNo;

    /** 快照类型：AUTO 自动 / MANUAL 手动 / RESTORE 回滚备份 */
    @Size(max = 32, message = "快照类型长度不能超过 32")
    @TableField("snapshot_type")
    private String snapshotType;

    /** 编排结果完整快照（单元/日程/分组/人员/道次，JSON 字符串） */
    @TableField("snapshot_json")
    private String snapshotJson;

    /** 快照内日程条目数 */
    @TableField("item_count")
    private Integer itemCount;

    /** 快照内分组数 */
    @TableField("heat_count")
    private Integer heatCount;

    /** 快照内参赛人员数 */
    @TableField("member_count")
    private Integer memberCount;

    /** 快照内锁定项数量 */
    @TableField("locked_count")
    private Integer lockedCount;

    /** 快照生成时的冲突条数 */
    @TableField("conflict_count")
    private Integer conflictCount;

    /** 快照范围：ALL 全部 / SESSION 按单元 / EVENT 按项目 */
    @Size(max = 32, message = "快照范围长度不能超过 32")
    @TableField("scope_type")
    private String scopeType;

    /** 范围 ID 列表，对应单元或项目（JSON 数组字符串） */
    @TableField("scope_ids_json")
    private String scopeIdsJson;

    /** 是否当前生效版本：0 否 / 1 是（回滚后切换） */
    @TableField("is_current")
    private Integer isCurrent;

    /** 操作人 sys_user.id */
    @TableField("operator_id")
    private Long operatorId;

    /** 被回滚使用的时间 */
    @TableField("restore_time")
    private LocalDateTime restoreTime;

    /** 执行回滚的用户 sys_user.id */
    @TableField("restore_by")
    private Long restoreBy;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
