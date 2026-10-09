package com.trackmeet.modules.announcement.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大屏奖牌榜投影：{@code medal_standing} 联 {@code unit} / {@code meet_group} 取名，
 * 按 {@code sort_score} 降序取前 N。
 *
 * @author TrackMeet
 */
@Data
public class ScreenMedalRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照 ID */
    private Long id;

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

    /** 排序分（按配置权重折算） */
    private BigDecimal sortScore;

    /** 排序规则快照 */
    private String medalSort;

    /** 奖牌榜名次 */
    private Integer rankNo;

    /** 名次显示文本（含并列） */
    private String rankDisplay;

    /** 统计时间 */
    private LocalDateTime calcTime;

    /** 是否已对外发布：1 是 / 0 否 */
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
