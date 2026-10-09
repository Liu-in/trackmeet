package com.trackmeet.modules.standing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.standing.dto.PointQuery;
import com.trackmeet.modules.standing.dto.StandingPointRow;
import com.trackmeet.modules.standing.entity.Points;
import com.trackmeet.modules.standing.mapper.StandingMapper;
import com.trackmeet.modules.standing.service.PointsService;
import com.trackmeet.modules.standing.vo.PointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 积分明细服务实现：{@code points} 的下钻查询与打印取数。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    /** 每页条数上限（与单页最大导出量保持一致） */
    private static final long MAX_PAGE_SIZE = 200L;

    private final StandingMapper standingMapper;

    @Override
    public PageResult<PointVO> pagePoints(PointQuery query) {
        if (query == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "查询条件不能为空");
        }
        Long meetId = query.getMeetId();
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        long pageNo = query.pageNo();
        long pageSize = Math.min(query.pageSize(), MAX_PAGE_SIZE);
        long total = standingMapper.countPoints(meetId, query.getGroupId(), query.getEventId(), query.getUnitId());
        if (total <= 0) {
            return PageResult.empty(pageNo, pageSize);
        }
        long offset = (pageNo - 1) * pageSize;
        List<StandingPointRow> rows = standingMapper.selectPointPage(meetId, query.getGroupId(),
                query.getEventId(), query.getUnitId(), pageSize, offset);
        List<PointVO> records = new ArrayList<>(rows.size());
        for (StandingPointRow row : rows) {
            records.add(toVO(row));
        }
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Override
    public List<StandingPointRow> listPointRows(Long meetId, Long groupId, Long eventId, Long unitId) {
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        return standingMapper.selectPointList(meetId, groupId, eventId, unitId);
    }

    @Override
    public PointVO toVO(StandingPointRow row) {
        PointVO vo = new PointVO();
        if (row == null) {
            return vo;
        }
        vo.setId(row.getId());
        vo.setMeetId(row.getMeetId());
        vo.setEventId(row.getEventId());
        vo.setEventName(row.getEventName());
        vo.setEventCode(row.getEventCode());
        vo.setEventType(row.getEventType());
        vo.setRelay(row.getRelay());
        vo.setRoundId(row.getRoundId());
        vo.setRoundCode(row.getRoundCode());
        vo.setRoundName(row.getRoundName());
        vo.setResultId(row.getResultId());
        vo.setRegistrationId(row.getRegistrationId());
        vo.setAthleteId(row.getAthleteId());
        vo.setAthleteName(StringUtils.hasText(row.getAthleteName()) ? row.getAthleteName() : row.getTeamName());
        vo.setBibNo(row.getBibNo());
        vo.setTeamName(row.getTeamName());
        vo.setUnitId(row.getUnitId());
        vo.setUnitName(row.getUnitName());
        vo.setUnitShortName(row.getUnitShortName());
        vo.setGroupId(row.getGroupId());
        vo.setGroupName(row.getGroupName());
        vo.setGender(row.getGender());
        vo.setRankNo(row.getRankNo());
        vo.setRankDisplay(row.getRankDisplay() == null && row.getRankNo() != null
                ? String.valueOf(row.getRankNo()) : row.getRankDisplay());
        vo.setRankPoint(row.getRankPoint());
        vo.setBonusPoint(row.getBonusPoint());
        vo.setRelayFactor(row.getRelayFactor());
        vo.setTotalPoint(row.getTotalPoint());
        vo.setScoreRuleId(row.getScoreRuleId());
        vo.setScoreRuleCode(row.getScoreRuleCode());
        vo.setBonusRuleIds(row.getBonusRuleIds());
        vo.setCalcTime(row.getCalcTime());
        vo.setIsManual(row.getIsManual());
        vo.setAdjustReason(row.getAdjustReason());
        return vo;
    }

    /**
     * 便捷查询：按届次读取全部积分明细（供榜单服务内部汇总使用，不校验分页）。
     *
     * @param meetId 届次 ID
     * @return 积分实体列表
     */
    public List<Points> listAll(Long meetId) {
        return list(new LambdaQueryWrapper<Points>().eq(Points::getMeetId, meetId));
    }
}
