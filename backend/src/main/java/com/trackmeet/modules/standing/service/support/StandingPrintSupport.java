package com.trackmeet.modules.standing.service.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.print.vo.SheetBundleVO;
import com.trackmeet.modules.standing.dto.StandingMeetRow;
import com.trackmeet.modules.standing.dto.StandingMedalRow;
import com.trackmeet.modules.standing.dto.StandingPointRow;
import com.trackmeet.modules.standing.dto.StandingRecordRow;
import com.trackmeet.modules.standing.dto.StandingResultRow;
import com.trackmeet.modules.standing.dto.StandingTeamScoreRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 榜单打印装配器：把榜单 / 成绩册数据装配为 {@link SheetBundleVO}，并写出多 Sheet Excel。
 * <p>
 * 与 {@code modules/print} 的秩序册输出共用同一契约：
 * <ul>
 *     <li>{@code sheetKey} 稳定唯一（{@code result:{eventId}:{roundId}} / {@code standings:team:{scoreType}} /
 *         {@code standings:medals} / {@code points}），供前端渲染与 Excel Sheet 排序；</li>
 *     <li>{@code headers} 为列名，{@code rows} 全部为字符串、空值统一输出 {@code -}；</li>
 *     <li>{@code format=xlsx} 时按同一批 {@code SheetBundleVO} 写出多 Sheet 文件流，
 *         保证「A4 打印」与「Excel 导出」列序完全一致。</li>
 * </ul>
 *
 * @author TrackMeet
 */
@Slf4j
@Component
public class StandingPrintSupport {

    /** Excel Sheet 名长度上限 */
    private static final int EXCEL_SHEET_NAME_LIMIT = 31;

    /** Excel Sheet 名非法字符 */
    private static final String ILLEGAL_SHEET_CHARS = "[\\\\/*?\\[\\]:]";

    /** 成绩册表头 */
    private static final List<String> RESULT_HEADERS = List.of("名次", "号码", "姓名", "单位", "成绩", "风速", "备注");

    /** 积分表表头 */
    private static final List<String> TEAM_HEADERS = List.of("名次", "单位", "组别", "名次分", "纪录加分", "接力分",
            "团体总分", "得分项目数", "得分人数");

    /** 奖牌榜表头 */
    private static final List<String> MEDAL_HEADERS = List.of("名次", "单位", "组别", "金牌", "银牌", "铜牌",
            "奖牌总数", "排序分");

    /** 积分明细表表头 */
    private static final List<String> POINT_HEADERS = List.of("项目", "轮次", "组别", "名次", "号码", "姓名", "单位",
            "名次分", "纪录加分", "倍数", "合计分", "名次分方案", "加分规则");

    /** 成绩册页脚时间格式 */
    private static final DateTimeFormatter FOOTER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 装配成绩册：按「项目 × 轮次」拆分为多张表，含纪录标记。
     *
     * @param rows     已审核成绩投影列表（按项目 / 轮次 / 名次排序）
     * @param records  现行纪录清单（用于标注所破纪录）
     * @param meet     届次信息（标题）
     * @param groupNames 组别 ID → 名称
     * @return 表结构列表
     */
    public List<SheetBundleVO> buildResultSheets(List<StandingResultRow> rows, List<StandingRecordRow> records,
                                                 StandingMeetRow meet, Map<Long, String> groupNames) {
        List<SheetBundleVO> bundles = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return bundles;
        }
        Map<String, StandingRecordRow> recordIndex = indexRecords(records);
        Map<String, List<StandingResultRow>> grouped = new java.util.LinkedHashMap<>();
        for (StandingResultRow row : rows) {
            if (row.getEventId() == null) {
                continue;
            }
            String key = row.getEventId() + "#" + (row.getRoundId() == null ? 0L : row.getRoundId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        Map<String, Integer> nameCounter = new HashMap<>();
        int index = 0;
        for (Map.Entry<String, List<StandingResultRow>> entry : grouped.entrySet()) {
            List<StandingResultRow> list = entry.getValue();
            StandingResultRow first = list.get(0);
            String sheetKey = "result:" + first.getEventId() + ":" + (first.getRoundId() == null ? 0L : first.getRoundId());
            String rawName = sheetTitle(first, groupNames);
            String sheetName = uniqueSheetName(rawName, nameCounter);
            List<List<String>> data = new ArrayList<>(list.size());
            for (StandingResultRow row : list) {
                data.add(resultRow(row, recordIndex));
            }
            SheetBundleVO bundle = SheetBundleVO.of(sheetKey, sheetName, RESULT_HEADERS, data);
            bundles.add(bundle);
            index++;
        }
        if (meet != null && !bundles.isEmpty()) {
            appendFooter(bundles, meet);
        }
        log.debug("成绩册装配完成：sheet 数={}", bundles.size());
        return bundles;
    }

    /**
     * 装配积分表（团体总分）：按团体口径分三张表。
     *
     * @param rows 团体总分投影列表
     * @param meet 届次信息
     * @return 表结构列表
     */
    public List<SheetBundleVO> buildTeamSheets(List<StandingTeamScoreRow> rows, StandingMeetRow meet) {
        List<SheetBundleVO> bundles = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return bundles;
        }
        Map<String, List<StandingTeamScoreRow>> grouped = new java.util.LinkedHashMap<>();
        for (StandingTeamScoreRow row : rows) {
            String type = row.getScoreType() == null ? "TOTAL" : row.getScoreType();
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<StandingTeamScoreRow>> entry : grouped.entrySet()) {
            List<List<String>> data = new ArrayList<>(entry.getValue().size());
            for (StandingTeamScoreRow row : entry.getValue()) {
                data.add(teamRow(row));
            }
            String title = "团体总分-" + StandingText.scoreTypeName(entry.getKey());
            bundles.add(SheetBundleVO.of("standings:team:" + entry.getKey(), title, TEAM_HEADERS, data));
        }
        if (meet != null) {
            appendFooter(bundles, meet);
        }
        return bundles;
    }

