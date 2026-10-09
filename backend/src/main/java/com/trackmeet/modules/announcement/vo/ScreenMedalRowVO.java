package com.trackmeet.modules.announcement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 大屏奖牌榜行 VO：{@code medal_standing} 一行 + 单位 / 组别名。
 *
 * @author TrackMeet
 */
@Data
public class ScreenMedalRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照 ID */
    private Long id;

    /** 参赛单位 ID */
    private Long unitId;

    /** 单位名称 */
    private String unitName;

    /** 单位简称（大屏优先显示） */
    private String unitShortName;

    /** 组别 ID */
    private Long groupId;

    /** 组别名称 */
    private String groupName;

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime calcTime;

    /** 是否已对外发布：1 是 / 0 否 */
    private Integer isPublished;

    /** 备注 */
    private String remark;
}
