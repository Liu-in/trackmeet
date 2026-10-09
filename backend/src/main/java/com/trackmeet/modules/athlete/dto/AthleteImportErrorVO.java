package com.trackmeet.modules.athlete.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 单行导入错误（前端表格展示与错误报告下载共用）。
 *
 * @author TrackMeet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AthleteImportErrorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Excel 行号（含表头，从 2 开始） */
    private Integer rowNum;

    /** 姓名 */
    private String athleteName;

    /** 身份证号 */
    private String idCard;

    /** 单位编码 */
    private String unitCode;

    /** 组别编码 */
    private String groupCode;

    /** 错误原因（多问题时以「；」分隔） */
    private String errorMessage;
}
