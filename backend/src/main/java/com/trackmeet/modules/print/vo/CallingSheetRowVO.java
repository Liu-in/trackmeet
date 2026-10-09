package com.trackmeet.modules.print.vo;

import com.trackmeet.modules.print.support.PrintText;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * 检录表行：检录时间 / 检录处 / 项目 / 轮次 / 组次 / 号码 / 姓名 / 单位 / 道次。
 * <p>
 * 检录时间优先取 {@code heat.call_time}；为空时用 {@code schedule.plan_start_time - 20 分钟} 兜底，
 * 再退化为本组开始时间 - 20 分钟，并在 {@link #callTimeEstimated} 与备注中标注「推算」。
 *
 * @author TrackMeet
 */
@Data
public class CallingSheetRowVO implements RowConvertible, Serializable {

    private static final long serialVersionUID = 1L;

    /** 检录时间 HH:mm */
    private String callTime;

    /** 检录处（heat.call_place，未设置输出 - 并在备注提示） */
    private String callPlace;

    /** 项目（含组别前缀） */
    private String eventName;

    /** 轮次名称 */
    private String roundName;

    /** 组次（组名优先，为空时「第N组」） */
    private String heatNoText;

    /** 组号（原始值） */
    private Integer heatNo;

    /** 号码布号 */
    private String bibNo;

    /** 姓名（接力为队长姓名） */
    private String athleteName;

    /** 参赛单位 */
    private String unitName;

    /** 道次号（径赛） */
    private Integer laneNo;

    /** 出场顺序号（田赛） */
    private Integer orderNo;

    /** 道次显示文本：道次数字 / 顺序N / - */
    private String laneText;

    /** 检录时间是否为系统推算（true 表示由计划开始时间前 20 分钟兜底） */
    private Boolean callTimeEstimated;

    /** 备注（推算说明、检录处缺失说明等） */
    private String remark;

    /**
     * 转为表头 {@code PrintSheetKeys.HEADERS_CALLING} 对应的一行单元格。
     *
     * @return 行数据
     */
    public List<String> toRow() {
        return Arrays.asList(
                PrintText.text(callTime),
                PrintText.text(callPlace),
                PrintText.text(eventName),
                PrintText.text(roundName),
                PrintText.text(heatNoText),
                PrintText.text(bibNo),
                PrintText.text(athleteName),
                PrintText.text(unitName),
                PrintText.text(laneText),
                PrintText.text(remark));
    }
}
