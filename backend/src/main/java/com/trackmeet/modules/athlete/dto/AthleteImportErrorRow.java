package com.trackmeet.modules.athlete.dto;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 运动员导入错误明细行（用于生成可下载的错误报告 Excel）。
 *
 * @author TrackMeet
 */
@Data
@ExcelIgnoreUnannotated
@ColumnWidth(16)
public class AthleteImportErrorRow {

    @ExcelProperty(value = "行号", index = 0)
    private Integer rowNum;

    @ExcelProperty(value = "姓名", index = 1)
    private String athleteName;

    @ExcelProperty(value = "性别", index = 2)
    private String gender;

    @ExcelProperty(value = "出生日期", index = 3)
    private String birthDate;

    @ExcelProperty(value = "身份证号", index = 4)
    private String idCard;

    @ExcelProperty(value = "单位编码", index = 5)
    private String unitCode;

    @ExcelProperty(value = "组别编码", index = 6)
    private String groupCode;

    @ExcelProperty(value = "号码", index = 7)
    private String bibNo;

    @ExcelProperty(value = "错误原因", index = 8)
    @ColumnWidth(50)
    private String errorMessage;
}
