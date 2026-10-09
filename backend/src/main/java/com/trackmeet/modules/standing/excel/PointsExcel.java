package com.trackmeet.modules.standing.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

/**
 * 积分明细表 Excel 行对象：积分明细下钻导出与打印。
 *
 * @author TrackMeet
 */
@Data
@ColumnWidth(13)
public class PointsExcel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 */
    @ColumnWidth(20)
    @ExcelProperty("项目")
    private String eventName;

    /** 轮次 */
    @ExcelProperty("轮次")
    private String roundName;

    /** 组别 */
    @ColumnWidth(16)
    @ExcelProperty("组别")
    private String groupName;

    /** 名次 */
    @ExcelProperty("名次")
    private String rankDisplay;

    /** 号码 */
    @ExcelProperty("号码")
    private String bibNo;

    /** 姓名 / 队名 */
    @ExcelProperty("姓名")
    private String athleteName;

    /** 单位 */
    @ColumnWidth(20)
    @ExcelProperty("单位")
    private String unitName;

    /** 名次分 */
    @ExcelProperty("名次分")
    private String rankPoint;

    /** 纪录加分 */
    @ExcelProperty("纪录加分")
    private String bonusPoint;

    /** 接力倍数 */
    @ExcelProperty("倍数")
    private String relayFactor;

    /** 合计分 */
    @ExcelProperty("合计分")
    private String totalPoint;

    /** 名次分方案 */
    @ExcelProperty("名次分方案")
    private String scoreRuleCode;

    /** 命中的加分规则 */
    @ColumnWidth(24)
    @ExcelProperty("加分规则")
    private String bonusRuleIds;
}
