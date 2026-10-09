package com.trackmeet.modules.standing.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

/**
 * 奖牌榜 Excel 行对象：奖牌榜导出。
 *
 * @author TrackMeet
 */
@Data
@ColumnWidth(14)
public class MedalExcel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 名次 */
    @ExcelProperty("名次")
    private String rankDisplay;

    /** 单位 */
    @ExcelProperty("单位")
    private String unitName;

    /** 组别 */
    @ExcelProperty("组别")
    private String groupName;

    /** 金牌数 */
    @ExcelProperty("金牌")
    private String gold;

    /** 银牌数 */
    @ExcelProperty("银牌")
    private String silver;

    /** 铜牌数 */
    @ExcelProperty("铜牌")
    private String bronze;

    /** 奖牌总数 */
    @ExcelProperty("奖牌总数")
    private String totalMedal;

    /** 排序分 */
    @ExcelProperty("排序分")
    private String sortScore;

    /** 排序规则 */
    @ExcelProperty("排序规则")
    private String medalSort;

    /** 备注（统计时间 / 批次号） */
    @ColumnWidth(24)
    @ExcelProperty("备注")
    private String remark;
}
