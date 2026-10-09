package com.trackmeet.common.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.io.Serializable;

/**
 * 列表查询基类：分页、关键字、届次上下文、排序。
 *
 * @author TrackMeet
 */
@Data
public class BaseQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始（框架内 0 基） */
    private Long page = 1L;

    /** 每页条数，最大 200 */
    private Long size = 20L;

    /** 关键字模糊搜索 */
    private String keyword;

    /** 届次 ID（业务表数据范围） */
    private Long meetId;

    /** 排序字段（驼峰，服务层转换为下划线） */
    private String sortField;

    /** 排序方向：asc / desc */
    private String sortOrder;

    public long pageNo() {
        return (page == null || page < 1) ? 1L : page;
    }

    public long pageSize() {
        long value = (size == null) ? 20L : size;
        if (value < 1) {
            value = 1;
        }
        if (value > 200) {
            value = 200;
        }
        return value;
    }

    public boolean isAsc() {
        return !"desc".equalsIgnoreCase(sortOrder);
    }

    /**
     * 转换为 MyBatis-Plus 分页对象（页码从 1 开始）。
     */
    public <T> Page<T> toPage() {
        return new Page<>(pageNo(), pageSize());
    }
}
