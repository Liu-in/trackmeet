package com.trackmeet.modules.athlete.dto;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入模板「填写说明」页的一行。
 *
 * @author TrackMeet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ExcelIgnoreUnannotated
public class AthleteImportTipRow {

    @ExcelProperty(value = "列名", index = 0)
    @ColumnWidth(30)
    private String column;

    @ExcelProperty(value = "是否必填", index = 1)
    @ColumnWidth(12)
    private String required;

    @ExcelProperty(value = "填写说明", index = 2)
    @ColumnWidth(70)
    private String tip;
}
