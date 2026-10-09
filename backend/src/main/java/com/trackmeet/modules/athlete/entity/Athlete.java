package com.trackmeet.modules.athlete.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * athlete 运动员表：参赛的「人」，届次内的运动员档案，关联组别与参赛单位。
 * <p>
 * 业务字段与《12-03 字段清单-人员报名与项目规则层》athlete 表逐字段一致；
 * 通用字段（id、create_by、create_time、update_by、update_time、deleted、version）继承自 {@link BaseEntity}。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("athlete")
public class Athlete extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（业务表多租户隔离列，必填） */
    @NotNull(message = "届次不能为空")
    @TableField("meet_id")
    private Long meetId;

    /** 运动员姓名 */
    @NotBlank(message = "运动员姓名不能为空")
    @Size(max = 50, message = "运动员姓名长度不能超过 50")
    @TableField("athlete_name")
    private String athleteName;

    /** 性别码 MALE/FEMALE */
    @Pattern(regexp = "^(MALE|FEMALE)?$", message = "性别码只能是 MALE 或 FEMALE")
    @TableField("gender")
    private String gender;

    /** 出生日期，用于组别年龄校验 */
    @TableField("birth_date")
    private LocalDate birthDate;

    /** 身份证/护照号，届次内唯一 */
    @Size(max = 32, message = "身份证/护照号长度不能超过 32")
    @TableField("id_card")
    private String idCard;

    /** 所属组别 meet_group.id，必填 */
    @NotNull(message = "所属组别不能为空")
    @TableField("group_id")
    private Long groupId;

    /** 所属参赛单位 unit.id，必填 */
    @NotNull(message = "所属参赛单位不能为空")
    @TableField("unit_id")
    private Long unitId;

    /** 号码布编号，届次内唯一；逻辑删除时改写为「原值_d{id}」 */
    @Size(max = 32, message = "号码长度不能超过 32")
    @Pattern(regexp = "^[0-9A-Za-z\\-_]*$", message = "号码只能包含字母、数字、中划线或下划线")
    @TableField("bib_no")
    private String bibNo;

    /** 班级/年级名称 */
    @Size(max = 64, message = "班级/年级名称长度不能超过 64")
    @TableField("class_name")
    private String className;

    /** 联系电话 */
    @Size(max = 32, message = "联系电话长度不能超过 32")
    @Pattern(regexp = "^[0-9+\\-() ]*$", message = "联系电话格式不正确")
    @TableField("phone")
    private String phone;

    /** 监护人姓名（青少年赛事） */
    @Size(max = 50, message = "监护人姓名长度不能超过 50")
    @TableField("guardian")
    private String guardian;

    /** 监护人联系电话 */
    @Size(max = 32, message = "监护人联系电话长度不能超过 32")
    @Pattern(regexp = "^[0-9+\\-() ]*$", message = "监护人联系电话格式不正确")
    @TableField("guardian_phone")
    private String guardianPhone;

    /** 资格状态 PENDING/QUALIFIED/DISQUALIFIED/BANNED（与字典 qualification_status 一致） */
    @Pattern(regexp = "^(PENDING|QUALIFIED|DISQUALIFIED|BANNED)?$", message = "资格状态码只能是 PENDING/QUALIFIED/DISQUALIFIED/BANNED")
    @TableField("qualification_status")
    private String qualificationStatus;

    /** 资格审核说明 */
    @Size(max = 100, message = "资格审核说明长度不能超过 100")
    @TableField("qualify_remark")
    private String qualifyRemark;

    /** 是否仅参加接力项目：0 否，1 是（布尔列用 Integer） */
    @TableField("is_relay_only")
    private Integer isRelayOnly;

    /** 照片附件 sys_file.id */
    @TableField("photo_file_id")
    private Long photoFileId;

    /** 数据来源 MANUAL/IMPORT */
    @Pattern(regexp = "^(MANUAL|IMPORT)?$", message = "数据来源码只能是 MANUAL 或 IMPORT")
    @TableField("entry_source")
    private String entrySource;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
