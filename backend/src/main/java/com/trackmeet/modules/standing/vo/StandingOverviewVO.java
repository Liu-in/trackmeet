package com.trackmeet.modules.standing.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 榜单总览 VO：积分重算 / 快照保存后的统计回执。
 * <p>
 * 同时携带团体总分与奖牌榜结果，便于前端一次请求完成「重算 → 刷新两张榜」。
 *
 * @author TrackMeet
 */
@Data
public class StandingOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID */
    private Long meetId;

    /** 组别 ID（全量重算为空） */
    private Long groupId;

    /** 项目 ID（全量重算为空） */
    private Long eventId;

    /** 本次涉及的积分明细条数 */
    private Integer pointCount;

    /** 本次涉及的团体总分行数 */
    private Integer teamScoreCount;

    /** 本次涉及的奖牌榜行数 */
    private Integer medalCount;

    /** 本次重建的团体总分合计（含全部口径） */
    private BigDecimal totalPoint;

    /** 计算批次号 */
    private String calcBatch;

    /** 计算时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calcTime;

    /** 奖牌榜排序规则（GOLD_FIRST / TOTAL_FIRST / POINT_FIRST） */
    private String medalSort;

    /** 重算范围说明 */
    private String scopeText;

    /** 团体总分榜单 */
    private List<TeamScoreVO> teamScores = new ArrayList<>();

    /** 奖牌榜 */
    private List<MedalVO> medals = new ArrayList<>();
}
