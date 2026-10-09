package com.trackmeet.modules.print.vo;

import com.trackmeet.modules.print.support.PrintText;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 总日程表行：一条日程条目（项目 × 轮次 × 场地 × 时间窗）。
 * <p>
 * 日期 / 时段 / 时间 / 场地 / 项目 / 轮次 / 组数 / 时长 / 备注，另加「状态」列
 * （{@code publishOnly=false} 时草稿显示「草稿」，即需求中的草稿标记列）。
 * 日期时间均为 {@code yyyy-MM-dd} / {@code HH:mm} 字符串。
 *
 * @author TrackMeet
 */
@Data
public class ScheduleSheetRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期格式：yyyy-MM-dd */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日期（yyyy-MM-dd，含「（第N天）」后缀） */
    private String sessionDate;

    /** 赛期第几天（原始值，便于程序判断） */
    private Integer dayNo;

    /** 时段名称：上午/下午/晚上 */
    private String periodName;

    /** 开始时间 HH:mm */
    private String startTime;

    /** 结束时间 HH:mm */
    private String endTime;

    /** 时间区间 HH:mm-HH:mm */
    private String timeRange;

    /** 场地名称（日程场地，为空时用单元主场地） */
    private String venueName;

    /** 项目（含组别前缀，如「高中男子组 100米」） */
    private String eventName;

    /** 轮次名称（如「预赛」「决赛」） */
    private String roundName;

    /** 组别名称（原始值，便于程序判断） */
    private String groupName;

    /** 组数 */
    private Integer heatCount;

    /** 预计时长（分钟） */
    private Integer durationMin;

    /** 备注（日程备注、冲突提示等） */
    private String remark;

    /** 发布状态名称：已发布/草稿 */
    private String publishStatusName;

    /** 发布状态原始值：PUBLISHED/DRAFT */
    private String publishStatus;

    /** 单元名称 */
    private String sessionName;

    /**
     * 转为表头 {@code PrintSheetKeys.HEADERS_SCHEDULE} 对应的一行单元格。
     *
     * @return 行数据
     */
    public List<String> toRow() {
        return Arrays.asList(
                PrintText.text(sessionDate),
                PrintText.text(periodName),
                PrintText.text(timeRange),
                PrintText.text(venueName),
                PrintText.text(eventName),
                PrintText.text(roundName),
                PrintText.number(heatCount),
                PrintText.number(durationMin),
                PrintText.text(remark),
                PrintText.text(publishStatusName));
    }

    /**
     * 展示用日期：{@code 2025-10-18（第1天）}。
     *
     * @param date  单元日期
     * @param dayNo 赛期第几天
     * @return 展示文本
     */
    public static String buildSessionDate(LocalDate date, Integer dayNo) {
        if (date == null) {
            return PrintText.EMPTY;
        }
        String text = DATE_FORMATTER.format(date);
        return dayNo == null ? text : text + "（第" + dayNo + "天）";
    }
}
