package com.trackmeet.modules.meet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运动会（届次）表：一届运动会的根实体，是所有业务数据的隔离边界与全局开关。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meet")
public class Meet extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：草稿 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态：报名中 */
    public static final String STATUS_REG_OPEN = "REG_OPEN";
    /** 状态：报名截止 */
    public static final String STATUS_REG_CLOSED = "REG_CLOSED";
    /** 状态：编排中 */
    public static final String STATUS_SCHEDULING = "SCHEDULING";
    /** 状态：编排完成 */
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    /** 状态：比赛进行 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 状态：已结束 */
    public static final String STATUS_FINISHED = "FINISHED";
    /** 状态：归档（终态） */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 运动会名称 */
    @NotBlank(message = "运动会名称不能为空")
    @Size(max = 100, message = "运动会名称长度不能超过 100")
    @TableField("meet_name")
    private String meetName;

    /** 届次标识（如「2025年第19届」） */
    @Size(max = 50, message = "届次标识长度不能超过 50")
    @TableField("edition")
    private String edition;

    /** 简称（大屏、证书使用） */
    @Size(max = 50, message = "简称长度不能超过 50")
    @TableField("short_name")
    private String shortName;

    /** 主办单位 */
    @Size(max = 100, message = "主办单位长度不能超过 100")
    @TableField("host")
    private String host;

    /** 承办单位 */
    @Size(max = 100, message = "承办单位长度不能超过 100")
    @TableField("organizer")
    private String organizer;

    /** 协办单位 */
    @Size(max = 100, message = "协办单位长度不能超过 100")
    @TableField("co_organizer")
    private String coOrganizer;

    /** 比赛开始日期 */
    @TableField("start_date")
    private LocalDate startDate;

    /** 比赛结束日期 */
    @TableField("end_date")
    private LocalDate endDate;

    /** 举办地点 */
    @Size(max = 100, message = "举办地点长度不能超过 100")
    @TableField("place")
    private String place;

    /** 报名开始时间 */
    @TableField("reg_start_time")
    private LocalDateTime regStartTime;

    /** 报名截止时间 */
    @TableField("reg_end_time")
    private LocalDateTime regEndTime;

    /** 届次状态（八态：DRAFT/REG_OPEN/REG_CLOSED/SCHEDULING/SCHEDULED/RUNNING/FINISHED/ARCHIVED） */
    @Size(max = 32, message = "届次状态长度不能超过 32")
    @TableField("status")
    private String status;

    /** 竞赛规程 HTML */
    @TableField("regulation_html")
    private String regulationHtml;

    /** 规程附件 sys_file.id */
    @TableField("regulation_file_id")
    private Long regulationFileId;

    /** 会徽附件 sys_file.id */
    @TableField("logo_file_id")
    private Long logoFileId;

    /** 是否可作为复制模板：1 是 / 0 否 */
    @TableField("is_template")
    private Integer isTemplate;

    /** 届次默认规则扩展（详见 meet_rule_config） */
    @TableField("config_json")
    private String configJson;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
