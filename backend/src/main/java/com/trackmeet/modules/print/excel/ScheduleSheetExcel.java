package com.trackmeet.modules.print.excel;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.trackmeet.modules.print.constant.PrintSheetKeys;
import com.trackmeet.modules.print.support.PrintText;
import com.trackmeet.modules.print.vo.ScheduleSheetRowVO;
import lombok.Data;

import java.io.Serializable;

/**
 * 总日程表 Excel 行对象（Excel Sheet「总日程表」）。
 * <p>
 * 列名引用 {@link PrintSheetKeys} 常量，与 JSON 表头（{@code SheetBundleVO.headers}）同源，
 * 保证 Excel 与前端 A4 表格列名、列序一致。
 *
 * @author TrackMeet
 */
@Data
@ExcelIgnoreUnannotated
@ColumnWidth(14)
public class ScheduleSheetExcel implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = PrintSheetKeys.COL_DATE, index = 0)
    private String sessionDate;

    @ExcelProperty(value = PrintSheetKeys.COL_PERIOD, index = 1)
    private String periodName;

    @ExcelProperty(value = PrintSheetKeys.COL_TIME, index = 2)
    private String timeRange;

    @ExcelProperty(value = PrintSheetKeys.COL_VENUE, index = 3)
    private String venueName;

    @ExcelProperty(value = PrintSheetKeys.COL_EVENT, index = 4)
    private String eventName;

    @ExcelProperty(value = PrintSheetKeys.COL_ROUND, index = 5)
    private String roundName;

    @ExcelProperty(value = PrintSheetKeys.COL_HEAT_COUNT, index = 6)
    private Integer heatCount;

    @ExcelProperty(value = PrintSheetKeys.COL_DURATION, index = 7)
    private Integer durationMin;

    @ExcelProperty(value = PrintSheetKeys.COL_REMARK, index = 8)
    private String remark;

    @ExcelProperty(value = PrintSheetKeys.COL_PUBLISH, index = 9)
    private String publishStatusName;

    /**
     * 由总日程表行 VO 构造 Excel 行。
     *
     * @param vo 总日程表行
     * @return Excel 行
     */
    public static ScheduleSheetExcel from(ScheduleSheetRowVO vo) {
        ScheduleSheetExcel row = new ScheduleSheetExcel();
        row.setSessionDate(PrintText.text(vo.getSessionDate()));
        row.setPeriodName(PrintText.text(vo.getPeriodName()));
        row.setTimeRange(PrintText.text(vo.getTimeRange()));
        row.setVenueName(PrintText.text(vo.getVenueName()));
        row.setEventName(PrintText.text(vo.getEventName()));
        row.setRoundName(PrintText.text(vo.getRoundName()));
        row.setHeatCount(vo.getHeatCount());
        row.setDurationMin(vo.getDurationMin());
        row.setRemark(PrintText.text(vo.getRemark()));
        row.setPublishStatusName(PrintText.text(vo.getPublishStatusName()));
        return row;
    }
}
