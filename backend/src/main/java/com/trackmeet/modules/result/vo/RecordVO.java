package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 纪录视图：{@code record} 表全字段 + 组别名称展示字段。
 * <p>
 * 列名逐字对照 {@code sql/02-schema-meet-athlete.sql} 的 {@code record} 建表语句。
 *
 * @author TrackMeet
 */
@Data
public class RecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 纪录 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    private String recordType;

    /** 关联项目 ID */
    private Long eventId;

    /** 项目名称 */
    private String eventName;

    /** 关联组别 ID */
    private Long groupId;

    /** 组别归并码 */
    private String groupCode;

    /** 性别：MALE/FEMALE/MIXED */
    private String gender;

    /** 是否接力项目纪录：0 否 / 1 是 */
    private Integer isRelay;

    /** 纪录保持者姓名 */
    private String athleteName;

    /** 保持者单位/代表队 */
    private String unitName;

    /** 国家或地区 */
    private String regionName;

    /** 纪录数值（秒、米） */
    private BigDecimal recordValue;

    /** 纪录原文（显示） */
    private String recordRaw;

    /** 单位：s/m/cm/pt */
    private String recordUnit;

    /** 创造时风速（m/s） */
    private BigDecimal windSpeed;

    /** 风速是否合规：0 不合规 / 1 合规 */
    private Integer isWindLegal;

    /** 创造日期 */
    private LocalDate recordDate;

    /** 创造地点 */
    private String recordPlace;

    /** 是否现行纪录：1 现行 / 0 历史 */
    private Integer isCurrent;

    /** 数据来源 */
    private String source;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 修改时间 */
    private LocalDateTime updateTime;

    /* ======================= 关联展示字段 ======================= */

    /** 组别名称 */
    private String groupName;

    /** 项目编码 */
    private String eventCode;

    /** 项目类型 */
    private String eventType;

    /** 当前对标成绩数（本纪录已被多少条成绩引用为对标纪录） */
    private Long referencedCount;
}
