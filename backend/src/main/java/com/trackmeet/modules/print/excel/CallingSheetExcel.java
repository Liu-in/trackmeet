package com.trackmeet.modules.print.excel;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.trackmeet.modules.print.constant.PrintSheetKeys;
import com.trackmeet.modules.print.support.PrintText;
import com.trackmeet.modules.print.vo.CallingSheetRowVO;
import lombok.Data;

import java.io.Serializable;

/**
 * 检录表 Excel 行对象（Excel Sheet「检录表」）。
 * <p>
 * 检录时间优先取分组检录时间，为空时按计划开始时间前 20 分钟推算（备注标注「推算」）。
 *
 * @author TrackMeet
 */
@Data
@ExcelIgnoreUnannotated
@ColumnWidth(14)
public class CallingSheetExcel implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = PrintSheetKeys.COL_CALL_TIME, index = 0)
    private String callTime;

    @ExcelProperty(value = PrintSheetKeys.COL_CALL_PLACE, index = 1)
    private String callPlace;

    @ExcelProperty(value = PrintSheetKeys.COL_EVENT, index = 2)
    private String eventName;

    @ExcelProperty(value = PrintSheetKeys.COL_ROUND, index = 3)
    private String roundName;

    @ExcelProperty(value = PrintSheetKeys.COL_HEAT_NO, index = 4)
    private String heatNoText;

    @ExcelProperty(value = PrintSheetKeys.COL_BIB, index = 5)
    private String bibNo;

    @ExcelProperty(value = PrintSheetKeys.COL_ATHLETE, index = 6)
    private String athleteName;

    @ExcelProperty(value = PrintSheetKeys.COL_UNIT, index = 7)
    private String unitName;

    @ExcelProperty(value = PrintSheetKeys.COL_LANE, index = 8)
    private String laneText;

    @ExcelProperty(value = PrintSheetKeys.COL_REMARK, index = 9)
    private String remark;

    /**
     * 由检录表行 VO 构造 Excel 行。
     *
     * @param vo 检录表行
     * @return Excel 行
     */
    public static CallingSheetExcel from(CallingSheetRowVO vo) {
        CallingSheetExcel row = new CallingSheetExcel();
        row.setCallTime(PrintText.text(vo.getCallTime()));
        row.setCallPlace(PrintText.text(vo.getCallPlace()));
        row.setEventName(PrintText.text(vo.getEventName()));
        row.setRoundName(PrintText.text(vo.getRoundName()));
        row.setHeatNoText(PrintText.text(vo.getHeatNoText()));
        row.setBibNo(PrintText.text(vo.getBibNo()));
        row.setAthleteName(PrintText.text(vo.getAthleteName()));
        row.setUnitName(PrintText.text(vo.getUnitName()));
        row.setLaneText(PrintText.text(vo.getLaneText()));
        row.setRemark(PrintText.text(vo.getRemark()));
        return row;
    }
}