    /**
     * 装配奖牌榜：单张表。
     *
     * @param rows 奖牌榜投影列表
     * @param meet 届次信息
     * @return 表结构列表（恒为一个元素）
     */
    public List<SheetBundleVO> buildMedalSheets(List<StandingMedalRow> rows, StandingMeetRow meet) {
        List<List<String>> data = new ArrayList<>();
        if (rows != null) {
            for (StandingMedalRow row : rows) {
                data.add(medalRow(row));
            }
        }
        SheetBundleVO bundle = SheetBundleVO.of("standings:medals", "奖牌榜", MEDAL_HEADERS, data);
        List<SheetBundleVO> bundles = new ArrayList<>(1);
        bundles.add(bundle);
        if (meet != null) {
            appendFooter(bundles, meet);
        }
        return bundles;
    }

    /**
     * 装配积分明细表：单张表。
     *
     * @param rows 积分明细投影列表
     * @param meet 届次信息
     * @return 表结构列表（恒为一个元素）
     */
    public List<SheetBundleVO> buildPointSheets(List<StandingPointRow> rows, StandingMeetRow meet) {
        List<List<String>> data = new ArrayList<>();
        if (rows != null) {
            for (StandingPointRow row : rows) {
                data.add(pointRow(row));
            }
        }
        SheetBundleVO bundle = SheetBundleVO.of("points", "积分明细表", POINT_HEADERS, data);
        List<SheetBundleVO> bundles = new ArrayList<>(1);
        bundles.add(bundle);
        if (meet != null) {
            appendFooter(bundles, meet);
        }
        return bundles;
    }

    /**
     * 写出多 Sheet Excel 文件流（sheetKey 顺序即 Sheet 顺序）。
     *
     * @param bundles      表结构列表
     * @param outputStream 输出流
     */
    public void writeExcel(List<SheetBundleVO> bundles, OutputStream outputStream) {
        try (ExcelWriter writer = EasyExcel.write(outputStream).registerWriteHandler(styleStrategy()).build()) {
            int sheetNo = 0;
            Map<String, Integer> nameCounter = new HashMap<>();
            for (SheetBundleVO bundle : bundles) {
                if (bundle == null) {
                    continue;
                }
                List<List<String>> head = new ArrayList<>();
                for (String column : bundle.getHeaders()) {
                    List<String> cell = new ArrayList<>(1);
                    cell.add(column);
                    head.add(cell);
                }
                WriteSheet sheet = EasyExcel.writerSheet(sheetNo, uniqueSheetName(bundle.getSheetName(), nameCounter))
                        .head(head)
                        .autoTrim(false)
                        .build();
                writer.write(bundle.getRows(), sheet);
                sheetNo++;
            }
        }
    }

    // ==================================================================================
    // 行装配
    // ==================================================================================

