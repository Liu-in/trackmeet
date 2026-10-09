package com.trackmeet.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项表 sys_dict_item。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class SysDictItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属字典类型编码 */
    @TableField("dict_type")
    private String dictType;

    /** 显示名 */
    @TableField("item_label")
    private String itemLabel;

    /** 存储码 */
    @TableField("item_value")
    private String itemValue;

    /** 上级字典项码，支持级联 */
    @TableField("parent_value")
    private String parentValue;

    /** 状态标签样式类 */
    @TableField("css_class")
    private String cssClass;

    /** 1 为默认选中项 */
    @TableField("is_default")
    private Integer isDefault;

    /** 同类型内排序号 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 扩展属性（JSON 文本） */
    @TableField("extra_json")
    private String extraJson;

    /** 状态：ENABLED / DISABLED */
    @TableField("status")
    private String status;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
