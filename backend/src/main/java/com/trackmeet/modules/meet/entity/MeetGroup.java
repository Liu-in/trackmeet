package com.trackmeet.modules.meet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 组别表：划分参赛对象（性别、年龄、年级），决定是否计入团体分与使用哪套组别纪录。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meet_group")
public class MeetGroup extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 组别名称（如高中男子组） */
    @NotBlank(message = "组别名称不能为空")
    @Size(max = 100, message = "组别名称长度不能超过 100")
    @TableField("group_name")
    private String groupName;

    /** 组别编码（届次内唯一） */
    @NotBlank(message = "组别编码不能为空")
    @Size(max = 64, message = "组别编码长度不能超过 64")
    @TableField("group_code")
    private String groupCode;

    /** 性别限制：MALE 男 / FEMALE 女 / MIXED 混合 */
    @Size(max = 32, message = "性别限制长度不能超过 32")
    @TableField("gender")
    private String gender;

    /** 最小年龄（含） */
    @TableField("age_min")
    private Integer ageMin;

    /** 最大年龄（含） */
    @TableField("age_max")
    private Integer ageMax;

    /** 出生日期区间起（资格判定基准） */
    @TableField("birth_date_start")
    private LocalDate birthDateStart;

    /** 出生日期区间止 */
    @TableField("birth_date_end")
    private LocalDate birthDateEnd;

    /** 年级范围（如高一至高三） */
    @Size(max = 100, message = "年级范围长度不能超过 100")
    @TableField("grade_range")
    private String gradeRange;

    /** 是否计入团体总分：1 是 / 0 否 */
    @TableField("is_team_score")
    private Integer isTeamScore;

    /** 团体计分归并码（跨组合并计分） */
    @Size(max = 64, message = "团体计分归并码长度不能超过 64")
    @TableField("score_group_code")
    private String scoreGroupCode;

    /** 组别纪录归并码（对应 record） */
    @Size(max = 64, message = "组别纪录归并码长度不能超过 64")
    @TableField("record_group_code")
    private String recordGroupCode;

    /** 显示排序号 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @Size(max = 32, message = "状态长度不能超过 32")
    @TableField("status")
    private String status;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
