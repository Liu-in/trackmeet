package com.trackmeet.modules.announcement.mapper;

import com.trackmeet.modules.announcement.dto.ScreenBoardBaseRow;
import com.trackmeet.modules.announcement.dto.ScreenMedalRow;
import com.trackmeet.modules.announcement.dto.ScreenResultRow;
import com.trackmeet.modules.announcement.dto.ScreenTeamScoreRow;
import com.trackmeet.modules.announcement.entity.ScreenConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 大屏配置与大屏聚合取数 Mapper。
 * <p>
 * 除配置行本身（{@link #selectActiveConfig}）外，全部为只读 {@code @Select} 投影：
 * <ol>
 *     <li><b>不引用其它模块的实体</b>：联表只出现在 SQL 中，返回本模块 DTO
 *         （{@code meet} / {@code unit} / {@code meet_group} / {@code event} / {@code event_round} /
 *         {@code athlete} 均按列取值，不引入其它模块的 Java 类型）；</li>
 *     <li><b>自带租户与逻辑删除条件</b>：主表显式带 {@code meet_id = #{meetId}} 与 {@code deleted = 0}，
 *         联表的 {@code deleted = 0} 写在 {@code ON} 子句；</li>
 *     <li><b>一次大屏请求共 5 条查询</b>：
 *         {@link #selectBoardBase}（届次 + 配置，1 条）、
 *         {@code AnnouncementMapper#selectScreenAnnouncements}（1 条）、
 *         {@link #selectTopMedals}（1 条）、{@link #selectTopTeamScores}（1 条）、
 *         {@link #selectLatestResults}（1 条）。</li>
 * </ol>
 * <b>关于大屏访问令牌</b>：{@code screen_config} 的建表脚本中没有 {@code access_token} 列，
 * 为守住「实体列与 DDL 逐字一致」，令牌持久化在 {@code theme_json} 的保留键 {@code accessToken} 上
 * （写入与解析见 {@code ScreenServiceImpl}）。待 SQL 增列后可平滑切换为独立列。
 *
 * @author TrackMeet
 */
@Mapper
public interface ScreenConfigMapper extends BaseMapper<ScreenConfig> {

    /**
     * 读取某届次的活动大屏配置（默认方案优先，其次主键最小）。
     *
     * @param meetId 届次 ID
     * @return 大屏配置实体，无配置时返回 {@code null}
     */
    @Select("SELECT c.* FROM screen_config c WHERE c.deleted = 0 AND c.meet_id = #{meetId} "
            + "ORDER BY c.is_default DESC, c.id ASC LIMIT 1")
    ScreenConfig selectActiveConfig(@Param("meetId") Long meetId);

    /**
     * 大屏基础投影：届次信息 + 该届次的活动大屏配置（一条 SQL 取出，控制总查询条数）。
     * <p>
     * 配置行通过 {@code ON c.id = (子查询 LIMIT 1)} 关联，取「默认方案优先、主键最小」的那一行；
     * 无配置时配置列全为 {@code null}，服务层回落到默认配置（轮播 15 秒、展示模块全开）。
     *
     * @param meetId 届次 ID
     * @return 届次 + 配置投影，届次不存在时返回 {@code null}
     */
    @Select("SELECT m.id AS meetId, m.meet_name AS meetName, m.short_name AS meetShortName, "
            + "m.edition AS edition, m.place AS venue, m.start_date AS startDate, m.end_date AS endDate, "
            + "m.status AS meetStatus, "
            + "c.id AS configId, c.screen_name AS screenName, c.screen_code AS screenCode, "
            + "c.modules_json AS modulesJson, c.refresh_interval AS refreshInterval, "
            + "c.theme_json AS themeJson, c.resolution AS resolution, c.rotate_seconds AS rotateSeconds, "
            + "c.play_mode AS playMode, c.status AS configStatus, c.is_default AS isDefault, "
            + "c.remark AS configRemark "
            + "FROM meet m "
            + "LEFT JOIN screen_config c ON c.id = (SELECT c2.id FROM screen_config c2 "
            + "WHERE c2.meet_id = #{meetId} AND c2.deleted = 0 "
            + "ORDER BY c2.is_default DESC, c2.id ASC LIMIT 1) "
            + "WHERE m.deleted = 0 AND m.id = #{meetId}")
    ScreenBoardBaseRow selectBoardBase(@Param("meetId") Long meetId);

    /**
     * 奖牌榜前 N（{@code sort_score} 降序，联单位与组别名）。
     *
     * @param meetId 届次 ID
     * @param limit  最大条数
     * @return 奖牌榜投影列表
     */
    @Select("SELECT m.id AS id, m.unit_id AS unitId, m.group_id AS groupId, m.gold AS gold, "
            + "m.silver AS silver, m.bronze AS bronze, m.total_medal AS totalMedal, "
            + "m.sort_score AS sortScore, m.medal_sort AS medalSort, m.rank_no AS rankNo, "
            + "m.rank_display AS rankDisplay, m.calc_time AS calcTime, "
            + "m.is_published AS isPublished, m.remark AS remark, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, g.group_name AS groupName "
            + "FROM medal_standing m "
            + "LEFT JOIN unit u ON u.id = m.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = m.group_id AND g.deleted = 0 "
            + "WHERE m.deleted = 0 AND m.meet_id = #{meetId} "
            + "ORDER BY m.sort_score DESC, m.gold DESC, m.total_medal DESC, m.id ASC "
            + "LIMIT #{limit}")
    List<ScreenMedalRow> selectTopMedals(@Param("meetId") Long meetId, @Param("limit") int limit);

    /**
     * 团体总分（总团体）前 N：仅取 {@code score_type = 'TOTAL'}，按 {@code total_point} 降序。
     *
     * @param meetId 届次 ID
     * @param limit  最大条数
     * @return 团体总分投影列表
     */
    @Select("SELECT t.id AS id, t.unit_id AS unitId, t.group_id AS groupId, t.score_type AS scoreType, "
            + "t.rank_point_sum AS rankPointSum, t.bonus_point_sum AS bonusPointSum, "
            + "t.relay_point_sum AS relayPointSum, t.total_point AS totalPoint, t.rank_no AS rankNo, "
            + "t.rank_display AS rankDisplay, t.event_count AS eventCount, "
            + "t.athlete_count AS athleteCount, t.calc_time AS calcTime, t.is_published AS isPublished, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, g.group_name AS groupName "
            + "FROM team_score t "
            + "LEFT JOIN unit u ON u.id = t.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = t.group_id AND g.deleted = 0 "
            + "WHERE t.deleted = 0 AND t.meet_id = #{meetId} AND t.score_type = 'TOTAL' "
            + "ORDER BY t.total_point DESC, t.rank_no ASC, t.id ASC "
            + "LIMIT #{limit}")
    List<ScreenTeamScoreRow> selectTopTeamScores(@Param("meetId") Long meetId, @Param("limit") int limit);

    /**
     * 最新成绩前 N：已审核（{@code APPROVED}）且有效（{@code VALID}），按 {@code update_time} 降序。
     *
     * @param meetId 届次 ID
     * @param limit  最大条数
     * @return 成绩投影列表（含项目 / 轮次 / 运动员 / 单位 / 组别名）
     */
    @Select("SELECT r.id AS resultId, r.meet_id AS meetId, r.event_id AS eventId, r.round_id AS roundId, "
            + "r.athlete_id AS athleteId, r.unit_id AS unitId, r.group_id AS groupId, "
            + "r.result_raw AS resultRaw, r.result_value AS resultValue, r.result_unit AS resultUnit, "
            + "r.rank_no AS rankNo, r.rank_display AS rankDisplay, r.status AS status, "
            + "r.wind_speed AS windSpeed, r.is_record_confirmed AS isRecordConfirmed, "
            + "r.update_time AS updateTime, "
            + "e.event_name AS eventName, e.event_code AS eventCode, e.event_type AS eventType, "
            + "e.gender AS gender, "
            + "rd.round_name AS roundName, rd.round_no AS roundNo, "
            + "a.athlete_name AS athleteName, a.bib_no AS bibNo, "
            + "u.unit_name AS unitName, u.short_name AS unitShortName, g.group_name AS groupName "
            + "FROM result r "
            + "JOIN event e ON e.id = r.event_id AND e.deleted = 0 "
            + "LEFT JOIN event_round rd ON rd.id = r.round_id AND rd.deleted = 0 "
            + "LEFT JOIN athlete a ON a.id = r.athlete_id AND a.deleted = 0 "
            + "LEFT JOIN unit u ON u.id = r.unit_id AND u.deleted = 0 "
            + "LEFT JOIN meet_group g ON g.id = r.group_id AND g.deleted = 0 "
            + "WHERE r.deleted = 0 AND r.meet_id = #{meetId} "
            + "AND r.audit_status = 'APPROVED' AND r.status = 'VALID' "
            + "ORDER BY r.update_time DESC, r.id DESC "
            + "LIMIT #{limit}")
    List<ScreenResultRow> selectLatestResults(@Param("meetId") Long meetId, @Param("limit") int limit);
}
