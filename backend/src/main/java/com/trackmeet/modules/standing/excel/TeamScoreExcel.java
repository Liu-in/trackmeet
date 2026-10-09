package com.trackmeet.modules.standing.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serializable;

/**
 * 积分表（团体总分）Excel 行对象：积分表 / 团体总分榜导出。
 *
 * @author TrackMeet
 */
@Data
@ColumnWidth(14)
public class TeamScoreExcel implements Serializable {

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

    /** 团体口径 */
    @ExcelProperty("口径")
    private String scoreTypeName;

    /** 名次分合计 */
    @ExcelProperty("名次分")
    private String rankPointSum;

    /** 破纪录加分合计 */
    @ExcelProperty("纪录加分")
    private String bonusPointSum;

    /** 接力项目得分合计 */
    @ExcelProperty("接力分")
    private String relayPointSum;

    /** 团体总分 */
    @ExcelProperty("团体总分")
    private String totalPoint;

    /** 得分项目数 */
    @ExcelProperty("得分项目数")
    private String eventCount;

    /** 得分人数 */
    @ExcelProperty("得分人数")
    private String athleteCount;

    /** 备注（统计时间 / 批次号） */
    @ColumnWidth(24)
    @ExcelProperty("备注")
    private String remark;
}
