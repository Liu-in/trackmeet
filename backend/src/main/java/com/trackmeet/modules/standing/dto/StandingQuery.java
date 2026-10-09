package com.trackmeet.modules.standing.dto;

import com.trackmeet.common.base.BaseQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 榜单通用查询条件：团体总分、奖牌榜、积分明细与打印输出共用。
 * <p>
 * {@code meetId} 必填且必须与 {@code MeetContext.get()} 一致，否则服务层抛 {@code FORBIDDEN}。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StandingQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填，须与 MeetContext 一致） */
    @NotNull(message = "届次不能为空")
    private Long meetId;

    /** 组别 ID（空为全部组别） */
    private Long groupId;

    /** 项目 ID（空为全部项目） */
    private Long eventId;

    /** 参赛单位 ID（空为全部单位） */
    private Long unitId;

    /** 团体口径：MALE 男子团体 / FEMALE 女子团体 / TOTAL 总团体 */
    private String scoreType;

    /** 导出格式：json（默认，返回 SheetBundleVO 列表）/ xlsx（直接输出文件流） */
    private String format;

    /**
     * 是否请求 xlsx 文件流。
     *
     * @return {@code true} 表示按 {@code format=xlsx} 输出 Excel
     */
    public boolean isXlsx() {
        return "xlsx".equalsIgnoreCase(format);
    }

    /**
     * 规范化团体口径：空或非法值统一按 {@code TOTAL} 处理。
     *
     * @return {@code MALE} / {@code FEMALE} / {@code TOTAL}
     */
    public String normalizedScoreType() {
        if ("MALE".equalsIgnoreCase(scoreType)) {
            return "MALE";
        }
        if ("FEMALE".equalsIgnoreCase(scoreType)) {
            return "FEMALE";
        }
        return "TOTAL";
    }
}
