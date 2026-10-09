package com.trackmeet.modules.announcement.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大屏团体总分投影：{@code team_score} 取 {@code score_type = 'TOTAL'} 联 {@code unit} /
 * {@code meet_group} 取名，按 {@code total_point} 降序取前 N。
 *
 * @author TrackMeet
 */
@Data
public class ScreenTeamScoreRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照 ID */
    private Long id;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 团体口径：MALE / FEMALE / TOTAL */
    private String scoreType;

    /** 名次分合计 */
    private BigDecimal rankPointSum;

    /** 破纪录加分合计 */
    private BigDecimal bonusPointSum;

    /** 接力项目得分合计 */
    private BigDecimal relayPointSum;

    /** 团体总分 */
    private BigDecimal totalPoint;

    /** 团体名次 */
    private Integer rankNo;

    /** 名次显示文本（含并列） */
    private String rankDisplay;

    /** 得分项目数 */
    private Integer eventCount;

    /** 得分人数 */
    private Integer athleteCount;

    /** 统计时间 */
    private LocalDateTime calcTime;

    /** 是否已对外发布：1 是 / 0 否 */
    private Integer isPublished;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 组别名称 */
    private String groupName;
}
