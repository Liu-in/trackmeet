package com.trackmeet.modules.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 字典项视图对象。
 *
 * @author TrackMeet
 */
@Data
public class DictItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字典项 ID */
    private Long id;

    /** 所属字典类型编码 */
    private String dictType;

    /** 显示名，如 径赛 */
    private String itemLabel;

    /** 存储码，如 TRACK */
    private String itemValue;

    /** 上级字典项码，支持级联 */
    private String parentValue;

    /** 状态标签样式类 */
    private String cssClass;

    /** 1 为默认选中项 */
    private Integer isDefault;

    /** 同类型内排序号 */
    private Integer sortNo;

    /** 扩展属性（JSON 文本） */
    private String extraJson;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    private String status;

    /** 备注 */
    private String remark;
}
