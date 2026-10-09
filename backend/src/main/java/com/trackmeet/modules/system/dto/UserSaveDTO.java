package com.trackmeet.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户新增 / 修改入参。
 * <p>新增时 {@code password} 必填；修改时忽略该字段（密码仅能通过重置密码接口变更）。
 *
 * @author TrackMeet
 */
@Data
public class UserSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，新增留空，修改时由路径变量指定 */
    private Long id;

    /** 登录名，全局唯一 */
    @NotBlank(message = "登录名不能为空")
    @Size(max = 64, message = "登录名长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{2,63}$",
            message = "登录名只能包含字母、数字、下划线、点、短横线，长度 3-64")
    private String username;

    /** 明文密码，仅新增时使用；序列化时忽略，避免写入操作日志 */
    @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不能超过 50")
    private String realName;

    /** 手机号 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    /** 头像文件 sys_file.id */
    private Long avatarFileId;

    /** 所属部门 sys_dept.id */
    private Long deptId;

    /** 关联参赛单位 unit.id */
    private Long unitId;

    /** 状态：ENABLED 启用 / DISABLED 停用，留空默认 ENABLED */
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态只能为 ENABLED 或 DISABLED")
    private String status;

    /** 列表显示顺序 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 同时分配的角色 ID 集合，null 表示不调整角色 */
    private List<Long> roleIds;
}
