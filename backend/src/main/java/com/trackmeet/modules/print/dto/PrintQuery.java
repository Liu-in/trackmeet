package com.trackmeet.modules.print.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 打印/导出统一查询参数（秩序册、分组表、分道表、检录表共用）。
 * <p>
 * 说明：
 * <ol>
 *     <li>{@code meetId} 可省略，省略时取当前届次上下文（请求头 {@code X-Meet-Id} 或参数 {@code meetId}，
 *         由 {@code MeetContextFilter} 写入 {@code MeetContext}）；两者同时存在但取值不一致时按越权处理；</li>
 *     <li>{@code publishOnly} 默认 {@code true}（只输出已发布日程），置 {@code false} 时导出全部并在
 *         状态列标记「草稿」；</li>
 *     <li>{@code format} 默认 {@code json}，{@code xlsx} 时返回 Excel 文件流。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Data
public class PrintQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JSON 输出 */
    public static final String FORMAT_JSON = "json";
    /** Excel 输出 */
    public static final String FORMAT_XLSX = "xlsx";

    /** 届次 ID（多租户）；为空时取当前届次上下文 */
    @Positive(message = "meetId 必须为正整数")
    private Long meetId;

    /** 竞赛单元 ID（schedule_session.id） */
    @Positive(message = "sessionId 必须为正整数")
    private Long sessionId;

    /** 组别 ID（meet_group.id，项目所属组别） */
    @Positive(message = "groupId 必须为正整数")
    private Long groupId;

    /** 项目 ID（event.id） */
    @Positive(message = "eventId 必须为正整数")
    private Long eventId;

    /** 参赛单位 ID（unit.id，按单位过滤参赛人员） */
    @Positive(message = "unitId 必须为正整数")
    private Long unitId;

    /** 场地 ID（venue.id，日程场地或单元主场地） */
    @Positive(message = "venueId 必须为正整数")
    private Long venueId;

    /** 输出格式：json（默认）/ xlsx */
    @Pattern(regexp = "(?i)^(json|xlsx)?$", message = "format 仅支持 json 或 xlsx")
    private String format = FORMAT_JSON;

    /** 是否只输出已发布（PUBLISHED）日程：默认 true */
    private Boolean publishOnly = Boolean.TRUE;

    /**
     * 是否 Excel 输出（忽略大小写与首尾空白）。
     */
    public boolean isXlsx() {
        return format != null && FORMAT_XLSX.equalsIgnoreCase(format.trim());
    }

    /**
     * 是否只输出已发布日程（null 视为 true）。
     */
    public boolean isPublishOnlyEnabled() {
        return publishOnly == null || Boolean.TRUE.equals(publishOnly);
    }

    /**
     * 规范化后的输出格式：非 xlsx 一律按 json 处理。
     */
    public String resolvedFormat() {
        return isXlsx() ? FORMAT_XLSX : FORMAT_JSON;
    }
}
