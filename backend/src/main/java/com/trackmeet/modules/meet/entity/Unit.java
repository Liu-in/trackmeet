package com.trackmeet.modules.meet.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 参赛单位表：参赛主体的组织单元，承载号码布前缀、领队教练归属与单位级报名上限。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("unit")
public class Unit extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 状态：停用 */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 届次隔离字段 */
    @TableField("meet_id")
    private Long meetId;

    /** 单位名称 */
    @NotBlank(message = "单位名称不能为空")
    @Size(max = 100, message = "单位名称长度不能超过 100")
    @TableField("unit_name")
    private String unitName;

    /** 单位编码（届次内唯一） */
    @NotBlank(message = "单位编码不能为空")
    @Size(max = 64, message = "单位编码长度不能超过 64")
    @TableField("unit_code")
    private String unitCode;

    /** 单位简称（榜单、大屏） */
    @Size(max = 50, message = "单位简称长度不能超过 50")
    @TableField("short_name")
    private String shortName;

    /** 类型：SCHOOL 学校 / CLUB 俱乐部 / OTHER 其他 */
    @Size(max = 32, message = "单位类型长度不能超过 32")
    @TableField("unit_type")
    private String unitType;

    /** 号码布前缀（如「一中」） */
    @Size(max = 50, message = "号码布前缀长度不能超过 50")
    @TableField("number_prefix")
    private String numberPrefix;

    /** 领队姓名（主联系人） */
    @Size(max = 50, message = "领队姓名长度不能超过 50")
    @TableField("leader")
    private String leader;

    /** 主教练姓名 */
    @Size(max = 50, message = "主教练姓名长度不能超过 50")
    @TableField("coach")
    private String coach;

    /** 联系电话 */
    @Size(max = 50, message = "联系电话长度不能超过 50")
    @TableField("phone")
    private String phone;

    /** 通信地址 */
    @Size(max = 100, message = "通信地址长度不能超过 100")
    @TableField("address")
    private String address;

    /** 单位标志附件 sys_file.id */
    @TableField("logo_file_id")
    private Long logoFileId;

    /** 可参加的组别范围（组别编码数组 JSON） */
    @TableField("group_scope_json")
    private String groupScopeJson;

    /** 单位报名人数上限（0 表示不限） */
    @TableField("total_quota")
    private Integer totalQuota;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @Size(max = 32, message = "状态长度不能超过 32")
    @TableField("status")
    private String status;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    @TableField("remark")
    private String remark;
}
