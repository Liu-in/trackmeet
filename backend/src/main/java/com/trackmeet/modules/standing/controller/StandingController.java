package com.trackmeet.modules.standing.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.standing.dto.PointQuery;
import com.trackmeet.modules.standing.dto.StandingRecalculateDTO;
import com.trackmeet.modules.standing.dto.StandingQuery;
import com.trackmeet.modules.standing.dto.StandingSnapshotDTO;
import com.trackmeet.modules.standing.service.PointsService;
import com.trackmeet.modules.standing.service.StandingService;
import com.trackmeet.modules.standing.vo.MedalVO;
import com.trackmeet.modules.standing.vo.PointVO;
import com.trackmeet.modules.standing.vo.StandingOverviewVO;
import com.trackmeet.modules.standing.vo.TeamScoreVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计与积分接口：团体总分、奖牌榜、积分明细与重算 / 快照。
 * <p>
 * 权限编码（均已在 {@code sql/05-init-data.sql} 初始化）：
 * <ul>
 *     <li>{@code GET /api/standings/team} → {@code standing:points}（任务书口径；文档 §9 的
 *         {@code standing:team} 未在初始化数据中定义）；</li>
 *     <li>{@code GET /api/standings/medals} → {@code standing:medal}；</li>
 *     <li>{@code GET /api/standings/points} → {@code standing:points}；</li>
 *     <li>{@code POST /api/standings/recalculate} → {@code standing:recalc}；</li>
 *     <li>{@code POST /api/standings/snapshot} → {@code standing:snapshot}。</li>
 * </ul>
 * 榜单查询只读快照，快照为空时由服务层实时计算并落库，避免每次拉全量成绩实时聚合。
 *
 * @author TrackMeet
 */
@Slf4j
@RestController
@RequestMapping("/api/standings")
@RequiredArgsConstructor
@Validated
public class StandingController {

    private final StandingService standingService;
    private final PointsService pointsService;

    /**
     * 团体总分（读 {@code team_score} 快照；为空时实时计算并落库）。
     *
     * @param query 查询条件（meetId 必填；groupId；scoreType=MALE|FEMALE|TOTAL）
     * @return 团体总分列表
     */
    @GetMapping("/team")
    @PreAuthorize("@perm.has('standing:points')")
    public R<List<TeamScoreVO>> team(@Valid StandingQuery query) {
        return R.ok(standingService.listTeamScores(query));
    }

    /**
     * 奖牌榜（读 {@code medal_standing} 快照；为空时实时计算并落库）。
     *
     * @param query 查询条件（meetId 必填；groupId）
     * @return 奖牌榜列表
     */
    @GetMapping("/medals")
    @PreAuthorize("@perm.has('standing:medal')")
    public R<List<MedalVO>> medals(@Valid StandingQuery query) {
        return R.ok(standingService.listMedals(query));
    }

    /**
     * 积分明细下钻（分页）。
     *
     * @param query 查询条件（meetId 必填；groupId / unitId / eventId；page / size）
     * @return 分页结果
     */
    @GetMapping("/points")
    @PreAuthorize("@perm.has('standing:points')")
    public R<PageResult<PointVO>> points(@Valid PointQuery query) {
        return R.ok(pointsService.pagePoints(query));
    }

    /**
     * 重算积分与榜单（幂等）。
     * <p>
     * 装配已审核（{@code APPROVED}）成绩 → {@code ScoreCalculator} → 按范围物理删除并重建
     * {@code points} → {@code StandingCalculator} 重建 {@code team_score} 与 {@code medal_standing}。
     *
     * @param dto 重算入参（meetId 必填；groupId / eventId 可空表示全量）
     * @return 重算统计与两张榜单
     */
    @PostMapping("/recalculate")
    @PreAuthorize("@perm.has('standing:recalc')")
    @OpLog(module = "STANDING", action = "RECALC", actionName = "重算积分与榜单", targetType = "points")
    public R<StandingOverviewVO> recalculate(@Valid @RequestBody StandingRecalculateDTO dto) {
        return R.ok(standingService.recalculate(dto));
    }

    /**
     * 保存榜单快照：刷新 {@code team_score} / {@code medal_standing} 的 {@code calc_time} 与
     * {@code calc_batch}（不重算分值）。
     *
     * @param dto 快照入参（meetId 必填；scoreType 可空表示三档全部）
     * @return 快照统计
     */
    @PostMapping("/snapshot")
    @PreAuthorize("@perm.has('standing:snapshot')")
    @OpLog(module = "STANDING", action = "SNAPSHOT", actionName = "保存榜单快照", targetType = "team_score")
    public R<StandingOverviewVO> snapshot(@Valid @RequestBody StandingSnapshotDTO dto) {
        return R.ok(standingService.snapshot(dto));
    }
}
