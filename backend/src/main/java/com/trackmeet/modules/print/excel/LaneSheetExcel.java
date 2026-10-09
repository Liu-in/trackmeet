package com.trackmeet.modules.print.excel;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.trackmeet.modules.print.constant.PrintSheetKeys;
import com.trackmeet.modules.print.support.PrintText;
import com.trackmeet.modules.print.vo.LaneSheetRowVO;
import lombok.Data;

import java.io.Serializable;

/**
 * 分道表 Excel 行对象（Excel Sheet「分道表」）。
 * <p>
 * 空道同样输出一行（号码/姓名/单位/种子成绩为 {@code -}），道次数含补空道结果。
 *
 * @author TrackMeet
 */
@Data
@ExcelIgnoreUnannotated
@ColumnWidth(14)
public class LaneSheetExcel implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = PrintSheetKeys.COL_EVENT, index = 0)
    private String eventName;

    @ExcelProperty(value = PrintSheetKeys.COL_ROUND, index = 1)
    private String roundName;

    @ExcelProperty(value = PrintSheetKeys.COL_HEAT_NO, index = 2)
    private String heatNoText;

    @ExcelProperty(value = PrintSheetKeys.COL_LANE, index = 3)
    private Integer laneNo;

    @ExcelProperty(value = PrintSheetKeys.COL_BIB, index = 4)
    private String bibNo;

    @ExcelProperty(value = PrintSheetKeys.COL_ATHLETE, index = 5)
    private String athleteName;

    @ExcelProperty(value = PrintSheetKeys.COL_UNIT, index = 6)
    private String unitName;

    @ExcelProperty(value = PrintSheetKeys.COL_SEED, index = 7)
    private String seedResult;

    @ExcelProperty(value = PrintSheetKeys.COL_REMARK, index = 8)
    private String remark;

    /**
     * 由分道表行 VO 构造 Excel 行。
     *
     * @param vo 分道表行
     * @return Excel 行
     */
    public static LaneSheetExcel from(LaneSheetRowVO vo) {
        LaneSheetExcel row = new LaneSheetExcel();
        row.setEventName(PrintText.text(vo.getEventName()));
        row.setRoundName(PrintText.text(vo.getRoundName()));
        row.setHeatNoText(PrintText.text(vo.getHeatNoText()));
        row.setLaneNo(vo.getLaneNo());
        row.setBibNo(PrintText.text(vo.getBibNo()));
        row.setAthleteName(PrintText.text(vo.getAthleteName()));
        row.setUnitName(PrintText.text(vo.getUnitName()));
        row.setSeedResult(PrintText.text(vo.getSeedResult()));
        row.setRemark(PrintText.text(vo.getRemark()));
        return row;
    }
}
