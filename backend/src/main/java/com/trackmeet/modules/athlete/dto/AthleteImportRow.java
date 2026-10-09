package com.trackmeet.modules.athlete.dto;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 运动员 Excel 导入行（与导入模板表头一一对应）。
 * 说明：单位/组别用「编码」定位（编码在届次内唯一）；也兼容直接填写名称。
 *
 * @author TrackMeet
 */
@Data
@ExcelIgnoreUnannotated
@ColumnWidth(16)
public class AthleteImportRow {

    @ExcelProperty(value = "姓名*", index = 0)
    private String athleteName;

    @ExcelProperty(value = "性别*（男/女）", index = 1)
    private String gender;

    @ExcelProperty(value = "出生日期*（yyyy-MM-dd）", index = 2)
    private String birthDate;

    @ExcelProperty(value = "身份证号*", index = 3)
    private String idCard;

    @ExcelProperty(value = "单位编码*", index = 4)
    private String unitCode;

    @ExcelProperty(value = "组别编码*", index = 5)
    private String groupCode;

    @ExcelProperty(value = "号码（留空自动生成）", index = 6)
    private String bibNo;

    @ExcelProperty(value = "班级", index = 7)
    private String className;

    @ExcelProperty(value = "联系电话", index = 8)
    private String phone;

    @ExcelProperty(value = "监护人", index = 9)
    private String guardian;

    @ExcelProperty(value = "监护人电话", index = 10)
    private String guardianPhone;

    @ExcelProperty(value = "资格状态（待审/合格/不合格/禁赛）", index = 11)
    private String qualificationStatus;

    @ExcelProperty(value = "是否仅参加接力（是/否）", index = 12)
    private String relayOnly;

    @ExcelProperty(value = "备注", index = 13)
    private String remark;
}
