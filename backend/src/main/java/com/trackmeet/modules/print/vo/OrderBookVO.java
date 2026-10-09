package com.trackmeet.modules.print.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 秩序册（JSON）：届次信息 + 四张表的行数据 + 便于前端通用 A4 渲染的「表结构」集合。
 * <p>
 * 字段与前端 {@code frontend/src/api/print.ts} 的 {@code OrderBook} 契约一致：
 * {@code meetInfo}、{@code scheduleSheets}、{@code heatSheets}、{@code laneSheets}、{@code callingSheets}；
 * 额外的 {@code *Rows} 为结构化行数据（便于二次加工/程序消费），不影响前端渲染。
 *
 * @author TrackMeet
 */
@Data
public class OrderBookVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次信息（封面/页眉） */
    private MeetInfoVO meetInfo;

    /** 是否只包含已发布日程（false 表示含草稿，草稿在状态列标记「草稿」） */
    private Boolean publishOnly;

    /** 总日程表（单表） */
    private List<SheetBundleVO> scheduleSheets = new ArrayList<>();

    /** 分组表（按项目 × 轮次拆分为多张表） */
    private List<SheetBundleVO> heatSheets = new ArrayList<>();

    /** 分道表（按径赛项目 × 轮次 × 组拆分为多张表） */
    private List<SheetBundleVO> laneSheets = new ArrayList<>();

    /** 检录表（单表，按检录时间排序） */
    private List<SheetBundleVO> callingSheets = new ArrayList<>();

    /** 总日程表结构化行 */
    private List<ScheduleSheetRowVO> scheduleRows = new ArrayList<>();

    /** 分组表结构化行 */
    private List<HeatSheetRowVO> heatRows = new ArrayList<>();

    /** 分道表结构化行 */
    private List<LaneSheetRowVO> laneRows = new ArrayList<>();

    /** 检录表结构化行 */
    private List<CallingSheetRowVO> callingRows = new ArrayList<>();
}
