package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 团体总分投影行：{@code team_score} 快照联 {@code unit} / {@code meet_group} 的只读明细。
 *
 * @author TrackMeet
 */
@Data
public class StandingTeamScoreRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

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

    /** 名次显示文本 */
    private String rankDisplay;

    /** 得分项目数 */
    private Integer eventCount;

    /** 得分人数 */
    private Integer athleteCount;

    /** 统计时间 */
    private LocalDateTime calcTime;

    /** 计算批次号 */
    private String calcBatch;

    /** 是否已发布：1 是 / 0 否 */
    private Integer isPublished;

    /** 备注 */
    private String remark;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 组别名称 */
    private String groupName;
}
