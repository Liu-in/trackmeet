package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型表 sys_dict_type。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 字典类型编码，唯一，如 event_type */
    @TableField("dict_type")
    private String dictType;

    /** 字典类型名称 */
    @TableField("dict_name")
    private String dictName;

    /** 字典分组，如 TRACK_FIELD */
    @TableField("dict_group")
    private String dictGroup;

    /** 1 系统内置，禁止删除 */
    @TableField("is_system")
    private Integer isSystem;

    /** 1 全局字典，届次间共用 */
    @TableField("is_global")
    private Integer isGlobal;

    /** 状态：ENABLED / DISABLED */
    @TableField("status")
    private String status;

    /** 列表显示顺序 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 扩展属性（JSON 文本） */
    @TableField("extra_json")
    private String extraJson;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