    private List<String> resultRow(StandingResultRow row, Map<String, StandingRecordRow> recordIndex) {
        List<String> cells = new ArrayList<>(RESULT_HEADERS.size());
        cells.add(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        cells.add(StandingText.text(row.getBibNo()));
        String name = StringUtils.hasText(row.getAthleteName()) ? row.getAthleteName() : row.getTeamName();
        cells.add(StandingText.text(name));
        cells.add(StandingText.text(StringUtils.hasText(row.getUnitShortName())
                ? row.getUnitShortName() : row.getUnitName()));
        cells.add(resultText(row));
        cells.add(windText(row));
        cells.add(remarkText(row, recordIndex));
        return cells;
    }

    /**
     * 成绩展示：原始成绩优先，为空时用数值 + 单位；无效状态（DNS/DNF/DSQ/NM）直接显示状态。
     */
    private String resultText(StandingResultRow row) {
        String status = row.getStatus();
        if (status != null && !status.isBlank() && !"VALID".equalsIgnoreCase(status)) {
            return status.toUpperCase();
        }
        if (StringUtils.hasText(row.getResultRaw())) {
            return row.getResultRaw().trim();
        }
        if (row.getResultValue() != null) {
            String unit = row.getResultUnit() == null ? "" : row.getResultUnit();
            return row.getResultValue().stripTrailingZeros().toPlainString() + unit;
        }
        return StandingText.EMPTY;
    }

    /**
     * 风速：无风速显示 {@code -}；超风速追加「超风速」标记。
     */
    private String windText(StandingResultRow row) {
        if (row.getWindSpeed() == null) {
            return StandingText.EMPTY;
        }
        String value = row.getWindSpeed().stripTrailingZeros().toPlainString();
        if (row.getWindLegal() != null && row.getWindLegal() == 0) {
            return value + "(超风速)";
        }
        return value;
    }

    /**
     * 备注：优先库内备注；破纪录候选/已认定时追加纪录标记与所破纪录层级。
     */
    private String remarkText(StandingResultRow row, Map<String, StandingRecordRow> recordIndex) {
        StringBuilder builder = new StringBuilder();
        boolean candidate = row.getRecordCandidate() != null && row.getRecordCandidate() == 1;
        boolean confirmed = row.getRecordConfirmed() != null && row.getRecordConfirmed() == 1;
        if (candidate || confirmed || StringUtils.hasText(row.getRecordType())) {
            StandingRecordRow record = recordIndex.get(recordKey(row.getEventId(), row.getRecordType()));
            String level = recordTypeName(row.getRecordType());
            builder.append(confirmed ? "破" : "拟破").append(level);
            if (record != null && StringUtils.hasText(record.getRecordRaw())) {
                builder.append("(原纪录 ").append(record.getRecordRaw().trim()).append(')');
            }
        }
        if (StringUtils.hasText(row.getRemark())) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(row.getRemark().trim());
        }
        return builder.length() == 0 ? StandingText.EMPTY : builder.toString();
    }

