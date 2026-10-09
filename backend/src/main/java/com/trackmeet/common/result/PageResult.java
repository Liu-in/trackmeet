package com.trackmeet.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一分页结构：{ records, total, page, size, pages }。
 *
 * @param <T> 记录类型
 * @author TrackMeet
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records = new ArrayList<>();
    /** 总记录数 */
    private long total;
    /** 当前页码 */
    private long page;
    /** 每页条数 */
    private long size;
    /** 总页数 */
    private long pages;

    public static <T> PageResult<T> of(IPage<T> iPage) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(iPage.getRecords() == null ? new ArrayList<>() : iPage.getRecords());
        result.setTotal(iPage.getTotal());
        result.setPage(iPage.getCurrent());
        result.setSize(iPage.getSize());
        result.setPages(iPage.getPages());
        return result;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records == null ? new ArrayList<>() : records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return result;
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(new ArrayList<>(), 0L, page, size);
    }
}
