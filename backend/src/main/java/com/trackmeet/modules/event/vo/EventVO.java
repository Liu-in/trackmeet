package com.trackmeet.modules.event.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目列表 / 详情视图对象。
 * <p>
 * 字段名与前端契约严格一致；{@code groupName} 由服务层按 {@code group_id} 关联查询后冗余填充，便于列表直接展示。
 * 布尔列（{@code is_relay} 等）在实体中为 {@link Integer}，对外仍以 Integer 暴露，前端按 0/1 判定。
 *
 * @author TrackMeet
 */
@Data
public class EventVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 项目名称 */
    private String eventName;

    /** 项目编码，届次内唯一 */
    private String eventCode;

    /** 项目类型：TRACK/FIELD/ALLROUND/RELAY */
    private String eventType;

    /** 项目子类型：SPRINT/MIDDLE/LONG/HURDLE/STEEPLE/WALK/JUMP/THROW/RELAY */
    private String subType;

    /** 所属组别 ID */
    private Long groupId;

    /** 所属组别名称（冗余显示） */
    private String groupName;

    /** 适用性别：MALE/FEMALE/MIXED */
    private String gender;

    /** 是否接力项目：0 否 / 1 是 */
    private Integer isRelay;

    /** 接力最少人数 */
    private Integer relayMin;

    /** 接力最多人数 */
    private Integer relayMax;

    /** 每单位每项限报人数 */
    private Integer maxPerUnit;

    /** 项目预计时长（分钟） */
    private Integer durationMin;

    /** 轮次数量 */
    private Integer roundCount;

    /** 是否计风速：0 否 / 1 是 */
    private Integer windApplicable;

    /** 是否计入团体总分：0 否 / 1 是 */
    private Integer isTeamScore;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 显示排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;
}
