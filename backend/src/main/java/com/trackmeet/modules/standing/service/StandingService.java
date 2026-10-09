package com.trackmeet.modules.standing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.standing.dto.StandingRecalculateDTO;
import com.trackmeet.modules.standing.dto.StandingSnapshotDTO;
import com.trackmeet.modules.standing.dto.StandingQuery;
import com.trackmeet.modules.standing.entity.TeamScore;
import com.trackmeet.modules.standing.vo.MedalVO;
import com.trackmeet.modules.standing.vo.StandingOverviewVO;
import com.trackmeet.modules.standing.vo.TeamScoreVO;

import java.util.List;

/**
 * 榜单服务：团体总分与奖牌榜的查询、重算与快照。
 * <p>
 * 计算全部委托给 {@code com.trackmeet.engine.score.ScoreCalculator} 与
 * {@code com.trackmeet.engine.score.StandingCalculator}，本服务只负责
 * 「读库装配 → 调引擎 → 幂等落库」。
 *
 * @author TrackMeet
 */
public interface StandingService {

    /**
     * 团体总分歧（读 {@code team_score} 快照；快照为空时实时计算并落库）。
     *
     * @param query 查询条件（meetId 必填，scoreType 取 MALE / FEMALE / TOTAL）
     * @return 团体总分列表
     */
    List<TeamScoreVO> listTeamScores(StandingQuery query);

    /**
     * 奖牌榜（读 {@code medal_standing} 快照；快照为空时实时计算并落库）。
     *
     * @param query 查询条件（meetId 必填）
     * @return 奖牌榜列表
     */
    List<MedalVO> listMedals(StandingQuery query);

    /**
     * 重算积分与榜单（幂等）。
     * <p>
     * 流程：装配已审核（{@code APPROVED}）成绩 → {@code ScoreCalculator} 计算积分 →
     * <b>按范围物理删除</b>后重建 {@code points} → {@code StandingCalculator} 重建
     * {@code team_score} 与 {@code medal_standing}。
     *
     * @param dto 重算入参（meetId 必填，groupId / eventId 可空表示全量）
     * @return 重算统计与两张榜单
     */
    StandingOverviewVO recalculate(StandingRecalculateDTO dto);

    /**
     * 保存榜单快照：刷新 {@code team_score} / {@code medal_standing} 的
     * {@code calc_time} 与 {@code calc_batch}（不重算分值）。
     *
     * @param dto 快照入参（meetId 必填，scoreType 可空表示三档全部）
     * @return 快照统计
     */
    StandingOverviewVO snapshot(StandingSnapshotDTO dto);

    /**
     * 投影行 → 团体总分 VO。
     *
     * @param row 投影行
     * @return 团体总分 VO
     */
    TeamScoreVO toTeamScoreVO(com.trackmeet.modules.standing.dto.StandingTeamScoreRow row);

    /**
     * 投影行 → 奖牌榜 VO。
     *
     * @param row 投影行
     * @return 奖牌榜 VO
     */
    MedalVO toMedalVO(com.trackmeet.modules.standing.dto.StandingMedalRow row);
}
