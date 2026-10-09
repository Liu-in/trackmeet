package com.trackmeet.modules.system.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询条件：关键字匹配操作人登录名。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 业务模块，如 SYSTEM */
    private String module;

    /** 动作码，如 ADD / EDIT / DELETE */
    private String action;

    /** 结果：SUCCESS 成功 / FAIL 失败 */
    private String result;

    /** 操作时间起（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    /** 操作时间止（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
