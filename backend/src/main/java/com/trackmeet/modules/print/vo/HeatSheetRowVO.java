package com.trackmeet.modules.print.vo;

import com.trackmeet.modules.print.support.PrintText;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * 分组表行：某项目某轮次某组内的一名参赛者。
 * <p>
 * 项目 / 轮次 / 组次 / 道次或顺序 / 号码 / 姓名 / 单位 / 种子成绩，另加备注
 * （种子选手、弃权、未到、田赛分批等标记）。
 * 径赛输出道次（{@code 第N道}），田赛输出出场顺序（{@code 第N位}）。
 *
 * @author TrackMeet
 */
@Data
public class HeatSheetRowVO implements RowConvertible, Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目（含组别前缀） */
    private String eventName;

    /** 轮次名称 */
    private String roundName;

    /** 组次（组名优先，为空时「第N组」） */
    private String heatNoText;

    /** 组号（原始值） */
    private Integer heatNo;

    /** 道次号（径赛，可能为空） */
    private Integer laneNo;

    /** 出场顺序号（田赛，可能为空） */
    private Integer orderNo;

    /** 道次/顺序显示文本：第N道 / 第N位 / - */
    private String laneOrderText;

    /** 号码布号 */
    private String bibNo;

    /** 姓名（接力为队长姓名） */
    private String athleteName;

    /** 参赛单位 */
    private String unitName;

    /** 种子成绩（原文优先，否则数值 + 单位） */
    private String seedResult;

    /** 参赛状态名称：未检录/已检录/到检/未到/弃权/未出发 */
    private String memberStatusName;

    /** 备注 */
    private String remark;

    /**
     * 转为表头 {@code PrintSheetKeys.HEADERS_HEAT} 对应的一行单元格。
     *
     * @return 行数据
     */
    public List<String> toRow() {
        return Arrays.asList(
                PrintText.text(eventName),
                PrintText.text(roundName),
                PrintText.text(heatNoText),
                PrintText.text(laneOrderText),
                PrintText.text(bibNo),
                PrintText.text(athleteName),
                PrintText.text(unitName),
                PrintText.text(seedResult),
                PrintText.text(remark));
    }
}
