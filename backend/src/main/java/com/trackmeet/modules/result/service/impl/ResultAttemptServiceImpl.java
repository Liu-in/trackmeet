package com.trackmeet.modules.result.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.result.dto.ResultAttemptDTO;
import com.trackmeet.modules.result.entity.Result;
import com.trackmeet.modules.result.entity.ResultAttempt;
import com.trackmeet.modules.result.mapper.ResultAttemptMapper;
import com.trackmeet.modules.result.mapper.ResultMapper;
import com.trackmeet.modules.result.service.ResultAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 田赛试跳试掷服务实现。
 * <p>
 * 数据落点：{@code result_attempt}；试跳序号在同一成绩内唯一（对应 {@code uk_result_attempt}）。
 * 写入后统一回写「最好一次」标记（{@code is_best}）与所属成绩的最好成绩（{@code result.result_value}），
 * 保证成绩双列的数值列与试跳明细始终一致。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class ResultAttemptServiceImpl extends ServiceImpl<ResultAttemptMapper, ResultAttempt> implements ResultAttemptService {

    private final ResultAttemptMapper resultAttemptMapper;

    private final ResultMapper resultMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAttempt(ResultAttemptDTO dto) {
        if (dto == null || dto.getResultId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属成绩不能为空");
        }
        if (dto.getAttemptNo() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "试跳序号不能为空");
        }
        Long meetId = currentMeetId();
        Result result = requireResult(meetId, dto.getResultId());
        checkNotLocked(result);

        // 同成绩内同试跳序号：覆盖更新，避免唯一键冲突
        ResultAttempt exists = resultAttemptMapper.selectOne(Wrappers.<ResultAttempt>lambdaQuery()
                .eq(ResultAttempt::getMeetId, meetId)
                .eq(ResultAttempt::getResultId, dto.getResultId())
                .eq(ResultAttempt::getAttemptNo, dto.getAttemptNo())
                .last("LIMIT 1"));
        ResultAttempt entity = buildAttempt(meetId, result, dto);
        if (exists != null) {
            entity.setId(exists.getId());
            if (!updateById(entity)) {
                throw new BizException(ResultCode.CONFLICT, "试跳成绩已被他人修改，请刷新后重试");
            }
        } else if (!save(entity)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "试跳成绩写入失败");
        }
        refreshBest(meetId, result);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAttempt(Long id, ResultAttemptDTO dto) {
        if (id == null || dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "试跳记录 ID 与试跳信息不能为空");
        }
        Long meetId = currentMeetId();
        ResultAttempt exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "试跳成绩不存在");
        }
        checkMeetId(meetId, exists.getMeetId());
        Result result = requireResult(meetId, exists.getResultId());
        checkNotLocked(result);

        ResultAttempt entity = new ResultAttempt();
        entity.setId(id);
        entity.setAttemptRound(dto.getAttemptRound());
        applyAttemptValue(entity, dto);
        entity.setHeightValue(dto.getHeightValue());
        entity.setIsSuccess(dto.getIsSuccess() == null ? ResultAttempt.YES : dto.getIsSuccess());
        entity.setIsPass(dto.getIsPass() == null ? ResultAttempt.NO : dto.getIsPass());
        entity.setIsFoul(dto.getIsFoul() == null ? ResultAttempt.NO : dto.getIsFoul());
        entity.setFoulType(dto.getFoulType());
        entity.setJudgeId(dto.getJudgeId());
        entity.setDeviceNo(dto.getDeviceNo());
        entity.setRemark(dto.getRemark());
        if (!updateById(entity)) {
            throw new BizException(ResultCode.CONFLICT, "试跳成绩已被他人修改，请刷新后重试");
        }
        refreshBest(meetId, result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAttempt(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "试跳记录 ID 不能为空");
        }
        Long meetId = currentMeetId();
        ResultAttempt exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "试跳成绩不存在");
        }
        checkMeetId(meetId, exists.getMeetId());
        Result result = requireResult(meetId, exists.getResultId());
        checkNotLocked(result);
        if (!removeById(id)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "试跳成绩删除失败");
        }
        refreshBest(meetId, result);
    }

    @Override
    public List<ResultAttempt> listByResult(Long resultId) {
        if (resultId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        return resultAttemptMapper.selectByResultId(currentMeetId(), resultId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAttempts(Long resultId, List<ResultAttemptDTO> dtos) {
        if (resultId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "成绩 ID 不能为空");
        }
        Long meetId = currentMeetId();
        // 整表替换：先物理删除（逻辑删除会残留 uk_result_attempt 占位行）
        resultAttemptMapper.deleteByResultIdPhysically(meetId, resultId);
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        Result result = requireResult(meetId, resultId);
        for (ResultAttemptDTO dto : dtos) {
            if (dto == null) {
                continue;
            }
            ResultAttempt entity = buildAttempt(meetId, result, dto);
            entity.setId(null);
            if (!save(entity)) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "试跳成绩写入失败");
            }
        }
    }

    /**
     * 依据入参构造试跳实体（含原文与数值双列解析）。
     *
     * @param meetId 届次 ID
     * @param result 所属成绩
     * @param dto    试跳入参
     * @return 试跳实体
     */
    private ResultAttempt buildAttempt(Long meetId, Result result, ResultAttemptDTO dto) {
        ResultAttempt entity = new ResultAttempt();
        entity.setMeetId(meetId);
        entity.setResultId(dto.getResultId());
        entity.setRegistrationId(dto.getRegistrationId() == null ? result.getRegistrationId() : dto.getRegistrationId());
        entity.setAttemptNo(dto.getAttemptNo());
        entity.setAttemptRound(dto.getAttemptRound());
        applyAttemptValue(entity, dto);
        entity.setHeightValue(dto.getHeightValue());
        entity.setIsSuccess(dto.getIsSuccess() == null ? ResultAttempt.YES : dto.getIsSuccess());
        entity.setIsPass(dto.getIsPass() == null ? ResultAttempt.NO : dto.getIsPass());
        entity.setIsFoul(dto.getIsFoul() == null ? ResultAttempt.NO : dto.getIsFoul());
        entity.setFoulType(dto.getFoulType());
        entity.setIsBest(ResultAttempt.NO);
        entity.setJudgeId(dto.getJudgeId());
        entity.setDeviceNo(dto.getDeviceNo());
        entity.setRemark(dto.getRemark());
        return entity;
    }

    /**
     * 解析并写入单次成绩的原文与数值：数值缺失时按原文解析；原文缺失时按数值格式化。
     * <p>
     * 免跳（PASS）与犯规（FOUL）不产生成绩数值。
     *
     * @param entity 试跳实体
     * @param dto    试跳入参
     */
    private void applyAttemptValue(ResultAttempt entity, ResultAttemptDTO dto) {
        boolean pass = ResultAttempt.YES.equals(dto.getIsPass());
        boolean foul = ResultAttempt.YES.equals(dto.getIsFoul());
        boolean success = !ResultAttempt.NO.equals(dto.getIsSuccess());
        String raw = dto.getAttemptRaw();
        BigDecimal value = dto.getAttemptValue();
        if (pass || foul) {
            // 免跳与犯规不产生成绩数值（保留原文以便前端回显 P / X）
            entity.setAttemptRaw(raw);
            entity.setAttemptValue(null);
            entity.setAttemptUnit(dto.getAttemptUnit());
            return;
        }
        if (value == null && StringUtils.hasText(raw)) {
            value = ResultRawParser.parse(raw);
        }
        if (value == null && success && dto.getHeightValue() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "试跳成功必须给出单次成绩数值或成绩原文");
        }
        if (!StringUtils.hasText(raw) && value != null) {
            raw = value.stripTrailingZeros().toPlainString();
        }
        entity.setAttemptRaw(raw);
        entity.setAttemptValue(value);
        entity.setAttemptUnit(dto.getAttemptUnit());
    }

    /**
     * 回写「最好一次」标记与所属成绩的最好成绩。
     *
     * @param meetId 届次 ID
     * @param result 所属成绩
     */
    private void refreshBest(Long meetId, Result result) {
        List<ResultAttempt> attempts = resultAttemptMapper.selectByResultId(meetId, result.getId());
        BigDecimal best = null;
        Integer bestAttemptNo = null;
        for (ResultAttempt attempt : attempts) {
            if (!attempt.validSuccess()) {
                continue;
            }
            if (best == null || attempt.getAttemptValue().compareTo(best) > 0) {
                best = attempt.getAttemptValue();
                bestAttemptNo = attempt.getAttemptNo();
            }
        }
        resultAttemptMapper.clearAllBestFlags(meetId, result.getId());
        if (bestAttemptNo != null) {
            resultAttemptMapper.markBest(meetId, result.getId(), bestAttemptNo);
        }
        // 成绩数值与最好一次试跳保持一致（无效状态不覆盖，由状态接口负责置空）
        if (best != null && !Result.isInvalidStatus(result.getStatus())) {
            Result update = new Result();
            update.setId(result.getId());
            update.setResultValue(best);
            update.setResultUnit(StringUtils.hasText(result.getResultUnit()) ? result.getResultUnit() : "m");
            if (!StringUtils.hasText(result.getResultRaw())) {
                update.setResultRaw(String.valueOf(best));
            }
            resultMapper.updateById(update);
        }
    }

    /**
     * 校验成绩存在且属于当前届次。
     *
     * @param meetId   届次 ID
     * @param resultId 成绩 ID
     * @return 成绩实体
     */
    private Result requireResult(Long meetId, Long resultId) {
        Result result = resultMapper.selectById(resultId);
        if (result == null) {
            throw new BizException(ResultCode.NOT_FOUND, "成绩不存在");
        }
        checkMeetId(meetId, result.getMeetId());
        return result;
    }

    /**
     * 已审核锁定的成绩禁止改动试跳明细。
     *
     * @param result 成绩实体
     */
    private void checkNotLocked(Result result) {
        if (result.locked()) {
            throw new BizException(ResultCode.RESULT_LOCKED, "成绩已审核锁定，禁止直接修改");
        }
    }

    /**
     * 届次一致性校验（不一致按越权处理）。
     *
     * @param currentMeetId 当前届次 ID
     * @param meetId        数据届次 ID
     */
    private void checkMeetId(Long currentMeetId, Long meetId) {
        if (!Objects.equals(currentMeetId, meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
    }

    /**
     * 当前届次 ID。
     *
     * @return 届次 ID
     */
    private Long currentMeetId() {
        Long meetId = MeetContext.get();
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次上下文缺失");
        }
        return meetId;
    }
}
