package com.trackmeet.modules.result.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 纪录维护入参（record 表）。
 *
 * @author TrackMeet
 */
@Data
public class RecordSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 纪录 ID：修改时必填；新增时留空 */
    private Long id;

    /** 届次 ID，缺省取当前届次上下文 */
    private Long meetId;

    /** 纪录层级：WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP */
    @NotBlank(message = "纪录层级不能为空")
    @Pattern(regexp = "^(WORLD|ASIA|NATION|PROVINCE|CITY|MEET|GROUP)$",
            message = "纪录层级只能是 WORLD/ASIA/NATION/PROVINCE/CITY/MEET/GROUP")
    private String recordType;

    /** 关联项目 event.id */
    @NotNull(message = "项目不能为空")
    private Long eventId;

    /** 项目名称（冗余显示，留空时服务层按项目补全） */
    @Size(max = 100, message = "项目名称长度不能超过 100")
    private String eventName;

    /** 关联组别 meet_group.id（组别纪录使用） */
    private Long groupId;

    /** 组别归并码（对应 meet_group.record_group_code） */
    @Size(max = 64, message = "组别归并码长度不能超过 64")
    private String groupCode;

    /** 性别：MALE/FEMALE/MIXED */
    @Pattern(regexp = "^(MALE|FEMALE|MIXED)?$", message = "性别码只能是 MALE/FEMALE/MIXED")
    private String gender;

    /** 是否接力项目纪录：0 否 / 1 是 */
    private Integer isRelay;

    /** 纪录保持者姓名 */
    @Size(max = 50, message = "纪录保持者姓名长度不能超过 50")
    private String athleteName;

    /** 保持者单位/代表队 */
    @Size(max = 100, message = "保持者单位长度不能超过 100")
    private String unitName;

    /** 国家或地区（世界、亚洲、全国纪录） */
    @Size(max = 100, message = "国家或地区长度不能超过 100")
    private String regionName;

    /** 纪录数值（秒、米） */
    @DecimalMin(value = "0", message = "纪录数值不能为负数")
    private BigDecimal recordValue;

    /** 纪录原文（显示，如 1:02.45） */
    @Size(max = 16, message = "纪录原文长度不能超过 16")
    private String recordRaw;

    /** 单位：s 秒 / m 米 / cm 厘米 / pt 分 */
    @Size(max = 32, message = "单位长度不能超过 32")
    private String recordUnit;

    /** 创造时风速（米/秒，正值顺风） */
    private BigDecimal windSpeed;

    /** 风速是否合规：0 不合规 / 1 合规（未测风速为空） */
    private Integer isWindLegal;

    /** 创造日期 */
    private LocalDate recordDate;

    /** 创造地点 */
    @Size(max = 100, message = "创造地点长度不能超过 100")
    private String recordPlace;

    /** 是否现行纪录：1 现行 / 0 历史纪录（缺省 1） */
    private Integer isCurrent;

    /** 数据来源（协会公布、组委会认定） */
    @Size(max = 100, message = "数据来源长度不能超过 100")
    private String source;
}
