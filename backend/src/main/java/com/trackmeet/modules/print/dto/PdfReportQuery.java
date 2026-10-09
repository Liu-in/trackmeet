package com.trackmeet.modules.print.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Set;

/**
 * PDF / Excel 报表输出的统一查询参数（打印中心全部输出接口共用）。
 * <p>
 * 与既有 {@code PrintQuery}（秩序册四表）和 {@code StandingQuery}（榜单）的关系：
 * 本类是「打印中心」的对外入参，服务层再把它转成上述两个既有查询对象，从而完全复用既有取数逻辑。
 * <p>
 * 说明：
 * <ol>
 *     <li>{@code meetId} 可省略，省略时取当前届次上下文（请求头 {@code X-Meet-Id} 或参数 {@code meetId}）；
 *         两者同时存在且不一致时按越权处理（{@code FORBIDDEN}）；</li>
 *     <li>{@code publishOnly} 默认 {@code true}（只输出已发布日程），置 {@code false} 时导出全部并在状态列标记「草稿」；</li>
 *     <li>{@code roundId} / {@code heatId} 为契约预留字段：现有取数服务（秩序册 / 榜单）不支持按轮次或分组过滤，
 *         传入时不影响结果（如需按轮次出表，请使用 {@code sessionId} + {@code eventId} 组合）；</li>
 *     <li>{@code reportType} 可省略；传入时必须与接口路径表达的报表类型一致（兼容 {@code order-book} 与
 *         {@code ORDER_BOOK} 两种写法），否则抛 {@code PARAM_ERROR}。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Data
public class PdfReportQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支持的团体口径 */
    private static final Set<String> SCORE_TYPES = Set.of("MALE", "FEMALE", "TOTAL");

    /** 支持的证书类型 */
    private static final Set<String> CERT_TYPES = Set.of("RANK", "RESULT", "MEDAL", "PARTICIPATION");

    /** 届次 ID（多租户）；为空时取当前届次上下文 */
    @Positive(message = "meetId 必须为正整数")
    private Long meetId;

    /** 报表类型（可空；如 {@code order-book} / {@code ORDER_BOOK}） */
    private String reportType;

    /** 竞赛单元 ID（schedule_session.id） */
    @Positive(message = "sessionId 必须为正整数")
    private Long sessionId;

    /** 组别 ID（meet_group.id） */
    @Positive(message = "groupId 必须为正整数")
    private Long groupId;

    /** 项目 ID（event.id） */
    @Positive(message = "eventId 必须为正整数")
    private Long eventId;

    /** 参赛单位 ID（unit.id） */
    @Positive(message = "unitId 必须为正整数")
    private Long unitId;

    /** 场地 ID（venue.id，日程场地或单元主场地；秩序册 / 检录表 / 分道表用） */
    @Positive(message = "venueId 必须为正整数")
    private Long venueId;

    /** 轮次 ID（event_round.id，契约预留） */
    @Positive(message = "roundId 必须为正整数")
    private Long roundId;

    /** 分组 ID（heat.id，契约预留） */
    @Positive(message = "heatId 必须为正整数")
    private Long heatId;

    /** 团体口径：MALE 男子团体 / FEMALE 女子团体 / TOTAL 总团体（积分表用） */
    private String scoreType;

    /** 证书类型：RANK / RESULT / MEDAL / PARTICIPATION（证书用） */
    private String certType;

    /** 是否只输出已发布（PUBLISHED）日程：默认 true */
    private Boolean publishOnly = Boolean.TRUE;

    /** 打印模板 ID（可空，空表示使用报表类型的默认模板） */
    @Positive(message = "templateId 必须为正整数")
    private Long templateId;

    /**
     * 是否只输出已发布日程（null 视为 true）。
     *
     * @return {@code true} 表示只输出已发布日程
     */
    public boolean isPublishOnlyEnabled() {
        return publishOnly == null || Boolean.TRUE.equals(publishOnly);
    }

    /**
     * 规范化团体口径：空值返回 {@code null}（表示三档全部输出），非法值回落 {@code TOTAL}。
     *
     * @return {@code MALE} / {@code FEMALE} / {@code TOTAL} / {@code null}
     */
    public String normalizedScoreType() {
        if (!StringUtils.hasText(scoreType)) {
            return null;
        }
        String upper = scoreType.trim().toUpperCase();
        return SCORE_TYPES.contains(upper) ? upper : "TOTAL";
    }

    /**
     * 规范化证书类型：空值返回 {@code null}（表示全部类型），非法值回落 {@code RANK}。
     *
     * @return {@code RANK} / {@code RESULT} / {@code MEDAL} / {@code PARTICIPATION} / {@code null}
     */
    public String normalizedCertType() {
        if (!StringUtils.hasText(certType)) {
            return null;
        }
        String upper = certType.trim().toUpperCase();
        return CERT_TYPES.contains(upper) ? upper : "RANK";
    }

    /**
     * 是否显式指定了打印模板。
     *
     * @return {@code true} 表示使用指定模板
     */
    public boolean hasTemplate() {
        return templateId != null;
    }
}
