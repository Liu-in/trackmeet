package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖牌榜投影行：{@code medal_standing} 快照联 {@code unit} / {@code meet_group} 的只读明细。
 *
 * @author TrackMeet
 */
@Data
public class StandingMedalRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 参赛单位 ID */
    private Long unitId;

    /** 组别 ID */
    private Long groupId;

    /** 金牌数 */
    private Integer gold;

    /** 银牌数 */
    private Integer silver;

    /** 铜牌数 */
    private Integer bronze;

    /** 奖牌总数 */
    private Integer totalMedal;

    /** 排序分 */
    private BigDecimal sortScore;

    /** 排序规则快照 */
    private String medalSort;

    /** 奖牌榜名次 */
    private Integer rankNo;

    /** 名次显示文本 */
    private String rankDisplay;

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
