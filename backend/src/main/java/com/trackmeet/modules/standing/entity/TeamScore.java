package com.trackmeet.modules.standing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * team_score 团体总分快照表：按单位 × 组别 × 性别口径聚合的团体总分快照，支持男团/女团/总团三张榜。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code team_score} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, meet_id, unit_id, group_id, score_type, rank_point_sum, bonus_point_sum, relay_point_sum,
 * total_point, rank_no, rank_display, event_count, athlete_count, calc_time, calc_batch,
 * is_published, remark, create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * <p>
 * <b>幂等重建要点</b>：唯一键 {@code uk_meet_unit_group_score_type (meet_id, unit_id, group_id, score_type)}
 * 不含 {@code deleted}，重算时按届次范围<b>物理删除</b>后再插入（见 {@code StandingMapper#deleteTeamScores}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_score")
public class TeamScore extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 团体口径：男子团体 */
    public static final String TYPE_MALE = "MALE";
    /** 团体口径：女子团体 */
    public static final String TYPE_FEMALE = "FEMALE";
    /** 团体口径：总团体 */
    public static final String TYPE_TOTAL = "TOTAL";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 参赛单位 ID，关联 unit.id */
    @TableField("unit_id")
    private Long unitId;

    /** 组别 ID，关联 meet_group.id */
    @TableField("group_id")
    private Long groupId;

    /** 团体口径：MALE 男子团体 / FEMALE 女子团体 / TOTAL 总团体 */
    @TableField("score_type")
    private String scoreType;

    /** 名次分合计 */
    @TableField("rank_point_sum")
    private BigDecimal rankPointSum;

    /** 破纪录加分合计（是否计入由 meet_rule_config.bonus_in_team_score 决定） */
    @TableField("bonus_point_sum")
    private BigDecimal bonusPointSum;

    /** 接力项目得分合计 */
    @TableField("relay_point_sum")
    private BigDecimal relayPointSum;

    /** 团体总分 */
    @TableField("total_point")
    private BigDecimal totalPoint;

    /** 团体名次 */
    @TableField("rank_no")
    private Integer rankNo;

    /** 名次显示文本（含并列） */
    @TableField("rank_display")
    private String rankDisplay;

    /** 得分项目数 */
    @TableField("event_count")
    private Integer eventCount;

    /** 得分人数 */
    @TableField("athlete_count")
    private Integer athleteCount;

    /** 统计时间 */
    @TableField("calc_time")
    private LocalDateTime calcTime;

    /** 计算批次号，便于整批重算覆盖 */
    @TableField("calc_batch")
    private String calcBatch;

    /** 是否已对外发布：1 已发布 / 0 未发布 */
    @TableField("is_published")
    private Integer isPublished;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