    private List<String> teamRow(StandingTeamScoreRow row) {
        List<String> cells = new ArrayList<>(TEAM_HEADERS.size());
        cells.add(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        cells.add(StandingText.text(StringUtils.hasText(row.getUnitShortName())
                ? row.getUnitShortName() : row.getUnitName()));
        cells.add(StandingText.text(row.getGroupName()));
        cells.add(StandingText.number(row.getRankPointSum()));
        cells.add(StandingText.number(row.getBonusPointSum()));
        cells.add(StandingText.number(row.getRelayPointSum()));
        cells.add(StandingText.number(row.getTotalPoint()));
        cells.add(StandingText.number(row.getEventCount()));
        cells.add(StandingText.number(row.getAthleteCount()));
        return cells;
    }

    private List<String> medalRow(StandingMedalRow row) {
        List<String> cells = new ArrayList<>(MEDAL_HEADERS.size());
        cells.add(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        cells.add(StandingText.text(StringUtils.hasText(row.getUnitShortName())
                ? row.getUnitShortName() : row.getUnitName()));
        cells.add(StandingText.text(row.getGroupName()));
        cells.add(StandingText.number(row.getGold()));
        cells.add(StandingText.number(row.getSilver()));
        cells.add(StandingText.number(row.getBronze()));
        cells.add(StandingText.number(row.getTotalMedal()));
        cells.add(StandingText.number(row.getSortScore()));
        return cells;
    }

    private List<String> pointRow(StandingPointRow row) {
        List<String> cells = new ArrayList<>(POINT_HEADERS.size());
        cells.add(StandingText.text(row.getEventName()));
        cells.add(StandingText.text(row.getRoundName()));
        cells.add(StandingText.text(row.getGroupName()));
        cells.add(StandingText.rankDisplay(row.getRankDisplay(), row.getRankNo()));
        cells.add(StandingText.text(row.getBibNo()));
        String name = StringUtils.hasText(row.getAthleteName()) ? row.getAthleteName() : row.getTeamName();
        cells.add(StandingText.text(name));
        cells.add(StandingText.text(StringUtils.hasText(row.getUnitShortName())
                ? row.getUnitShortName() : row.getUnitName()));
        cells.add(StandingText.number(row.getRankPoint()));
        cells.add(StandingText.number(row.getBonusPoint()));
        cells.add(StandingText.number(row.getRelayFactor()));
        cells.add(StandingText.number(row.getTotalPoint()));
        cells.add(StandingText.text(row.getScoreRuleCode()));
        cells.add(StandingText.text(row.getBonusRuleIds()));
        return cells;
    }

    // ==================================================================================
    // 工具
    // ==================================================================================

    /**
     * 成绩册表标题：组别 + 项目名 + 轮次名。
     */
    private String sheetTitle(StandingResultRow row, Map<Long, String> groupNames) {
        StringBuilder builder = new StringBuilder();
        String groupName = row.getGroupName();
        if (!StringUtils.hasText(groupName) && groupNames != null && row.getGroupId() != null) {
            groupName = groupNames.get(row.getGroupId());
        }
        if (StringUtils.hasText(groupName)) {
            builder.append(groupName.trim()).append(' ');
        }
        builder.append(StandingText.text(row.getEventName()));
        if (StringUtils.hasText(row.getRoundName())) {
            builder.append(' ').append(row.getRoundName().trim());
        }
        return builder.toString();
    }

    /**
     * 在每张表末尾追加一行「打印时间」页脚（占满列宽，便于 A4 打印留痕）。
     */
    private void appendFooter(List<SheetBundleVO> bundles, StandingMeetRow meet) {
        String footer = "打印时间：" + FOOTER_FORMAT.format(LocalDateTime.now())
                + "    届次：" + StandingText.text(meet.getMeetName());
        for (SheetBundleVO bundle : bundles) {
            int columns = bundle.getHeaders() == null ? 0 : bundle.getHeaders().size();
            if (columns <= 0) {
                continue;
            }
            List<String> row = new ArrayList<>(columns);
            row.add(footer);
            for (int i = 1; i < columns; i++) {
                row.add("");
            }
            bundle.getRows().add(row);
        }
    }

    /**
     * 现行纪录索引：{@code eventId#recordType} → 纪录行。
     */
    private Map<String, StandingRecordRow> indexRecords(List<StandingRecordRow> records) {
        Map<String, StandingRecordRow> map = new HashMap<>();
        if (records != null) {
            for (StandingRecordRow record : records) {
                map.putIfAbsent(recordKey(record.getEventId(), record.getRecordType()), record);
            }
        }
        return map;
    }

    private String recordKey(Long eventId, String recordType) {
        return (eventId == null ? 0L : eventId) + "#" + (recordType == null ? "" : recordType);
    }

    /**
     * 纪录层级中文名。
     */
    private String recordTypeName(String recordType) {
        if (!StringUtils.hasText(recordType)) {
            return "纪录";
        }
        return switch (recordType.trim().toUpperCase()) {
            case "WORLD" -> "世界纪录";
            case "ASIA" -> "亚洲纪录";
            case "NATION" -> "全国纪录";
            case "PROVINCE" -> "省级纪录";
            case "CITY" -> "市级纪录";
            case "MEET" -> "赛会纪录";
            case "GROUP" -> "组别纪录";
            default -> "纪录";
        };
    }

    /**
     * Sheet 名清洗并保证同一工作簿内唯一（重名追加 {@code (n)}，仍受 31 字符上限约束）。
     */
    private String uniqueSheetName(String rawName, Map<String, Integer> nameCounter) {
        String cleaned = rawName == null ? "" : rawName.replaceAll(ILLEGAL_SHEET_CHARS, "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "榜单";
        }
        if (cleaned.length() > EXCEL_SHEET_NAME_LIMIT) {
            cleaned = cleaned.substring(0, EXCEL_SHEET_NAME_LIMIT);
        }
        Integer count = nameCounter.get(cleaned);
        if (count == null) {
            nameCounter.put(cleaned, 1);
            return cleaned;
        }
        nameCounter.put(cleaned, count + 1);
        String suffix = "(" + count + ")";
        int keep = Math.max(1, EXCEL_SHEET_NAME_LIMIT - suffix.length());
        String base = cleaned.length() > keep ? cleaned.substring(0, keep) : cleaned;
        return base + suffix;
    }

    /**
     * 统一表头与正文样式（表头加粗居中、正文居中），保证 A4 打印观感一致。
     */
    private HorizontalCellStyleStrategy styleStrategy() {
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        WriteFont headFont = new WriteFont();
        headFont.setBold(true);
        headStyle.setWriteFont(headFont);

        WriteCellStyle bodyStyle = new WriteCellStyle();
        bodyStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return new HorizontalCellStyleStrategy(headStyle, bodyStyle);
    }

    /**
     * 校验导出范围非空，避免下载到没有 Sheet 的空文件。
     *
     * @param bundles 表结构列表
     */
    public void requireNotEmpty(List<SheetBundleVO> bundles) {
        if (bundles == null || bundles.isEmpty()) {
            throw new BizException(ResultCode.NOT_FOUND, "暂无可导出的数据");
        }
    }
}
