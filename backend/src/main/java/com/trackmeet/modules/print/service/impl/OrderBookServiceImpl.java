package com.trackmeet.modules.print.service.impl;

import com.trackmeet.modules.print.vo.RowConvertible;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.print.constant.PrintSheetKeys;
import com.trackmeet.modules.print.dto.HeatMemberProjection;
import com.trackmeet.modules.print.dto.HeatProjection;
import com.trackmeet.modules.print.dto.LiteProjection;
import com.trackmeet.modules.print.dto.MeetProjection;
import com.trackmeet.modules.print.dto.OrderBookQuery;
import com.trackmeet.modules.print.dto.PrintQuery;
import com.trackmeet.modules.print.dto.ScheduleProjection;
import com.trackmeet.modules.print.excel.CallingSheetExcel;
import com.trackmeet.modules.print.excel.HeatSheetExcel;
import com.trackmeet.modules.print.excel.LaneSheetExcel;
import com.trackmeet.modules.print.excel.ScheduleSheetExcel;
import com.trackmeet.modules.print.mapper.PrintMapper;
import com.trackmeet.modules.print.service.OrderBookService;
import com.trackmeet.modules.print.support.PrintText;
import com.trackmeet.modules.print.vo.CallingSheetRowVO;
import com.trackmeet.modules.print.vo.HeatSheetRowVO;
import com.trackmeet.modules.print.vo.LaneSheetRowVO;
import com.trackmeet.modules.print.vo.MeetInfoVO;
import com.trackmeet.modules.print.vo.OrderBookVO;
import com.trackmeet.modules.print.vo.ScheduleSheetRowVO;
import com.trackmeet.modules.print.vo.SheetBundleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 秩序册/分组表/分道表/检录表输出服务实现。
 * <p>
 * 装配策略（一次装库、内存组装，四张表口径一致、无 N+1）：
 * <ol>
 *     <li>查 {@code meet} 取届次信息；</li>
 *     <li>查 {@code schedule}（联单元/项目/轮次/场地/组别）取日程主数据；</li>
 *     <li>按 {@code schedule_id} 批量查 {@code heat}；</li>
 *     <li>按 {@code heat_id} 批量查 {@code heat_member}（联 {@code lane} / {@code field_order} / {@code athlete} / {@code unit}）；</li>
 *     <li>一次查 {@code schedule_session} 的 {@code venue_ids_json}，用于按场地过滤；</li>
 *     <li>四张表全部由以上内存数据组装，不再触发查询。</li>
 * </ol>
 * 约定：单元格空值统一 {@link PrintText#EMPTY}（{@code -}）；日期 {@code yyyy-MM-dd}、时刻 {@code HH:mm}；
 * JSON 列解析失败降级为空列表，不影响出表。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookServiceImpl implements OrderBookService {

    /** 日期展示格式 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 文件名中的日期格式 */
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 时刻展示格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 检录时间相对计划开始时间的提前量（分钟） */
    private static final int CALL_AHEAD_MINUTES = 20;

    /** 场地/分组均未配置道数时的兜底道数 */
    private static final int DEFAULT_LANE_COUNT = 8;

    /** 径赛/接力项目类型（需要输出分道表） */
    private static final Set<String> TRACK_EVENT_TYPES = Set.of("TRACK", "RELAY");

    /** 单批查库条数（避免 IN 列表过长） */
    private static final int BATCH_SIZE = 500;

    /** Excel Sheet 名最大长度（Excel 限制 31 字符） */
    private static final int EXCEL_SHEET_NAME_LIMIT = 31;

    /** Excel Sheet 名非法字符（{@code \ / * ? : [ ]}，逐字符匹配，不用正则模式串） */
    private static final char[] EXCEL_SHEET_NAME_ILLEGAL_CHARS = {'\\', '/', '*', '?', ':', '[', ']'};

    /** Windows 文件名非法字符（在 Sheet 名非法字符上追加 {@code " < > |} 与控制字符） */
    private static final char[] FILE_NAME_ILLEGAL_CHARS = {'\\', '/', '*', '?', ':', '[', ']', '"', '<', '>', '|'};

    /** 参赛状态名称 */
    private static final Map<String, String> MEMBER_STATUS_NAMES = Map.of(
            "NOT_CALLED", "未检录",
            "CALLED", "已检录",
            "PRESENT", "到检",
            "ABSENT", "未到",
            "WITHDRAW", "弃权",
            "DNS", "未出发");

    private final PrintMapper printMapper;

    private final ObjectMapper objectMapper;

    @Override
    public OrderBookVO getOrderBook(OrderBookQuery query) {
        PrintAssembly assembly = assemble(query);
        OrderBookVO orderBook = new OrderBookVO();
        orderBook.setMeetInfo(assembly.meetInfo);
        orderBook.setPublishOnly(assembly.publishOnly);
        orderBook.setScheduleRows(buildScheduleRows(assembly));
        orderBook.setHeatRows(buildHeatRows(assembly));
        orderBook.setLaneRows(buildLaneRows(assembly));
        orderBook.setCallingRows(buildCallingRows(assembly));
        orderBook.setScheduleSheets(List.of(buildScheduleSheet(assembly)));
        orderBook.setHeatSheets(buildHeatSheets(assembly));
        orderBook.setLaneSheets(buildLaneSheets(assembly));
        orderBook.setCallingSheets(List.of(buildCallingSheet(assembly, orderBook.getCallingRows())));
        return orderBook;
    }

    @Override
    public List<SheetBundleVO> listScheduleSheets(PrintQuery query) {
        return List.of(buildScheduleSheet(assemble(query)));
    }

    @Override
    public List<SheetBundleVO> listHeatSheets(PrintQuery query) {
        return buildHeatSheets(assemble(query));
    }

    @Override
    public List<SheetBundleVO> listLaneSheets(PrintQuery query) {
        return buildLaneSheets(assemble(query));
    }

    @Override
    public List<SheetBundleVO> listCallingSheets(PrintQuery query) {
        PrintAssembly assembly = assemble(query);
        return List.of(buildCallingSheet(assembly, buildCallingRows(assembly)));
    }

    @Override
    public void writeOrderBookExcel(OrderBookVO orderBook, OutputStream outputStream) {
        if (orderBook == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "秩序册数据不能为空");
        }
        if (outputStream == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "输出流不能为空");
        }
        List<ScheduleSheetExcel> scheduleRows = new ArrayList<>();
        for (ScheduleSheetRowVO row : nullSafe(orderBook.getScheduleRows())) {
            scheduleRows.add(ScheduleSheetExcel.from(row));
        }
        List<HeatSheetExcel> heatRows = new ArrayList<>();
        for (HeatSheetRowVO row : nullSafe(orderBook.getHeatRows())) {
            heatRows.add(HeatSheetExcel.from(row));
        }
        List<LaneSheetExcel> laneRows = new ArrayList<>();
        for (LaneSheetRowVO row : nullSafe(orderBook.getLaneRows())) {
            laneRows.add(LaneSheetExcel.from(row));
        }
        List<CallingSheetExcel> callingRows = new ArrayList<>();
        for (CallingSheetRowVO row : nullSafe(orderBook.getCallingRows())) {
            callingRows.add(CallingSheetExcel.from(row));
        }
        // 4 个 Sheet 固定顺序：总日程表 / 分组表 / 分道表 / 检录表
        try (ExcelWriter writer = EasyExcel.write(outputStream).build()) {
            writeSheet(writer, PrintSheetKeys.INDEX_SCHEDULE, PrintSheetKeys.NAME_SCHEDULE,
                    ScheduleSheetExcel.class, scheduleRows);
            writeSheet(writer, PrintSheetKeys.INDEX_HEAT, PrintSheetKeys.NAME_HEAT,
                    HeatSheetExcel.class, heatRows);
            writeSheet(writer, PrintSheetKeys.INDEX_LANE, PrintSheetKeys.NAME_LANE,
                    LaneSheetExcel.class, laneRows);
            writeSheet(writer, PrintSheetKeys.INDEX_CALLING, PrintSheetKeys.NAME_CALLING,
                    CallingSheetExcel.class, callingRows);
        }
    }

    @Override
    public String buildOrderBookFileName(OrderBookVO orderBook) {
        MeetInfoVO meetInfo = orderBook == null ? null : orderBook.getMeetInfo();
        String subject = PrintText.firstNonBlank(
                meetInfo == null ? null : meetInfo.getShortName(),
                meetInfo == null ? null : meetInfo.getEdition(),
                meetInfo == null ? null : meetInfo.getMeetName());
        String date = FILE_DATE_FORMATTER.format(LocalDate.now());
        if (meetInfo != null && meetInfo.getStartDate() != null) {
            try {
                date = FILE_DATE_FORMATTER.format(LocalDate.parse(meetInfo.getStartDate(), DATE_FORMATTER));
            } catch (Exception ex) {
                log.debug("届次开始日期解析失败，文件名改用当天：{}", meetInfo.getStartDate());
            }
        }
        // 只清洗届次主体（可能含 / \ : 等非法字符），日期与扩展名保持原样
        String safeSubject = sanitizeFileName(subject);
        StringBuilder builder = new StringBuilder("秩序册");
        if (safeSubject != null) {
            builder.append('_').append(safeSubject);
        }
        builder.append('_').append(date);
        return builder + ".xlsx";
    }

    // ------------------------------------------------------------------
    // 一、数据装配
    // ------------------------------------------------------------------

    /**
     * 一次装库：届次 + 日程 + 分组 + 组内人员 + 单元可用场地（全部走内存 Map，后续不再查库）。
     *
     * @param query 查询条件（可为 null）
     * @return 装配结果
     */
    private PrintAssembly assemble(PrintQuery query) {
        PrintQuery effective = normalize(query);
        boolean publishOnly = effective.isPublishOnlyEnabled();
        Long meetId = resolveMeetId(effective);
        effective.setMeetId(meetId);
        effective.setPublishOnly(publishOnly);

        PrintAssembly assembly = new PrintAssembly();
        assembly.publishOnly = publishOnly;

        MeetProjection meet = printMapper.selectMeetInfo(meetId);
        if (meet == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运动会不存在或已删除");
        }
        assembly.meetInfo = toMeetInfo(meet);

        assembly.schedules = filterByVenue(printMapper.selectSchedules(effective, meetId, publishOnly),
                loadSessionVenueIds(meetId, effective), effective.getVenueId());
        if (assembly.schedules.isEmpty()) {
            return assembly;
        }

        List<Long> scheduleIds = distinctScheduleIds(assembly.schedules);
        List<HeatProjection> heats = new ArrayList<>();
        for (int start = 0; start < scheduleIds.size(); start += BATCH_SIZE) {
            List<Long> batch = scheduleIds.subList(start, Math.min(start + BATCH_SIZE, scheduleIds.size()));
            heats.addAll(printMapper.selectHeats(meetId, batch));
        }
        List<Long> heatIds = new ArrayList<>();
        assembly.heatsBySchedule = new LinkedHashMap<>();
        for (HeatProjection heat : heats) {
            assembly.heatsBySchedule.computeIfAbsent(heat.getScheduleId(), key -> new ArrayList<>()).add(heat);
            if (heat.getHeatId() != null) {
                heatIds.add(heat.getHeatId());
            }
        }
        assembly.membersByHeat = new LinkedHashMap<>();
        for (int start = 0; start < heatIds.size(); start += BATCH_SIZE) {
            List<Long> batch = heatIds.subList(start, Math.min(start + BATCH_SIZE, heatIds.size()));
            for (HeatMemberProjection member : printMapper.selectHeatMembers(meetId, batch, effective.getUnitId())) {
                assembly.membersByHeat.computeIfAbsent(member.getHeatId(), key -> new ArrayList<>()).add(member);
            }
        }
        return assembly;
    }

    /**
     * 查询条件规范化：null 与全空 {@code meetId} 统一处理，保证 Mapper 的 {@code <if>} 判断安全。
     *
     * @param query 原始条件
     * @return 规范化后的副本（不修改调用方入参）
     */
    private PrintQuery normalize(PrintQuery query) {
        PrintQuery source = query == null ? new PrintQuery() : query;
        PrintQuery copy = new PrintQuery();
        copy.setMeetId(source.getMeetId());
        copy.setSessionId(source.getSessionId());
        copy.setGroupId(source.getGroupId());
        copy.setEventId(source.getEventId());
        copy.setUnitId(source.getUnitId());
        copy.setVenueId(source.getVenueId());
        copy.setFormat(source.getFormat());
        copy.setPublishOnly(source.getPublishOnly());
        return copy;
    }

    /**
     * 解析并校验届次：参数 {@code meetId} 与 {@code MeetContext} 同时存在且不一致时按越权处理。
     *
     * @param query 规范化后的条件
     * @return 生效的届次 ID
     */
    private Long resolveMeetId(PrintQuery query) {
        Long requestMeetId = query.getMeetId();
        Long contextMeetId = MeetContext.get();
        if (requestMeetId != null && contextMeetId != null && !requestMeetId.equals(contextMeetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "请求届次与当前届次上下文不一致，禁止跨届次导出");
        }
        Long meetId = requestMeetId != null ? requestMeetId : contextMeetId;
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少届次参数 meetId（或请求头 X-Meet-Id）");
        }
        return meetId;
    }

    /**
     * 批量装入单元的可用场地 ID（小参照表，每请求一次）。
     *
     * @param meetId 届次 ID
     * @param query  条件（用于收敛单元范围）
     * @return 单元 ID → 可用场地 ID 集合
     */
    private Map<Long, Set<Long>> loadSessionVenueIds(Long meetId, PrintQuery query) {
        List<Long> sessionIds = new ArrayList<>();
        if (query.getSessionId() != null) {
            sessionIds.add(query.getSessionId());
        }
        List<LiteProjection> rows;
        try {
            rows = printMapper.selectSessionVenueIds(meetId, sessionIds.isEmpty() ? null : sessionIds);
        } catch (Exception ex) {
            // JSON 列不可读等异常时降级：不做「单元可用场地」过滤，不影响出表
            log.warn("读取单元可用场地失败，已降级为不过滤：{}", ex.getMessage());
            return new HashMap<>();
        }
        Map<Long, Set<Long>> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (LiteProjection row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            result.put(row.getId(), parseIdArray(row.getText()));
        }
        return result;
    }

    /**
     * 按场地过滤：日程自身场地命中，或日程未指定场地但其所属单元的可用场地列表命中。
     *
     * @param schedules       日程列表
     * @param sessionVenueIds 单元可用场地
     * @param venueId         场地 ID（可为空）
     * @return 过滤后的日程列表
     */
    private List<ScheduleProjection> filterByVenue(List<ScheduleProjection> schedules,
                                                   Map<Long, Set<Long>> sessionVenueIds,
                                                   Long venueId) {
        if (venueId == null) {
            return schedules;
        }
        List<ScheduleProjection> filtered = new ArrayList<>();
        for (ScheduleProjection schedule : schedules) {
            if (venueId.equals(schedule.getVenueId())
                    || sessionVenueIds.getOrDefault(schedule.getSessionId(), Set.of()).contains(venueId)) {
                filtered.add(schedule);
            }
        }
        return filtered;
    }

    /**
     * 解析 JSON 数组为 ID 集合；非数组、非法 JSON、元素非数字时降级为空集合。
     *
     * @param json JSON 文本（如 {@code [1, 2, 3]}）
     * @return ID 集合
     */
    private Set<Long> parseIdArray(String json) {
        Set<Long> ids = new LinkedHashSet<>();
        if (json == null || json.isBlank()) {
            return ids;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    if (item != null && item.canConvertToLong()) {
                        ids.add(item.asLong());
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("JSON 数组解析失败，已降级为空：{}", ex.getMessage());
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // 二、总日程表
    // ------------------------------------------------------------------

    private SheetBundleVO buildScheduleSheet(PrintAssembly assembly) {
        return SheetBundleVO.of(PrintSheetKeys.KEY_SCHEDULE, sheetTitle(assembly, PrintSheetKeys.NAME_SCHEDULE),
                PrintSheetKeys.HEADERS_SCHEDULE, toRows(buildScheduleRows(assembly)));
    }

    private List<ScheduleSheetRowVO> buildScheduleRows(PrintAssembly assembly) {
        List<ScheduleSheetRowVO> rows = new ArrayList<>();
        for (ScheduleProjection schedule : assembly.schedules) {
            ScheduleSheetRowVO row = new ScheduleSheetRowVO();
            row.setSessionDate(ScheduleSheetRowVO.buildSessionDate(schedule.getSessionDate(), schedule.getDayNo()));
            row.setDayNo(schedule.getDayNo());
            row.setPeriodName(periodName(schedule.getPeriod()));
            row.setStartTime(formatTime(schedule.getPlanStartTime()));
            row.setEndTime(formatTime(schedule.getPlanEndTime()));
            row.setTimeRange(timeRange(schedule.getPlanStartTime(), schedule.getPlanEndTime()));
            row.setVenueName(PrintText.firstNonBlank(schedule.getVenueName(), schedule.getSessionVenueName()));
            row.setEventName(eventDisplayName(schedule));
            row.setRoundName(roundName(schedule));
            row.setGroupName(schedule.getGroupName());
            row.setHeatCount(schedule.getHeatCount());
            row.setDurationMin(schedule.getDurationMin() != null
                    ? schedule.getDurationMin() : schedule.getEventDurationMin());
            row.setRemark(PrintText.join(schedule.getScheduleRemark(), conflictText(schedule)));
            row.setPublishStatusName(publishStatusName(schedule.getPublishStatus()));
            row.setPublishStatus(schedule.getPublishStatus());
            row.setSessionName(schedule.getSessionName());
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 三、分组表（按 项目 × 轮次 拆表）
    // ------------------------------------------------------------------

    private List<SheetBundleVO> buildHeatSheets(PrintAssembly assembly) {
        List<SheetBundleVO> sheets = new ArrayList<>();
        for (List<ScheduleProjection> group : groupByEventRound(assembly.schedules)) {
            List<HeatSheetRowVO> rows = buildHeatRows(assembly, group);
            ScheduleProjection head = group.get(0);
            String sheetName = PrintText.join(eventDisplayName(head), roundName(head), PrintSheetKeys.NAME_HEAT)
                    + draftSuffix(assembly);
            sheets.add(SheetBundleVO.of(heatSheetKey(head), sheetName,
                    PrintSheetKeys.HEADERS_HEAT, toRows(rows)));
        }
        return sheets;
    }

    private List<HeatSheetRowVO> buildHeatRows(PrintAssembly assembly) {
        List<HeatSheetRowVO> rows = new ArrayList<>();
        for (List<ScheduleProjection> group : groupByEventRound(assembly.schedules)) {
            rows.addAll(buildHeatRows(assembly, group));
        }
        return rows;
    }

    private List<HeatSheetRowVO> buildHeatRows(PrintAssembly assembly, List<ScheduleProjection> group) {
        List<HeatSheetRowVO> rows = new ArrayList<>();
        for (ScheduleProjection schedule : group) {
            for (HeatProjection heat : heatsOf(assembly, schedule)) {
                List<HeatMemberProjection> members = sortedMembers(assembly, heat, isTrack(schedule));
                if (members.isEmpty()) {
                    // 空组不出表，避免只有组头没有参赛人员的空白页
                    continue;
                }
                for (HeatMemberProjection member : members) {
                    HeatSheetRowVO row = new HeatSheetRowVO();
                    row.setEventName(eventDisplayName(schedule));
                    row.setRoundName(roundName(schedule));
                    row.setHeatNoText(heatNoText(heat));
                    row.setHeatNo(heat.getHeatNo());
                    row.setLaneNo(laneNoOf(member));
                    row.setOrderNo(orderNoOf(member));
                    row.setLaneOrderText(isTrack(schedule)
                            ? laneText(row.getLaneNo()) : orderText(row.getOrderNo()));
                    row.setBibNo(member.getBibNo());
                    row.setAthleteName(member.getAthleteName());
                    row.setUnitName(PrintText.firstNonBlank(member.getUnitShortName(), member.getUnitName()));
                    row.setSeedResult(seedResultOf(member));
                    row.setMemberStatusName(memberStatusName(member.getMemberStatus()));
                    row.setRemark(memberRemark(heat, member));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 四、分道表（径赛/接力，按 分组 拆表并补空道）
    // ------------------------------------------------------------------

    private List<SheetBundleVO> buildLaneSheets(PrintAssembly assembly) {
        List<SheetBundleVO> sheets = new ArrayList<>();
        for (List<ScheduleProjection> group : groupByEventRound(assembly.schedules)) {
            for (ScheduleProjection schedule : group) {
                if (!isTrack(schedule)) {
                    continue;
                }
                for (HeatProjection heat : heatsOf(assembly, schedule)) {
                    List<LaneSheetRowVO> rows = laneRowsOf(assembly, schedule, heat);
                    String sheetName = PrintText.join(eventDisplayName(schedule), roundName(schedule),
                            heatNoText(heat), PrintSheetKeys.NAME_LANE) + draftSuffix(assembly);
                    sheets.add(SheetBundleVO.of(laneSheetKey(heat), sheetName,
                            PrintSheetKeys.HEADERS_LANE, toRows(rows)));
                }
            }
        }
        return sheets;
    }

    private List<LaneSheetRowVO> buildLaneRows(PrintAssembly assembly) {
        List<LaneSheetRowVO> rows = new ArrayList<>();
        for (List<ScheduleProjection> group : groupByEventRound(assembly.schedules)) {
            for (ScheduleProjection schedule : group) {
                if (!isTrack(schedule)) {
                    continue;
                }
                for (HeatProjection heat : heatsOf(assembly, schedule)) {
                    rows.addAll(laneRowsOf(assembly, schedule, heat));
                }
            }
        }
        return rows;
    }

    private List<LaneSheetRowVO> laneRowsOf(PrintAssembly assembly, ScheduleProjection schedule,
                                            HeatProjection heat) {
        int laneCount = laneCount(schedule, heat);
        Map<Integer, HeatMemberProjection> byLane = new HashMap<>();
        int fallbackCursor = 1;
        for (HeatMemberProjection member : sortedMembers(assembly, heat, true)) {
            Integer lane = laneNoOf(member);
            if (lane != null && lane >= 1 && lane <= laneCount && !byLane.containsKey(lane)) {
                byLane.put(lane, member);
                continue;
            }
            // 未分道或道次越界：顺延到第一个空道，保证人员不丢失（剩余人员在末尾顺延展示）
            while (fallbackCursor <= laneCount && byLane.containsKey(fallbackCursor)) {
                fallbackCursor++;
            }
            if (fallbackCursor <= laneCount) {
                byLane.put(fallbackCursor, member);
                fallbackCursor++;
            }
        }
        List<LaneSheetRowVO> rows = new ArrayList<>();
        for (int lane = 1; lane <= laneCount; lane++) {
            HeatMemberProjection member = byLane.get(lane);
            LaneSheetRowVO row = new LaneSheetRowVO();
            row.setEventName(eventDisplayName(schedule));
            row.setRoundName(roundName(schedule));
            row.setHeatNoText(heatNoText(heat));
            row.setHeatNo(heat.getHeatNo());
            row.setLaneNo(lane);
            row.setLaneCount(laneCount);
            row.setEmptyLane(member == null);
            if (member == null) {
                row.setBibNo(PrintText.EMPTY);
                row.setAthleteName(PrintText.EMPTY);
                row.setUnitName(PrintText.EMPTY);
                row.setSeedResult(PrintText.EMPTY);
                row.setMemberStatusName(PrintText.EMPTY);
            } else {
                row.setBibNo(member.getBibNo());
                row.setAthleteName(member.getAthleteName());
                row.setUnitName(PrintText.firstNonBlank(member.getUnitShortName(), member.getUnitName()));
                row.setSeedResult(seedResultOf(member));
                row.setMemberStatusName(memberStatusName(member.getMemberStatus()));
            }
            row.setRemark(member == null ? PrintText.EMPTY : memberRemark(heat, member));
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 五、检录表（单表，按检录时间排序）
    // ------------------------------------------------------------------

    private SheetBundleVO buildCallingSheet(PrintAssembly assembly, List<CallingSheetRowVO> rows) {
        return SheetBundleVO.of(PrintSheetKeys.KEY_CALLING, sheetTitle(assembly, PrintSheetKeys.NAME_CALLING),
                PrintSheetKeys.HEADERS_CALLING, toRows(rows));
    }

    private List<CallingSheetRowVO> buildCallingRows(PrintAssembly assembly) {
        List<CallingEntry> entries = new ArrayList<>();
        for (List<ScheduleProjection> group : groupByEventRound(assembly.schedules)) {
            for (ScheduleProjection schedule : group) {
                for (HeatProjection heat : heatsOf(assembly, schedule)) {
                    LocalDateTime callTime = resolveCallTime(heat, schedule);
                    boolean estimated = heat.getCallTime() == null && callTime != null;
                    for (HeatMemberProjection member : sortedMembers(assembly, heat, isTrack(schedule))) {
                        CallingEntry entry = new CallingEntry();
                        entry.callTime = callTime;
                        entry.estimated = estimated;
                        entry.heatsNo = heat.getHeatNo();
                        entry.laneNo = laneNoOf(member);
                        entry.orderNo = orderNoOf(member);
                        entry.memberId = member.getHeatMemberId();
                        entry.schedule = schedule;
                        entry.heat = heat;
                        entry.member = member;
                        entries.add(entry);
                    }
                }
            }
        }
        entries.sort(Comparator
                .comparing((CallingEntry entry) -> entry.callTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.schedule.getSessionDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.schedule.getOrderNo(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.heatsNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.laneNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.orderNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(entry -> entry.memberId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<CallingSheetRowVO> rows = new ArrayList<>();
        for (CallingEntry entry : entries) {
            ScheduleProjection schedule = entry.schedule;
            HeatMemberProjection member = entry.member;
            CallingSheetRowVO row = new CallingSheetRowVO();
            row.setCallTime(formatTime(entry.callTime));
            row.setCallPlace(entry.heat.getCallPlace());
            row.setEventName(eventDisplayName(schedule));
            row.setRoundName(roundName(schedule));
            row.setHeatNoText(heatNoText(entry.heat));
            row.setHeatNo(entry.heatsNo);
            row.setBibNo(member.getBibNo());
            row.setAthleteName(member.getAthleteName());
            row.setUnitName(PrintText.firstNonBlank(member.getUnitShortName(), member.getUnitName()));
            row.setLaneNo(entry.laneNo);
            row.setOrderNo(entry.orderNo);
            row.setLaneText(isTrack(schedule) ? laneText(entry.laneNo) : orderText(entry.orderNo));
            row.setCallTimeEstimated(entry.estimated);
            row.setRemark(PrintText.join(entry.estimated ? "检录时间推算" : null,
                    PrintText.firstNonBlank(entry.heat.getCallPlace()) == null ? "检录处待定" : null,
                    memberRemark(entry.heat, member)));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 检录时间：优先 {@code heat.call_time}，为空时用 {@code schedule.plan_start_time - 20 分钟}，
     * 再退化为本组开始时间 - 20 分钟。
     *
     * @param heat     分组
     * @param schedule 日程条目
     * @return 检录时间（无任何可用时间时为 null）
     */
    private LocalDateTime resolveCallTime(HeatProjection heat, ScheduleProjection schedule) {
        if (heat.getCallTime() != null) {
            return heat.getCallTime();
        }
        LocalDateTime base = schedule.getPlanStartTime() != null
                ? schedule.getPlanStartTime() : heat.getHeatStartTime();
        return base == null ? null : base.minusMinutes(CALL_AHEAD_MINUTES);
    }

    // ------------------------------------------------------------------
    // 六、公共组装工具
    // ------------------------------------------------------------------

    /**
     * 按「项目 × 轮次」拆表并保持日程排序（分表：分组表/分道表；合表：检录表口径一致）。
     *
     * @param schedules 已排序日程
     * @return 有序分组列表
     */
    private List<List<ScheduleProjection>> groupByEventRound(List<ScheduleProjection> schedules) {
        Map<String, List<ScheduleProjection>> grouped = new LinkedHashMap<>();
        for (ScheduleProjection schedule : schedules) {
            grouped.computeIfAbsent(eventRoundKey(schedule), key -> new ArrayList<>()).add(schedule);
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 项目 × 轮次 分组键：项目 ID + 轮次 ID（任一缺失时用 0 占位，保证同项目同轮次仍聚合在一张表）。
     */
    private String eventRoundKey(ScheduleProjection schedule) {
        return (schedule.getEventId() == null ? 0L : schedule.getEventId())
                + ":" + (schedule.getRoundId() == null ? 0L : schedule.getRoundId());
    }

    /** 分组表 Sheet 键：{@code heat:{eventId}:{roundId}} */
    private String heatSheetKey(ScheduleProjection schedule) {
        return PrintSheetKeys.KEY_HEAT + ":" + (schedule.getEventId() == null ? 0L : schedule.getEventId())
                + ":" + (schedule.getRoundId() == null ? 0L : schedule.getRoundId());
    }

    /** 分道表 Sheet 键：{@code lane:{heatId}} */
    private String laneSheetKey(HeatProjection heat) {
        return PrintSheetKeys.KEY_LANE + ":" + (heat.getHeatId() == null ? 0L : heat.getHeatId());
    }

    /**
     * 表名：单表（总日程表/检录表）为「届次简称 表名」，非 publishOnly 时追加「（草稿）」。
     */
    private String sheetTitle(PrintAssembly assembly, String baseName) {
        String subject = assembly.meetInfo == null ? null : PrintText.firstNonBlank(
                assembly.meetInfo.getShortName(), assembly.meetInfo.getEdition());
        return PrintText.join(subject, baseName) + draftSuffix(assembly);
    }

    /** 草稿标记后缀：仅当输出包含草稿（publishOnly=false）时出现 */
    private String draftSuffix(PrintAssembly assembly) {
        return assembly.publishOnly ? "" : "（草稿）";
    }

    /** 项目展示名：{@code 组别 项目名}（组别缺失时仅项目名） */
    private String eventDisplayName(ScheduleProjection schedule) {
        String eventName = PrintText.text(schedule.getEventName());
        String groupName = PrintText.firstNonBlank(schedule.getGroupName());
        if (groupName == null || PrintText.EMPTY.equals(eventName) || eventName.startsWith(groupName)) {
            return eventName;
        }
        return groupName + " " + eventName;
    }

    private String roundName(ScheduleProjection schedule) {
        return PrintText.firstNonBlank(schedule.getRoundName(), schedule.getRoundCode());
    }

    /** 组次文本：组名优先，其次「第N组」，均无输出 - */
    private String heatNoText(HeatProjection heat) {
        String heatName = PrintText.firstNonBlank(heat.getHeatName());
        if (heatName != null) {
            return heatName;
        }
        return heat.getHeatNo() == null ? PrintText.EMPTY : "第" + heat.getHeatNo() + "组";
    }

    /** 组内人员：径赛按道次升序（缺道次排后），田赛按出场顺序升序 */
    private List<HeatMemberProjection> sortedMembers(PrintAssembly assembly, HeatProjection heat, boolean track) {
        List<HeatMemberProjection> members = new ArrayList<>(
                assembly.membersByHeat.getOrDefault(heat.getHeatId(), List.of()));
        Comparator<HeatMemberProjection> byLane = Comparator
                .comparing((HeatMemberProjection member) -> laneNoOf(member),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(member -> orderNoOf(member), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(member -> heat.getHeatNo(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(member -> member.getHeatMemberId(), Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<HeatMemberProjection> byOrder = Comparator
                .comparing((HeatMemberProjection member) -> orderNoOf(member),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(member -> laneNoOf(member), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(member -> member.getHeatMemberId(), Comparator.nullsLast(Comparator.naturalOrder()));
        members.sort(track ? byLane : byOrder);
        return members;
    }

    /** 道次：{@code lane.lane_no} 优先，冗余列 {@code heat_member.lane_no} 兜底 */
    private Integer laneNoOf(HeatMemberProjection member) {
        return member.getLaneNo() != null ? member.getLaneNo() : member.getLaneNoRaw();
    }

    /** 出场顺序：{@code field_order.order_no} 优先，{@code heat_member.order_no} 兜底 */
    private Integer orderNoOf(HeatMemberProjection member) {
        return member.getFieldOrderNo() != null ? member.getFieldOrderNo() : member.getOrderNo();
    }

    /** 道次文本：第N道 / - */
    private String laneText(Integer laneNo) {
        return PrintText.isPositive(laneNo) ? "第" + laneNo + "道" : PrintText.EMPTY;
    }

    /** 顺序文本：第N位 / - */
    private String orderText(Integer orderNo) {
        return PrintText.isPositive(orderNo) ? "第" + orderNo + "位" : PrintText.EMPTY;
    }

    /** 分道表道数：{@code heat.lane_count} → 场地道数（日程场地 → 单元主场地）→ 8 道 */
    private int laneCount(ScheduleProjection schedule, HeatProjection heat) {
        if (PrintText.isPositive(heat.getLaneCount())) {
            return heat.getLaneCount();
        }
        if (PrintText.isPositive(schedule.getVenueLaneCount())) {
            return schedule.getVenueLaneCount();
        }
        if (PrintText.isPositive(schedule.getSessionVenueLaneCount())) {
            return schedule.getSessionVenueLaneCount();
        }
        return DEFAULT_LANE_COUNT;
    }

    /** 种子成绩：原文优先，其次数值 + 单位，田赛取 {@code field_order} 兜底 */
    private String seedResultOf(HeatMemberProjection member) {
        if (member == null) {
            return PrintText.EMPTY;
        }
        String raw = PrintText.firstNonBlank(member.getSeedResultRaw(), member.getBestSeedRaw());
        if (raw != null) {
            return raw;
        }
        Number value = member.getSeedResult() != null ? member.getSeedResult() : member.getBestSeed();
        if (value == null) {
            return PrintText.EMPTY;
        }
        return PrintText.join(PrintText.number(value),
                PrintText.firstNonBlank(member.getSeedResultUnit(), member.getBestSeedUnit()));
    }

    private String memberStatusName(String status) {
        if (status == null || status.isBlank()) {
            return PrintText.EMPTY;
        }
        return MEMBER_STATUS_NAMES.getOrDefault(status.trim(), status.trim());
    }

    /** 备注：分组备注 + 弃权标记 + 种子标记；草稿标记由表名与状态列体现 */
    private String memberRemark(HeatProjection heat, HeatMemberProjection member) {
        return PrintText.join(
                heat == null ? null : heat.getHeatRemark(),
                member.getScratchFlag() != null && member.getScratchFlag() == 1 ? "弃权" : null,
                member.getSeedFlag() != null && member.getSeedFlag() == 1 ? "种子" : null);
    }

    private String publishStatusName(String publishStatus) {
        if (publishStatus == null || publishStatus.isBlank()) {
            return PrintText.EMPTY;
        }
        return "PUBLISHED".equalsIgnoreCase(publishStatus.trim()) ? "已发布" : "草稿";
    }

    private String conflictText(ScheduleProjection schedule) {
        return schedule.getConflictFlag() != null && schedule.getConflictFlag() == 1 ? "存在冲突" : null;
    }

    private String periodName(String period) {
        if (period == null || period.isBlank()) {
            return PrintText.EMPTY;
        }
        return switch (period.trim().toUpperCase()) {
            case "MORNING" -> "上午";
            case "AFTERNOON" -> "下午";
            case "EVENING" -> "晚上";
            default -> period.trim();
        };
    }

    private String timeRange(LocalDateTime start, LocalDateTime end) {
        String from = formatTime(start);
        String to = formatTime(end);
        if (PrintText.EMPTY.equals(from)) {
            return to;
        }
        return PrintText.EMPTY.equals(to) ? from : from + "-" + to;
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? PrintText.EMPTY : TIME_FORMATTER.format(value);
    }

    /**
     * 是否径赛/接力项目（输出分道表）。
     * <p>
     * {@code event.event_type} 为 {@code TRACK}/{@code RELAY} 时成立；类型缺失时用
     * {@code event.is_relay = 1} 兜底。
     */
    private boolean isTrack(ScheduleProjection schedule) {
        if (schedule.getEventType() != null && TRACK_EVENT_TYPES.contains(schedule.getEventType().trim().toUpperCase())) {
            return true;
        }
        return schedule.getRelayFlag() != null && schedule.getRelayFlag() == 1;
    }

    private List<HeatProjection> heatsOf(PrintAssembly assembly, ScheduleProjection schedule) {
        if (assembly.heatsBySchedule == null || schedule.getScheduleId() == null) {
            return List.of();
        }
        return assembly.heatsBySchedule.getOrDefault(schedule.getScheduleId(), List.of());
    }

    private List<Long> distinctScheduleIds(List<ScheduleProjection> schedules) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ScheduleProjection schedule : schedules) {
            if (schedule.getScheduleId() != null) {
                ids.add(schedule.getScheduleId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 行对象 → 字符串单元格：全部走 {@code toRow()} + {@link PrintText}，空值统一 {@code -}。
     *
     * @param rows 行对象列表
     * @return 字符串行列表
     */
    private List<List<String>> toRows(List<? extends RowConvertible> rows) {
        List<List<String>> result = new ArrayList<>();
        for (RowConvertible row : nullSafe(rows)) {
            if (row != null) {
                result.add(row.toRow());
            }
        }
        return result;
    }

    private MeetInfoVO toMeetInfo(MeetProjection meet) {
        MeetInfoVO vo = new MeetInfoVO();
        vo.setMeetId(meet.getMeetId());
        vo.setMeetName(meet.getMeetName());
        vo.setEdition(meet.getEdition());
        vo.setShortName(meet.getShortName());
        vo.setStartDate(meet.getStartDate() == null ? null : DATE_FORMATTER.format(meet.getStartDate()));
        vo.setEndDate(meet.getEndDate() == null ? null : DATE_FORMATTER.format(meet.getEndDate()));
        vo.setPlace(meet.getPlace());
        vo.setHost(meet.getHost());
        vo.setOrganizer(meet.getOrganizer());
        return vo;
    }

    /**
     * 写出单个 Sheet：同时指定 {@code sheetNo}（0 起）与 {@code sheetName}，保证 4 表顺序固定。
     */
    private void writeSheet(ExcelWriter writer, int sheetNo, String sheetName,
                            Class<?> headClass, List<?> rows) {
        WriteSheet sheet = EasyExcel.writerSheet(sheetNo, safeSheetName(sheetName))
                .head(headClass)
                .autoTrim(false)
                .build();
        List<?> data = rows == null ? List.of() : rows;
        writer.write(data, sheet);
    }

    /** Excel Sheet 名清洗：替换 {@code \ / * ? : [ ]} 为下划线，超长截断到 31 字符 */
    private String safeSheetName(String sheetName) {
        String value = sheetName == null ? PrintSheetKeys.NAME_SCHEDULE : sheetName;
        String cleaned = replaceIllegalChars(value, EXCEL_SHEET_NAME_ILLEGAL_CHARS).trim();
        if (cleaned.isEmpty()) {
            cleaned = PrintSheetKeys.NAME_SCHEDULE;
        }
        return cleaned.length() > EXCEL_SHEET_NAME_LIMIT ? cleaned.substring(0, EXCEL_SHEET_NAME_LIMIT) : cleaned;
    }

    /**
     * 过滤 Windows 非法字符（{@code \ / : * ? " &lt; &gt; | [ ]}）与控制字符，用于文件名主体。
     *
     * @param name 原始文本（可为 null）
     * @return 清洗后的文本，全部被过滤时返回 null（由调用方决定是否拼接）
     */
    private String sanitizeFileName(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = replaceIllegalChars(name, FILE_NAME_ILLEGAL_CHARS).trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 把指定字符逐个替换为下划线（并对控制字符做同样处理），避免正则转义带来的歧义。
     *
     * @param value       原文本
     * @param illegalChars 非法字符清单
     * @return 替换后的文本
     */
    private String replaceIllegalChars(String value, char[] illegalChars) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            boolean illegal = Character.isISOControl(current);
            if (!illegal) {
                for (char illegalChar : illegalChars) {
                    if (current == illegalChar) {
                        illegal = true;
                        break;
                    }
                }
            }
            builder.append(illegal ? '_' : current);
        }
        return builder.toString();
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    // ------------------------------------------------------------------
    // 七、内部数据结构
    // ------------------------------------------------------------------

    /**
     * 行对象统一转字符串行的约定（与 {@code vo/*SheetRowVO#toRow()} 对应）。
     */

    /**
     * 装配上下文：届次信息 + 全部内存索引 + 出表口径，避免 N+1。
     */
    private static final class PrintAssembly {

        private boolean publishOnly = true;
        private MeetInfoVO meetInfo;
        private List<ScheduleProjection> schedules = List.of();
        private Map<Long, List<HeatProjection>> heatsBySchedule = Map.of();
        private Map<Long, List<HeatMemberProjection>> membersByHeat = Map.of();
    }

    /**
     * 检录表中间结构：先排序再转行，保证与其它表口径一致。
     */
    private static final class CallingEntry {

        private LocalDateTime callTime;
        private boolean estimated;
        private Integer heatsNo;
        private Integer laneNo;
        private Integer orderNo;
        private Long memberId;
        private ScheduleProjection schedule;
        private HeatProjection heat;
        private HeatMemberProjection member;
    }
}
