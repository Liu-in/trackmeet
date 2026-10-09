package com.trackmeet.modules.meet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 场地表 venue：比赛场地及其道数与道次顺序配置，是道次分配算法的输入。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("venue")
public class Venue extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 场地类型：径赛跑道 */
    public static final String TYPE_TRACK = "TRACK";
    /** 场地类型：田赛场地 */
    public static final String TYPE_FIELD = "FIELD";
    /** 场地类型：馆内 */
    public static final String TYPE_INDOOR = "INDOOR";

    /** 届次 ID */
    @TableField("meet_id")
    private Long meetId;

    /** 场地名称 */
    @TableField("venue_name")
    private String venueName;

    /** 场地编码（届次内唯一） */
    @TableField("venue_code")
    private String venueCode;

    /** 类型：TRACK 径赛 / FIELD 田赛 / INDOOR 馆内 */
    @TableField("venue_type")
    private String venueType;

    /** 道数（6 或 8） */
    @TableField("lane_count")
    private Integer laneCount;

    /** 道次优先顺序 JSON，默认 8 道 [4,5,3,6,2,7,1,8] */
    @TableField("lane_order_json")
    private String laneOrderJson;

    /** 单道宽度（米） */
    @TableField("lane_width")
    private BigDecimal laneWidth;

    /** 场地位置描述 */
    @TableField("location")
    private String location;

    /** 可容纳观众人数 */
    @TableField("capacity")
    private Integer capacity;

    /** 是否配置风速测量设备：0 否 / 1 是 */
    @TableField("is_wind_measured")
    private Integer isWindMeasured;

    /** 是否具备电子计时：0 否 / 1 是 */
    @TableField("is_electric_timing")
    private Integer isElectricTiming;

    /** 田赛区配置 JSON */
    @TableField("field_zone_json")
    private String fieldZoneJson;

    /** 可用情况扩展 JSON */
    @TableField("available_json")
    private String availableJson;

    /** 场地联系人 */
    @TableField("contact_name")
    private String contactName;

    /** 联系人电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
