package com.trackmeet.modules.standing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.standing.dto.PointQuery;
import com.trackmeet.modules.standing.dto.StandingPointRow;
import com.trackmeet.modules.standing.entity.Points;
import com.trackmeet.modules.standing.vo.PointVO;

import java.util.List;

/**
 * 积分明细服务：{@code points} 的下钻查询与打印取数。
 * <p>
 * 写入（重算重建）由 {@link StandingService#recalculate} 统一编排，保证
 * {@code points} / {@code team_score} / {@code medal_standing} 在同一事务内重建。
 *
 * @author TrackMeet
 */
public interface PointsService {

    /**
     * 积分明细分页查询（积分下钻）。
     *
     * @param query 查询条件（meetId 必填）
     * @return 分页结果
     */
    PageResult<PointVO> pagePoints(PointQuery query);

    /**
     * 积分明细全量查询（积分明细表打印，不分页）。
     *
     * @param meetId  届次 ID
     * @param groupId 组别 ID（可空）
     * @param eventId 项目 ID（可空）
     * @param unitId  单位 ID（可空）
     * @return 积分明细投影列表
     */
    List<StandingPointRow> listPointRows(Long meetId, Long groupId, Long eventId, Long unitId);

    /**
     * 投影行 → 积分明细 VO。
     *
     * @param row 投影行
     * @return 积分明细 VO
     */
    PointVO toVO(StandingPointRow row);
}
