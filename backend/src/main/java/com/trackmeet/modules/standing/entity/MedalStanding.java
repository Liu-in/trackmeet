package com.trackmeet.modules.standing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * medal_standing 奖牌榜快照表：按单位 × 组别聚合的金银铜牌快照，排序优先级由届次规则配置。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code medal_standing} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, meet_id, unit_id, group_id, gold, silver, bronze, total_medal, sort_score, medal_sort,
 * rank_no, rank_display, calc_time, calc_batch, is_published, remark,
 * create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * <p>
 * <b>幂等重建要点</b>：唯一键 {@code uk_meet_unit_group (meet_id, unit_id, group_id)} 不含 {@code deleted}，
 * 重算时按届次范围<b>物理删除</b>后再插入（见 {@code StandingMapper#deleteMedalStandings}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("medal_standing")
public class MedalStanding extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 排序规则：金牌优先（默认） */
    public static final String SORT_GOLD_FIRST = "GOLD_FIRST";
    /** 排序规则：奖牌总数优先 */
    public static final String SORT_TOTAL_FIRST = "TOTAL_FIRST";
    /** 排序规则：积分优先 */
    public static final String SORT_POINT_FIRST = "POINT_FIRST";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 参赛单位 ID，关联 unit.id */
    @TableField("unit_id")
    private Long unitId;

    /** 组别 ID，关联 meet_group.id */
    @TableField("group_id")
    private Long groupId;

    /** 金牌数 */
    @TableField("gold")
    private Integer gold;

    /** 银牌数 */
    @TableField("silver")
    private Integer silver;

    /** 铜牌数 */
    @TableField("bronze")
    private Integer bronze;

    /** 奖牌总数 */
    @TableField("total_medal")
    private Integer totalMedal;

    /** 排序分，按配置权重折算 */
    @TableField("sort_score")
    private BigDecimal sortScore;

    /** 排序规则快照，如 GOLD_FIRST */
    @TableField("medal_sort")
    private String medalSort;

    /** 奖牌榜名次 */
    @TableField("rank_no")
    private Integer rankNo;

    /** 名次显示文本（含并列） */
    @TableField("rank_display")
    private String rankDisplay;

    /** 统计时间 */
    @TableField("calc_time")
    private LocalDateTime calcTime;

    /** 计算批次号 */
    @TableField("calc_batch")
    private String calcBatch;

    /** 是否已对外发布：1 已发布 / 0 未发布 */
    @TableField("is_published")
    private Integer isPublished;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
