package com.trackmeet.modules.standing.mapper;

import com.trackmeet.modules.standing.dto.StandingAttemptRow;
import com.trackmeet.modules.standing.dto.StandingCertificateRow;
import com.trackmeet.modules.standing.dto.StandingGroupRow;
import com.trackmeet.modules.standing.dto.StandingMedalRow;
import com.trackmeet.modules.standing.dto.StandingMeetRow;
import com.trackmeet.modules.standing.dto.StandingParticipantRow;
import com.trackmeet.modules.standing.dto.StandingPointRow;
import com.trackmeet.modules.standing.dto.StandingRecordRow;
import com.trackmeet.modules.standing.dto.StandingResultRow;
import com.trackmeet.modules.standing.dto.StandingTeamScoreRow;
import com.trackmeet.modules.standing.dto.StandingUnitRow;
import com.trackmeet.modules.standing.entity.MedalStanding;
import com.trackmeet.modules.standing.entity.Points;
import com.trackmeet.modules.standing.entity.TeamScore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分与榜单专用 Mapper：全部为 {@code @Select} 只读投影 + 幂等重建所需的物理删除 / 快照更新。
 * <p>
 * 设计说明（与 {@code modules/print/mapper/PrintMapper} 保持同一风格）：
 * <ol>
 *     <li><b>不依赖其它模块的实体与 Mapper</b>：所有联表查询直接从
 *         {@code result} / {@code event} / {@code event_round} / {@code unit} / {@code athlete} /
 *         {@code registration} / {@code meet_group} / {@code record} / {@code certificate}
 *         读取并返回本模块 DTO，避免跨模块耦合；</li>
 *     <li><b>自带租户与逻辑删除条件</b>：所有业务表 SQL 均显式带 {@code meet_id = #{meetId}} 与
 *         {@code deleted = 0}（join 表的 {@code deleted = 0} 写在 ON 子句），不依赖多租户拦截器；</li>
 *     <li><b>物理删除</b>：{@code points} 的唯一键 {@code (event_id, round_id, registration_id)}、
 *         {@code team_score} 的 {@code (meet_id, unit_id, group_id, score_type)}、
 *         {@code medal_standing} 的 {@code (meet_id, unit_id, group_id)} 均<b>不含</b> {@code deleted}，
 *         重算前必须物理删除，否则逻辑删除残留行会与新行争抢唯一键。
 *         此处与既有先例 {@code ScoreRuleItemMapper#deleteByRuleId} 一致；</li>
 *     <li><b>避免 N+1</b>：结果集一次查全（含项目/轮次/单位/运动员/组别名），规则在服务层按 ID 批量装载。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Mapper
public interface StandingMapper {

    // ==================================================================================
    // 一、届次与字典
    // ==================================================================================

    /**
     * 届次信息（榜单标题、证书签发单位与编号前缀）。
     *
     * @param meetId 届次 ID
     * @return 届次投影，不存在时返回 null
     */
    @Select("SELECT m.id AS id, m.meet_name AS meetName, m.edition AS edition, m.short_name AS shortName, "
            + "m.host AS host, m.organizer AS organizer, m.start_date AS startDate, "
            + "m.end_date AS endDate, m.place AS place "
            + "FROM meet m WHERE m.deleted = 0 AND m.id = #{meetId}")
    StandingMeetRow selectMeetInfo(@Param("meetId") Long meetId);

    /**
     * 组别清单（用于补齐榜单的组别名）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可为空，为空表示全部）
     * @return 组别投影列表
     */
    @Select("<script>"
            + "SELECT g.id AS id, g.meet_id AS meetId, g.group_name AS groupName, g.group_code AS groupCode, "
            + "g.gender AS gender "
            + "FROM meet_group g "
            + "WHERE g.deleted = 0 AND g.meet_id = #{meetId} "
            + "<if test='groupId != null'>AND g.id = #{groupId} </if>"
            + "ORDER BY g.sort_no ASC, g.id ASC"
            + "</script>")
    List<StandingGroupRow> selectGroups(@Param("meetId") Long meetId, @Param("groupId") Long groupId);

    /**
     * 单位 ID → 单位名称映射（团体总分与奖牌榜汇总时的名称来源）。
     *
     * @param meetId 届次 ID
     * @return 单位投影列表
     */
    @Select("SELECT u.id AS id, u.unit_name AS unitName, u.short_name AS unitShortName "
            + "FROM unit u WHERE u.deleted = 0 AND u.meet_id = #{meetId} ORDER BY u.id ASC")
    List<StandingUnitRow> selectUnitNames(@Param("meetId") Long meetId);

    /**
     * 现行纪录清单（成绩册的纪录标记）。
     *
     * @param meetId 届次 ID
     * @return 现行纪录投影列表
     */
    @Select("SELECT r.id AS id, r.meet_id AS meetId, r.event_id AS eventId, r.group_id AS groupId, "
            + "r.record_type AS recordType, r.record_value AS recordValue, r.record_raw AS recordRaw, "
            + "r.record_unit AS recordUnit, r.athlete_name AS athleteName, r.unit_name AS unitName "
            + "FROM record r WHERE r.deleted = 0 AND r.meet_id = #{meetId} AND r.is_current = 1 "
            + "ORDER BY r.event_id ASC, r.id ASC")
    List<StandingRecordRow> selectCurrentRecords(@Param("meetId") Long meetId);

    // ==================================================================================
    // 二、成绩读取（积分计算 / 奖牌榜 / 成绩册 / 证书共用）
    // ==================================================================================

    /**
     * 已审核（{@code APPROVED}）成绩投影：积分重算、成绩册与证书生成的数据源。
     * <p>
     * 名次由成绩模块的 {@code RankResolver} 回写，此处直接使用 {@code result.rank_no}。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID（可空，空为全部项目）
     * @param groupId 组别 ID（可空，空为全部组别）
     * @param unitId  单位 ID（可空，空为全部单位）
     * @return 成绩投影列表，按项目 → 轮次 → 名次排序
     */
    @Select("<script>"
            + "SELECT r.id AS resultId, r.meet_id AS meetId, r.event_id AS eventId, r.round_id AS roundId, "
            + "r.registration_id AS registrationId, r.athlete_id AS athleteId, r.unit_id AS unitId, "
            + "r.group_id AS groupId, r.result_raw AS resultRaw, r.result_value AS resultValue, "
            + "r.result_unit AS resultUnit, r.rank_no AS rankNo, r.rank_display AS rankDisplay, "
            + "r.status AS status, r.wind_speed AS windSpeed, r.is_wind_legal AS windLegal, "
            + "r.is_record_candidate AS recordCandidate, r.is_record_confirmed AS recordConfirmed, "
            + "r.record_type AS recordType, r.remark AS remark, r.audit_status AS auditStatus, "
            + "r.audit_time AS auditTime, "
            + "e.event_name AS eventName, e.event_code AS eventCode, e.event_type AS eventType, "
            + "e.gender AS gender, e.is_relay AS eventRelay, reg.is_relay AS registrationRelay, "
            + "rd.round_code AS roundCode, rd.round_name AS roundName, rd.round_no AS roundNo, "
            + "rd.is_final AS finalRound, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, "
            + "a.athlete_name AS athleteName, a.bib_no AS bibNo, "
            + "reg.relay_name AS teamName, g.group_name AS groupName "
            + "FROM result r "
            + "JOIN event e ON e.id = r.event_id AND e.deleted = 0 "
            + "LEFT JOIN event_round rd ON rd.id = r.round_id AND rd.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0 "
            + "LEFT JOIN athlete a ON a.id = r.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN registration reg ON reg.id = r.registration_id AND reg.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.meet_id = #{meetId} AND r.audit_status = 'APPROVED' "
            + "<if test='eventId != null'>AND r.event_id = #{eventId} </if>"
            + "<if test='groupId != null'>AND r.group_id = #{groupId} </if>"
            + "<if test='unitId != null'>AND r.unit_id = #{unitId} </if>"
            + "ORDER BY r.event_id ASC, rd.round_no ASC, r.rank_no ASC, r.id ASC"
            + "</script>")
    List<StandingResultRow> selectApprovedResults(@Param("meetId") Long meetId,
                                                  @Param("eventId") Long eventId,
                                                  @Param("groupId") Long groupId,
                                                  @Param("unitId") Long unitId);

    /**
     * 决赛轮已审核成绩投影：奖牌榜（金/银/铜）的数据源。
     * <p>
     * 仅取 {@code event_round.is_final = 1} 的轮次，且服务层再按
     * {@code status = VALID} 与 {@code rank_no ∈ {1,2,3}} 过滤。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空，空为全部组别）
     * @return 决赛成绩投影列表
     */
    @Select("<script>"
            + "SELECT r.id AS resultId, r.meet_id AS meetId, r.event_id AS eventId, r.round_id AS roundId, "
            + "r.registration_id AS registrationId, r.athlete_id AS athleteId, r.unit_id AS unitId, "
            + "r.group_id AS groupId, r.result_raw AS resultRaw, r.result_value AS resultValue, "
            + "r.result_unit AS resultUnit, r.rank_no AS rankNo, r.rank_display AS rankDisplay, "
            + "r.status AS status, r.wind_speed AS windSpeed, r.is_wind_legal AS windLegal, "
            + "r.is_record_candidate AS recordCandidate, r.is_record_confirmed AS recordConfirmed, "
            + "r.record_type AS recordType, r.remark AS remark, r.audit_status AS auditStatus, "
            + "r.audit_time AS auditTime, "
            + "e.event_name AS eventName, e.event_code AS eventCode, e.event_type AS eventType, "
            + "e.gender AS gender, e.is_relay AS eventRelay, reg.is_relay AS registrationRelay, "
            + "rd.round_code AS roundCode, rd.round_name AS roundName, rd.round_no AS roundNo, "
            + "rd.is_final AS finalRound, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, "
            + "a.athlete_name AS athleteName, a.bib_no AS bibNo, "
            + "reg.relay_name AS teamName, g.group_name AS groupName "
            + "FROM result r "
            + "JOIN event e ON e.id = r.event_id AND e.deleted = 0 "
            + "JOIN event_round rd ON rd.id = r.round_id AND rd.deleted = 0 AND rd.is_final = 1 "
            + "LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0 "
            + "LEFT JOIN athlete a ON a.id = r.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN registration reg ON reg.id = r.registration_id AND reg.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.meet_id = #{meetId} AND r.audit_status = 'APPROVED' "
            + "<if test='groupId != null'>AND r.group_id = #{groupId} </if>"
            + "ORDER BY r.event_id ASC, r.rank_no ASC, r.id ASC"
            + "</script>")
    List<StandingResultRow> selectFinalResults(@Param("meetId") Long meetId,
                                               @Param("groupId") Long groupId);

    /**
     * 已通过报名的参赛人员投影：{@code PARTICIPATION} 参赛证书的数据源。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID（可空）
     * @param groupId 组别 ID（可空）
     * @param unitId  单位 ID（可空）
     * @return 参赛人员投影列表
     */
    @Select("<script>"
            + "SELECT reg.id AS registrationId, reg.meet_id AS meetId, reg.event_id AS eventId, "
            + "reg.athlete_id AS athleteId, reg.unit_id AS unitId, reg.group_id AS groupId, "
            + "a.athlete_name AS athleteName, a.bib_no AS bibNo, reg.relay_name AS teamName, "
            + "reg.is_relay AS relay, e.event_name AS eventName, e.event_code AS eventCode, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, g.group_name AS groupName, "
            + "reg.status AS status "
            + "FROM registration reg "
            + "JOIN event e ON e.id = reg.event_id AND e.deleted = 0 "
            + "LEFT JOIN athlete a ON a.id = reg.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = reg.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = reg.group_id AND g.deleted = 0 "
            + "WHERE reg.deleted = 0 AND reg.meet_id = #{meetId} AND reg.status = 'APPROVED' "
            + "<if test='eventId != null'>AND reg.event_id = #{eventId} </if>"
            + "<if test='groupId != null'>AND reg.group_id = #{groupId} </if>"
            + "<if test='unitId != null'>AND reg.unit_id = #{unitId} </if>"
            + "ORDER BY reg.event_id ASC, reg.unit_id ASC, reg.id ASC"
            + "</script>")
    List<StandingParticipantRow> selectParticipants(@Param("meetId") Long meetId,
                                                    @Param("eventId") Long eventId,
                                                    @Param("groupId") Long groupId,
                                                    @Param("unitId") Long unitId);

    /**
     * 田赛试跳/试掷投影：按成绩 ID 批量查询，用于装配 {@code ResultRecord.attempts}。
     * <p>
     * 仅用于引擎的平局规则（比较次优成绩 / 最好成绩出现轮次），按 {@code attempt_no} 升序返回。
     *
     * @param meetId    届次 ID
     * @param resultIds 成绩 ID 列表（调用方保证非空）
     * @return 试跳投影列表
     */
    @Select("<script>"
            + "SELECT ra.id AS id, ra.result_id AS resultId, ra.meet_id AS meetId, "
            + "ra.attempt_no AS attemptNo, ra.attempt_value AS attemptValue, "
            + "ra.is_success AS success, ra.is_pass AS pass, ra.is_foul AS foul "
            + "FROM result_attempt ra "
            + "WHERE ra.deleted = 0 AND ra.meet_id = #{meetId} AND ra.result_id IN "
            + "<foreach collection='resultIds' item='resultId' open='(' separator=',' close=')'>"
            + "#{resultId}</foreach> "
            + "ORDER BY ra.result_id ASC, ra.attempt_no ASC"
            + "</script>")
    List<StandingAttemptRow> selectAttempts(@Param("meetId") Long meetId,
                                            @Param("resultIds") List<Long> resultIds);

    // ==================================================================================
    // 三、榜单快照读取（团队总分 / 奖牌榜）
    // ==================================================================================

    /**
     * 团体总分快照读取（{@code team_score} 联单位与组别名）。
     *
     * @param meetId    届次 ID
     * @param groupId   组别 ID（可空）
     * @param scoreType 团体口径 MALE / FEMALE / TOTAL
     * @return 团体总分投影列表，按口径 → 名次排序
     */
    @Select("<script>"
            + "SELECT t.id AS id, t.meet_id AS meetId, t.unit_id AS unitId, t.group_id AS groupId, "
            + "t.score_type AS scoreType, t.rank_point_sum AS rankPointSum, "
            + "t.bonus_point_sum AS bonusPointSum, t.relay_point_sum AS relayPointSum, "
            + "t.total_point AS totalPoint, t.rank_no AS rankNo, t.rank_display AS rankDisplay, "
            + "t.event_count AS eventCount, t.athlete_count AS athleteCount, t.calc_time AS calcTime, "
            + "t.calc_batch AS calcBatch, t.is_published AS isPublished, t.remark AS remark, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, g.group_name AS groupName "
            + "FROM team_score t "
            + "LEFT JOIN unit u ON u.id = t.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = t.group_id AND g.deleted = 0 "
            + "WHERE t.deleted = 0 AND t.meet_id = #{meetId} "
            + "<if test='groupId != null'>AND t.group_id = #{groupId} </if>"
            + "<if test='scoreType != null'>AND t.score_type = #{scoreType} </if>"
            + "ORDER BY t.score_type ASC, t.rank_no ASC, t.total_point DESC, t.id ASC"
            + "</script>")
    List<StandingTeamScoreRow> selectTeamScores(@Param("meetId") Long meetId,
                                                @Param("groupId") Long groupId,
                                                @Param("scoreType") String scoreType);

    /**
     * 奖牌榜快照读取（{@code medal_standing} 联单位与组别名）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @return 奖牌榜投影列表，按名次排序
     */
    @Select("<script>"
            + "SELECT m.id AS id, m.meet_id AS meetId, m.unit_id AS unitId, m.group_id AS groupId, "
            + "m.gold AS gold, m.silver AS silver, m.bronze AS bronze, m.total_medal AS totalMedal, "
            + "m.sort_score AS sortScore, m.medal_sort AS medalSort, m.rank_no AS rankNo, "
            + "m.rank_display AS rankDisplay, m.calc_time AS calcTime, m.calc_batch AS calcBatch, "
            + "m.is_published AS isPublished, m.remark AS remark, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, g.group_name AS groupName "
            + "FROM medal_standing m "
            + "LEFT JOIN unit u ON u.id = m.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = m.group_id AND g.deleted = 0 "
            + "WHERE m.deleted = 0 AND m.meet_id = #{meetId} "
            + "<if test='groupId != null'>AND m.group_id = #{groupId} </if>"
            + "ORDER BY m.rank_no ASC, m.total_medal DESC, m.id ASC"
            + "</script>")
    List<StandingMedalRow> selectMedalStandings(@Param("meetId") Long meetId,
                                                @Param("groupId") Long groupId);

    // ==================================================================================
    // 四、积分明细（下钻与打印）
    // ==================================================================================

    /**
     * 积分明细查询 SQL 片段（供本接口内两个方法复用，避免重复维护联表与条件）。
     */
    String POINT_SELECT = "SELECT p.id AS id, p.meet_id AS meetId, p.event_id AS eventId, p.round_id AS roundId, "
            + "p.result_id AS resultId, p.registration_id AS registrationId, p.athlete_id AS athleteId, "
            + "p.unit_id AS unitId, p.group_id AS groupId, p.gender AS gender, p.rank_no AS rankNo, "
            + "p.rank_point AS rankPoint, p.bonus_point AS bonusPoint, p.relay_factor AS relayFactor, "
            + "p.total_point AS totalPoint, p.score_rule_id AS scoreRuleId, p.score_rule_code AS scoreRuleCode, "
            + "p.bonus_rule_ids AS bonusRuleIds, p.calc_time AS calcTime, p.is_manual AS isManual, "
            + "p.adjust_reason AS adjustReason, "
            + "e.event_name AS eventName, e.event_code AS eventCode, e.event_type AS eventType, "
            + "e.is_relay AS relay, rd.round_code AS roundCode, rd.round_name AS roundName, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, g.group_name AS groupName, "
            + "a.athlete_name AS athleteName, a.bib_no AS bibNo, reg.relay_name AS teamName, "
            + "res.rank_display AS rankDisplay "
            + "FROM points p "
            + "JOIN event e ON e.id = p.event_id AND e.deleted = 0 "
            + "LEFT JOIN event_round rd ON rd.id = p.round_id AND rd.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = p.unit_id AND u.deleted = 0 "
            + "LEFT JOIN athlete a ON a.id = p.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN registration reg ON reg.id = p.registration_id AND reg.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = p.group_id AND g.deleted = 0 "
            + "LEFT JOIN result res ON res.id = p.result_id AND res.deleted = 0 "
            + "WHERE p.deleted = 0 AND p.meet_id = #{meetId} "
            + "<if test='groupId != null'>AND p.group_id = #{groupId} </if>"
            + "<if test='eventId != null'>AND p.event_id = #{eventId} </if>"
            + "<if test='unitId != null'>AND p.unit_id = #{unitId} </if>";

    /**
     * 积分明细分页查询（手工 LIMIT/OFFSET，避免 {@code @Select} 与 MyBatis-Plus 分页拦截器叠加）。
     *
     * @param meetId   届次 ID
     * @param groupId  组别 ID（可空）
     * @param eventId  项目 ID（可空）
     * @param unitId   单位 ID（可空）
     * @param pageSize 每页条数
     * @param offset   偏移量
     * @return 积分明细投影列表
     */
    @Select("<script>" + POINT_SELECT
            + "ORDER BY p.event_id ASC, p.round_id ASC, p.rank_no ASC, p.id ASC "
            + "LIMIT #{pageSize} OFFSET #{offset}"
            + "</script>")
    List<StandingPointRow> selectPointPage(@Param("meetId") Long meetId,
                                           @Param("groupId") Long groupId,
                                           @Param("eventId") Long eventId,
                                           @Param("unitId") Long unitId,
                                           @Param("pageSize") long pageSize,
                                           @Param("offset") long offset);

    /**
     * 积分明细总数（与 {@link #selectPointPage} 条件严格一致）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @param eventId 项目 ID（可空）
     * @param unitId  单位 ID（可空）
     * @return 总条数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM points p "
            + "WHERE p.deleted = 0 AND p.meet_id = #{meetId} "
            + "<if test='groupId != null'>AND p.group_id = #{groupId} </if>"
            + "<if test='eventId != null'>AND p.event_id = #{eventId} </if>"
            + "<if test='unitId != null'>AND p.unit_id = #{unitId} </if>"
            + "</script>")
    long countPoints(@Param("meetId") Long meetId,
                     @Param("groupId") Long groupId,
                     @Param("eventId") Long eventId,
                     @Param("unitId") Long unitId);

    /**
     * 积分明细全量查询（打印积分明细表，不分页）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @param eventId 项目 ID（可空）
     * @param unitId  单位 ID（可空）
     * @return 积分明细投影列表
     */
    @Select("<script>" + POINT_SELECT
            + "ORDER BY p.event_id ASC, p.round_id ASC, p.rank_no ASC, p.id ASC"
            + "</script>")
    List<StandingPointRow> selectPointList(@Param("meetId") Long meetId,
                                           @Param("groupId") Long groupId,
                                           @Param("eventId") Long eventId,
                                           @Param("unitId") Long unitId);

    // ==================================================================================
    // 五、证书
    // ==================================================================================

    /**
     * 证书分页查询（联运动员 / 单位 / 项目 / 轮次 / 组别名）。
     *
     * @param meetId  届次 ID
     * @param certType 证书类型（可空）
     * @param unitId  单位 ID（可空）
     * @param keyword 关键字（证书编号 / 荣誉描述 / 运动员姓名 / 单位名称，可空）
     * @param pageSize 每页条数
     * @param offset   偏移量
     * @return 证书投影列表
     */
    @Select("<script>"
            + "SELECT c.id AS id, c.meet_id AS meetId, c.cert_no AS certNo, c.cert_type AS certType, "
            + "c.template_id AS templateId, c.athlete_id AS athleteId, c.unit_id AS unitId, "
            + "c.event_id AS eventId, c.round_id AS roundId, c.result_id AS resultId, "
            + "c.rank_no AS rankNo, c.rank_display AS rankDisplay, c.result_text AS resultText, "
            + "c.honor_text AS honorText, c.issue_date AS issueDate, c.issuer AS issuer, "
            + "c.file_id AS fileId, c.qr_content AS qrContent, c.status AS status, "
            + "c.print_count AS printCount, c.last_print_time AS lastPrintTime, c.remark AS remark, "
            + "c.create_time AS createTime, "
            + "a.athlete_name AS athleteName, a.bib_no AS bibNo, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, "
            + "e.event_name AS eventName, e.event_code AS eventCode, "
            + "rd.round_name AS roundName, g.group_name AS groupName "
            + "FROM certificate c "
            + "LEFT JOIN athlete a ON a.id = c.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = c.unit_id AND u.deleted = 0 "
            + "LEFT JOIN event e ON e.id = c.event_id AND e.deleted = 0 "
            + "LEFT JOIN event_round rd ON rd.id = c.round_id AND rd.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = e.group_id AND g.deleted = 0 "
            + "WHERE c.deleted = 0 AND c.meet_id = #{meetId} "
            + "<if test='certType != null and certType != \"\"'>AND c.cert_type = #{certType} </if>"
            + "<if test='unitId != null'>AND c.unit_id = #{unitId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (c.cert_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR c.honor_text LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.athlete_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.unit_name LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "ORDER BY c.cert_type ASC, c.event_id ASC, c.rank_no ASC, c.id ASC "
            + "LIMIT #{pageSize} OFFSET #{offset}"
            + "</script>")
    List<StandingCertificateRow> selectCertificatePage(@Param("meetId") Long meetId,
                                                       @Param("certType") String certType,
                                                       @Param("unitId") Long unitId,
                                                       @Param("keyword") String keyword,
                                                       @Param("pageSize") long pageSize,
                                                       @Param("offset") long offset);

    /**
     * 证书总数（与 {@link #selectCertificatePage} 条件严格一致）。
     *
     * @param meetId  届次 ID
     * @param certType 证书类型（可空）
     * @param unitId  单位 ID（可空）
     * @param keyword 关键字（可空）
     * @return 总条数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM certificate c "
            + "LEFT JOIN athlete a ON a.id = c.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = c.unit_id AND u.deleted = 0 "
            + "LEFT JOIN event e ON e.id = c.event_id AND e.deleted = 0 "
            + "WHERE c.deleted = 0 AND c.meet_id = #{meetId} "
            + "<if test='certType != null and certType != \"\"'>AND c.cert_type = #{certType} </if>"
            + "<if test='unitId != null'>AND c.unit_id = #{unitId} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (c.cert_no LIKE CONCAT('%', #{keyword}, '%') "
            + "OR c.honor_text LIKE CONCAT('%', #{keyword}, '%') "
            + "OR a.athlete_name LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.unit_name LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "</script>")
    long countCertificates(@Param("meetId") Long meetId,
                           @Param("certType") String certType,
                           @Param("unitId") Long unitId,
                           @Param("keyword") String keyword);

    /**
     * 按证书 ID 查询证书明细（导出下载用）。
     * <p>
     * 手动拼 {@code IN} 以避免依赖 MyBatis-Plus 的 {@code selectBatchIds}（本方法需要联表列）。
     *
     * @param meetId 届次 ID
     * @param ids    证书 ID 列表（调用方保证非空）
     * @return 证书投影列表
     */
    @Select("<script>"
            + "SELECT c.id AS id, c.meet_id AS meetId, c.cert_no AS certNo, c.cert_type AS certType, "
            + "c.template_id AS templateId, c.athlete_id AS athleteId, c.unit_id AS unitId, "
            + "c.event_id AS eventId, c.round_id AS roundId, c.result_id AS resultId, "
            + "c.rank_no AS rankNo, c.rank_display AS rankDisplay, c.result_text AS resultText, "
            + "c.honor_text AS honorText, c.issue_date AS issueDate, c.issuer AS issuer, "
            + "c.file_id AS fileId, c.qr_content AS qrContent, c.status AS status, "
            + "c.print_count AS printCount, c.last_print_time AS lastPrintTime, c.remark AS remark, "
            + "c.create_time AS createTime, "
            + "a.athlete_name AS athleteName, a.bib_no AS bibNo, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, "
            + "e.event_name AS eventName, e.event_code AS eventCode, "
            + "rd.round_name AS roundName, g.group_name AS groupName "
            + "FROM certificate c "
            + "LEFT JOIN athlete a ON a.id = c.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = c.unit_id AND u.deleted = 0 "
            + "LEFT JOIN event e ON e.id = c.event_id AND e.deleted = 0 "
            + "LEFT JOIN event_round rd ON rd.id = c.round_id AND rd.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = e.group_id AND g.deleted = 0 "
            + "WHERE c.deleted = 0 AND c.meet_id = #{meetId} AND c.id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY c.cert_type ASC, c.event_id ASC, c.rank_no ASC, c.id ASC"
            + "</script>")
    List<StandingCertificateRow> selectCertificatesByIds(@Param("meetId") Long meetId,
                                                         @Param("ids") List<Long> ids);

    /**
     * 统计某证书编号前缀在库内的条数（含逻辑删除行，因 {@code uk_cert_no} 不含 {@code deleted}）。
     * <p>
     * 用于生成唯一 {@code cert_no}：新序号 = 该前缀既有条数 + 1。
     *
     * @param meetId 届次 ID
     * @param prefix 证书编号前缀
     * @return 既有条数
     */
    @Select("SELECT COUNT(*) FROM certificate WHERE meet_id = #{meetId} AND cert_no LIKE CONCAT(#{prefix}, '%')")
    long countCertNoByPrefix(@Param("meetId") Long meetId, @Param("prefix") String prefix);

    /**
     * 打印计数累加（只累加未被逻辑删除的记录）。
     *
     * @param meetId      届次 ID
     * @param ids         证书 ID 列表（调用方保证非空）
     * @param printTime   打印时间
     * @return 受影响行数
     */
    @Update("<script>"
            + "UPDATE certificate SET print_count = IFNULL(print_count, 0) + 1, last_print_time = #{printTime} "
            + "WHERE deleted = 0 AND meet_id = #{meetId} AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int increasePrintCount(@Param("meetId") Long meetId,
                           @Param("ids") List<Long> ids,
                           @Param("printTime") LocalDateTime printTime);

    // ==================================================================================
    // 六、幂等重建：物理删除
    // ==================================================================================

    /**
     * 物理删除指定范围内的积分明细。
     * <p>
     * {@code points} 唯一键不含 {@code deleted}，必须物理删除以避免唯一键冲突。
     *
     * @param meetId  届次 ID
     * @param eventId 项目 ID（可空）
     * @param groupId 组别 ID（可空）
     * @return 删除行数
     */
    @Delete("<script>"
            + "DELETE FROM points WHERE meet_id = #{meetId} "
            + "<if test='eventId != null'>AND event_id = #{eventId} </if>"
            + "<if test='groupId != null'>AND group_id = #{groupId} </if>"
            + "</script>")
    int deletePoints(@Param("meetId") Long meetId,
                     @Param("eventId") Long eventId,
                     @Param("groupId") Long groupId);

    /**
     * 物理删除指定范围内的团体总分快照。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @return 删除行数
     */
    @Delete("<script>"
            + "DELETE FROM team_score WHERE meet_id = #{meetId} "
            + "<if test='groupId != null'>AND group_id = #{groupId} </if>"
            + "</script>")
    int deleteTeamScores(@Param("meetId") Long meetId, @Param("groupId") Long groupId);

    /**
     * 物理删除指定范围内的奖牌榜快照。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @return 删除行数
     */
    @Delete("<script>"
            + "DELETE FROM medal_standing WHERE meet_id = #{meetId} "
            + "<if test='groupId != null'>AND group_id = #{groupId} </if>"
            + "</script>")
    int deleteMedalStandings(@Param("meetId") Long meetId, @Param("groupId") Long groupId);

    // ==================================================================================
    // 六、幂等重建：批量插入
    // ==================================================================================

    /**
     * 批量插入积分明细（重算时整批写入，避免逐行 insert 的往返开销）。
     *
     * @param list 积分明细实体列表（调用方保证非空）
     * @return 插入行数
     */
    @Insert("<script>"
            + "INSERT INTO points (meet_id, event_id, round_id, result_id, registration_id, athlete_id, unit_id, "
            + "group_id, gender, rank_no, rank_point, bonus_point, relay_factor, total_point, score_rule_id, "
            + "score_rule_code, bonus_rule_ids, calc_time, is_manual, adjust_reason, remark, "
            + "create_time, update_time, deleted, version) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.meetId}, #{item.eventId}, #{item.roundId}, #{item.resultId}, #{item.registrationId}, "
            + "#{item.athleteId}, #{item.unitId}, #{item.groupId}, #{item.gender}, #{item.rankNo}, "
            + "#{item.rankPoint}, #{item.bonusPoint}, #{item.relayFactor}, #{item.totalPoint}, "
            + "#{item.scoreRuleId}, #{item.scoreRuleCode}, #{item.bonusRuleIds}, #{item.calcTime}, "
            + "#{item.isManual}, #{item.adjustReason}, #{item.remark}, "
            + "NOW(), NOW(), 0, 0)"
            + "</foreach>"
            + "</script>")
    int batchInsertPoints(@Param("list") List<Points> list);

    /**
     * 批量插入团体总分快照。
     *
     * @param list 团体总分实体列表（调用方保证非空）
     * @return 插入行数
     */
    @Insert("<script>"
            + "INSERT INTO team_score (meet_id, unit_id, group_id, score_type, rank_point_sum, bonus_point_sum, "
            + "relay_point_sum, total_point, rank_no, rank_display, event_count, athlete_count, calc_time, "
            + "calc_batch, is_published, remark, create_time, update_time, deleted, version) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.meetId}, #{item.unitId}, #{item.groupId}, #{item.scoreType}, #{item.rankPointSum}, "
            + "#{item.bonusPointSum}, #{item.relayPointSum}, #{item.totalPoint}, #{item.rankNo}, "
            + "#{item.rankDisplay}, #{item.eventCount}, #{item.athleteCount}, #{item.calcTime}, "
            + "#{item.calcBatch}, 0, #{item.remark}, NOW(), NOW(), 0, 0)"
            + "</foreach>"
            + "</script>")
    int batchInsertTeamScores(@Param("list") List<TeamScore> list);

    /**
     * 批量插入奖牌榜快照。
     *
     * @param list 奖牌榜实体列表（调用方保证非空）
     * @return 插入行数
     */
    @Insert("<script>"
            + "INSERT INTO medal_standing (meet_id, unit_id, group_id, gold, silver, bronze, total_medal, "
            + "sort_score, medal_sort, rank_no, rank_display, calc_time, calc_batch, is_published, remark, "
            + "create_time, update_time, deleted, version) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.meetId}, #{item.unitId}, #{item.groupId}, #{item.gold}, #{item.silver}, #{item.bronze}, "
            + "#{item.totalMedal}, #{item.sortScore}, #{item.medalSort}, #{item.rankNo}, #{item.rankDisplay}, "
            + "#{item.calcTime}, #{item.calcBatch}, 0, #{item.remark}, NOW(), NOW(), 0, 0)"
            + "</foreach>"
            + "</script>")
    int batchInsertMedalStandings(@Param("list") List<MedalStanding> list);

    // ==================================================================================
    // 七、快照保存：只刷新 calc_time / calc_batch
    // ==================================================================================
    /**
     * 刷新团体总分快照的计算时间与批次号。
     *
     * @param meetId    届次 ID
     * @param scoreType 团体口径（可空，空为三档全部）
     * @param calcTime  计算时间
     * @param calcBatch 计算批次号
     * @return 受影响行数
     */
    @Update("<script>"
            + "UPDATE team_score SET calc_time = #{calcTime}, calc_batch = #{calcBatch} "
            + "WHERE deleted = 0 AND meet_id = #{meetId} "
            + "<if test='scoreType != null and scoreType != \"\"'>AND score_type = #{scoreType} </if>"
            + "</script>")
    int updateTeamScoreSnapshot(@Param("meetId") Long meetId,
                                @Param("scoreType") String scoreType,
                                @Param("calcTime") LocalDateTime calcTime,
                                @Param("calcBatch") String calcBatch);

    /**
     * 刷新奖牌榜快照的计算时间与批次号。
     *
     * @param meetId    届次 ID
     * @param calcTime  计算时间
     * @param calcBatch 计算批次号
     * @return 受影响行数
     */
    @Update("UPDATE medal_standing SET calc_time = #{calcTime}, calc_batch = #{calcBatch} "
            + "WHERE deleted = 0 AND meet_id = #{meetId}")
    int updateMedalSnapshot(@Param("meetId") Long meetId,
                            @Param("calcTime") LocalDateTime calcTime,
                            @Param("calcBatch") String calcBatch);
}
