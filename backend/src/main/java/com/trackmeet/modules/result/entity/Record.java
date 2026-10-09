package com.trackmeet.modules.result.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * record 纪录表：多层级纪录库（世界到组别），支持风速与是否现行标记，供成绩实时对标。
 * <p>
 * 字段与 {@code sql/02-schema-meet-athlete.sql} 的 {@code record} 表逐列一致。
 * 本表按「届次 + 项目 + 层级」维护纪录值，成绩模块承担查询、维护与破纪录认定（回写
 * {@code result.is_record_confirmed} / {@code result.record_id}）职责。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("record")
public class Record extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 纪录层级：世界 */
    public static final String TYPE_WORLD = "WORLD";
    /** 纪录层级：亚洲 */
    public static final String TYPE_ASIA = "ASIA";
    /** 纪录层级：全国 */
    public static final String TYPE_NATION = "NATION";
    /** 纪录层级：省 */
    public static final String TYPE_PROVINCE = "PROVINCE";
    /** 纪录层级：市 */
    public static final String TYPE_CITY = "CITY";
    /** 纪录层级：赛会 */
    public static final String TYPE_MEET = "MEET";
    /** 纪录层级：组别 */
    public static final String TYPE_GROUP = "GROUP";

    /** 现行纪录 */
    public static final Integer CURRENT = 1;
    /** 历史纪录 */
    public static final Integer HISTORY = 0;
    /** 风速合规 */
    public static final Integer WIND_LEGAL = 1;
    /** 风速不合规 */
    public static final Integer WIND_ILLEGAL = 0;
    /** 布尔真 */
    public static final Integer YES = 1;
    /** 布尔假 */
    public static final Integer NO = 0;

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP（必填） */
    @TableField("record_type")
    private String recordType;

    /** 关联项目 event.id */
    @TableField("event_id")
    private Long eventId;

    /** 项目名称（冗余显示） */
    @TableField("event_name")
    private String eventName;

    /** 关联组别 meet_group.id（组别纪录使用） */
    @TableField("group_id")
    private Long groupId;

    /** 组别归并码（对应 meet_group.record_group_code） */
    @TableField("group_code")
    private String groupCode;

    /** 性别：MALE 男 / FEMALE 女 / MIXED 混合 */
    @TableField("gender")
    private String gender;

    /** 是否接力项目纪录：0 否 / 1 是 */
    @TableField("is_relay")
    private Integer isRelay;

    /** 纪录保持者姓名 */
    @TableField("athlete_name")
    private String athleteName;

    /** 保持者单位/代表队 */
    @TableField("unit_name")
    private String unitName;

    /** 国家或地区（世界、亚洲、全国纪录） */
    @TableField("region_name")
    private String regionName;

    /** 纪录数值（秒、米，用于排序比较） */
    @TableField("record_value")
    private BigDecimal recordValue;

    /** 纪录原文（显示，如 1:02.45） */
    @TableField("record_raw")
    private String recordRaw;

    /** 单位：s 秒 / m 米 / cm 厘米 / pt 分 */
    @TableField("record_unit")
    private String recordUnit;

    /** 创造时风速（米/秒，正值顺风） */
    @TableField("wind_speed")
    private BigDecimal windSpeed;

    /** 风速是否合规：0 不合规 / 1 合规（未测风速为空） */
    @TableField("is_wind_legal")
    private Integer isWindLegal;

    /** 创造日期 */
    @TableField("record_date")
    private LocalDate recordDate;

    /** 创造地点 */
    @TableField("record_place")
    private String recordPlace;

    /** 是否现行纪录：1 现行 / 0 历史纪录 */
    @TableField("is_current")
    private Integer isCurrent;

    /** 数据来源（协会公布、组委会认定） */
    @TableField("source")
    private String source;
}
