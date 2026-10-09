package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 现行纪录投影行：成绩册「纪录标记」列的只读数据源。
 * <p>
 * 由 {@code StandingMapper#selectCurrentRecords} 从 {@code record} 表按
 * {@code is_current = 1} 读取，用于在成绩行旁标注所破纪录的层级与原纪录成绩。
 *
 * @author TrackMeet
 */
@Data
public class StandingRecordRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 纪录 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 项目 ID */
    private Long eventId;

    /** 组别 ID（组别纪录使用） */
    private Long groupId;

    /** 纪录层级：WORLD / ASIA / NATION / PROVINCE / CITY / MEET / GROUP */
    private String recordType;

    /** 纪录数值（秒、米） */
    private BigDecimal recordValue;

    /** 纪录原文（显示，如 1:02.45） */
    private String recordRaw;

    /** 纪录单位：s 秒 / m 米 / cm 厘米 / pt 分 */
    private String recordUnit;

    /** 纪录保持者姓名 */
    private String athleteName;

    /** 保持者单位/代表队 */
    private String unitName;
}
