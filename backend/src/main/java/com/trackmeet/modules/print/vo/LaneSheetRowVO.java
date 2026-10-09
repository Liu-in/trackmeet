package com.trackmeet.modules.print.vo;

import com.trackmeet.modules.print.support.PrintText;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * 分道表行：径赛某项目某轮次某组的 1~N 道。
 * <p>
 * 未分配人员的空道同样输出一行（号码/姓名/单位/种子成绩均为 {@code -}），
 * 便于按道核对与现场点名；道数取 {@code heat.lane_count}，为空时用场地道数兜底，默认 8 道。
 *
 * @author TrackMeet
 */
@Data
public class LaneSheetRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目（含组别前缀） */
    private String eventName;

    /** 轮次名称 */
    private String roundName;

    /** 组次（组名优先，为空时「第N组」） */
    private String heatNoText;

    /** 组号（原始值） */
    private Integer heatNo;

    /** 道次号（1 起） */
    private Integer laneNo;

    /** 本组道数 */
    private Integer laneCount;

    /** 号码布号，空道为 - */
    private String bibNo;

    /** 姓名，空道为 - */
    private String athleteName;

    /** 参赛单位，空道为 - */
    private String unitName;

    /** 种子成绩，空道为 - */
    private String seedResult;

    /** 参赛状态名称，空道为 - */
    private String memberStatusName;

    /** 备注 */
    private String remark;

    /** 是否空道（未分配人员） */
    private Boolean emptyLane;

    /**
     * 转为表头 {@code PrintSheetKeys.HEADERS_LANE} 对应的一行单元格。
     *
     * @return 行数据
     */
    public List<String> toRow() {
        return Arrays.asList(
                PrintText.text(eventName),
                PrintText.text(roundName),
                PrintText.text(heatNoText),
                PrintText.number(laneNo),
                PrintText.text(bibNo),
                PrintText.text(athleteName),
                PrintText.text(unitName),
                PrintText.text(seedResult),
                PrintText.text(remark));
    }
}
