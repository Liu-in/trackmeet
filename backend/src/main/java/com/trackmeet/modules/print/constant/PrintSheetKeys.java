package com.trackmeet.modules.print.constant;

import java.util.List;

/**
 * 秩序册各类表格的 Sheet 标识、Sheet 名称与表头常量。
 * <p>
 * 说明：本类是「列清单一来源」——{@code vo.SheetBundleVO}（JSON，供前端通用 A4 渲染打印）
 * 与 {@code excel.*}（EasyExcel {@code @ExcelProperty} 列名）统一引用本类常量，
 * 因此 JSON 表头、Excel 表头与列序天然一致，不会出现两处不一致。
 * <p>
 * 约定的四张表与 Excel 的 4 个 Sheet 一一对应：
 * <ol>
 *     <li>总日程表（KEY_SCHEDULE）</li>
 *     <li>分组表（KEY_HEAT）</li>
 *     <li>分道表（KEY_LANE）</li>
 *     <li>检录表（KEY_CALLING）</li>
 * </ol>
 *
 * @author TrackMeet
 */
public final class PrintSheetKeys {

    private PrintSheetKeys() {
    }

    // ------------------------------------------------------------------
    // 一、Sheet 键：JSON 场景下 sheetKey 的前缀（分组表/分道表按项目、轮次、组拆分，键含业务 ID）
    // ------------------------------------------------------------------

    /** 总日程表 Sheet 键 */
    public static final String KEY_SCHEDULE = "schedule";
    /** 分组表 Sheet 键前缀，完整键形如 {@code heat:{eventId}:{roundId}} */
    public static final String KEY_HEAT = "heat";
    /** 分道表 Sheet 键前缀，完整键形如 {@code lane:{heatId}} */
    public static final String KEY_LANE = "lane";
    /** 检录表 Sheet 键 */
    public static final String KEY_CALLING = "calling";

    // ------------------------------------------------------------------
    // 二、Sheet 名称：既是 Excel 的 Sheet 名，也是前端 A4 表格标题
    // ------------------------------------------------------------------

    /** 总日程表 */
    public static final String NAME_SCHEDULE = "总日程表";
    /** 分组表 */
    public static final String NAME_HEAT = "分组表";
    /** 分道表 */
    public static final String NAME_LANE = "分道表";
    /** 检录表 */
    public static final String NAME_CALLING = "检录表";

    // ------------------------------------------------------------------
    // 三、列名
    // ------------------------------------------------------------------

    /** 日期 */
    public static final String COL_DATE = "日期";
    /** 时段（上午/下午/晚上） */
    public static final String COL_PERIOD = "时段";
    /** 时间（HH:mm-HH:mm） */
    public static final String COL_TIME = "时间";
    /** 场地 */
    public static final String COL_VENUE = "场地";
    /** 项目 */
    public static final String COL_EVENT = "项目";
    /** 轮次 */
    public static final String COL_ROUND = "轮次";
    /** 组数 */
    public static final String COL_HEAT_COUNT = "组数";
    /** 时长（分钟） */
    public static final String COL_DURATION = "时长(分钟)";
    /** 备注 */
    public static final String COL_REMARK = "备注";
    /** 发布状态（草稿标记列：publishOnly=false 时草稿显示「草稿」） */
    public static final String COL_PUBLISH = "状态";
    /** 组次 */
    public static final String COL_HEAT_NO = "组次";
    /** 道次/顺序（径赛为道次，田赛为出场顺序） */
    public static final String COL_LANE_ORDER = "道次/顺序";
    /** 道次 */
    public static final String COL_LANE = "道次";
    /** 号码 */
    public static final String COL_BIB = "号码";
    /** 姓名 */
    public static final String COL_ATHLETE = "姓名";
    /** 单位 */
    public static final String COL_UNIT = "单位";
    /** 种子成绩 */
    public static final String COL_SEED = "种子成绩";
    /** 检录时间 */
    public static final String COL_CALL_TIME = "检录时间";
    /** 检录处 */
    public static final String COL_CALL_PLACE = "检录处";

    // ------------------------------------------------------------------
    // 四、表头（列序）
    // ------------------------------------------------------------------

    /** 总日程表表头：日期 / 时段 / 时间 / 场地 / 项目 / 轮次 / 组数 / 时长 / 备注 / 状态 */
    public static final List<String> HEADERS_SCHEDULE = List.of(
            COL_DATE, COL_PERIOD, COL_TIME, COL_VENUE, COL_EVENT,
            COL_ROUND, COL_HEAT_COUNT, COL_DURATION, COL_REMARK, COL_PUBLISH);

    /** 分组表表头：项目 / 轮次 / 组次 / 道次或顺序 / 号码 / 姓名 / 单位 / 种子成绩 / 备注 */
    public static final List<String> HEADERS_HEAT = List.of(
            COL_EVENT, COL_ROUND, COL_HEAT_NO, COL_LANE_ORDER, COL_BIB,
            COL_ATHLETE, COL_UNIT, COL_SEED, COL_REMARK);

    /** 分道表表头：项目 / 轮次 / 组次 / 道次 / 号码 / 姓名 / 单位 / 种子成绩 / 备注 */
    public static final List<String> HEADERS_LANE = List.of(
            COL_EVENT, COL_ROUND, COL_HEAT_NO, COL_LANE, COL_BIB,
            COL_ATHLETE, COL_UNIT, COL_SEED, COL_REMARK);

    /** 检录表表头：检录时间 / 检录处 / 项目 / 轮次 / 组次 / 号码 / 姓名 / 单位 / 道次 / 备注 */
    public static final List<String> HEADERS_CALLING = List.of(
            COL_CALL_TIME, COL_CALL_PLACE, COL_EVENT, COL_ROUND, COL_HEAT_NO,
            COL_BIB, COL_ATHLETE, COL_UNIT, COL_LANE, COL_REMARK);

    // ------------------------------------------------------------------
    // 五、Excel 中 4 个 Sheet 的下标与名称（导出顺序固定）
    // ------------------------------------------------------------------

    /** 总日程表 Sheet 下标 */
    public static final int INDEX_SCHEDULE = 0;
    /** 分组表 Sheet 下标 */
    public static final int INDEX_HEAT = 1;
    /** 分道表 Sheet 下标 */
    public static final int INDEX_LANE = 2;
    /** 检录表 Sheet 下标 */
    public static final int INDEX_CALLING = 3;
}
