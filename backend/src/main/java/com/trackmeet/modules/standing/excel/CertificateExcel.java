package com.trackmeet.modules.standing.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

/**
 * 证书 Excel 行对象：证书清单导出（一行一证书，含编号/姓名/单位/项目/名次/成绩/日期）。
 *
 * @author TrackMeet
 */
@Data
@ColumnWidth(15)
public class CertificateExcel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证书编号 */
    @ColumnWidth(30)
    @ExcelProperty("证书编号")
    private String certNo;

    /** 证书类型 */
    @ExcelProperty("证书类型")
    private String certTypeName;

    /** 姓名 / 队名 */
    @ColumnWidth(16)
    @ExcelProperty("姓名")
    private String athleteName;

    /** 号码 */
    @ExcelProperty("号码")
    private String bibNo;

    /** 单位 */
    @ColumnWidth(20)
    @ExcelProperty("单位")
    private String unitName;

    /** 项目 */
    @ColumnWidth(22)
    @ExcelProperty("项目")
    private String eventName;

    /** 组别 */
    @ColumnWidth(16)
    @ExcelProperty("组别")
    private String groupName;

    /** 名次 */
    @ExcelProperty("名次")
    private String rankDisplay;

    /** 成绩 */
    @ExcelProperty("成绩")
    private String resultText;

    /** 荣誉描述 */
    @ColumnWidth(30)
    @ExcelProperty("荣誉描述")
    private String honorText;

    /** 签发日期 */
    @ExcelProperty("签发日期")
    private String issueDate;

    /** 签发单位 */
    @ColumnWidth(22)
    @ExcelProperty("签发单位")
    private String issuer;

    /** 二维码验真串 */
    @ColumnWidth(36)
    @ExcelProperty("验真串")
    private String qrContent;

    /** 状态 */
    @ExcelProperty("状态")
    private String statusName;
}
