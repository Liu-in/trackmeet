package com.trackmeet.modules.meet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.meet.dto.UnitQuery;
import com.trackmeet.modules.meet.entity.Unit;
import com.trackmeet.modules.meet.mapper.UnitMapper;
import com.trackmeet.modules.meet.service.UnitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 参赛单位服务实现：含 UK(meet_id, unit_code) 校验与「删除时改写唯一键」策略。
 *
 * @author TrackMeet
 */
@Service
public class UnitServiceImpl extends ServiceImpl<UnitMapper, Unit> implements UnitService {

    /** unit_code 列长度上限 */
    private static final int CODE_MAX_LENGTH = 64;

    /** 逻辑删除时唯一键后缀前缀 */
    private static final String DELETED_SUFFIX = "_d";

    @Override
    public PageResult<Unit> pageQuery(UnitQuery query) {
        LambdaQueryWrapper<Unit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getMeetId() != null, Unit::getMeetId, query.getMeetId());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Unit::getUnitName, keyword)
                    .or().like(Unit::getUnitCode, keyword)
                    .or().like(Unit::getShortName, keyword));
        }
        wrapper.orderByAsc(Unit::getUnitCode).orderByAsc(Unit::getId);
        Page<Unit> page = query.toPage();
        return PageResult.of(this.page(page, wrapper));
    }

    @Override
    public Unit getDetail(Long id) {
        Unit unit = this.getById(id);
        if (unit == null) {
            throw new BizException(ResultCode.NOT_FOUND, "参赛单位不存在");
        }
        return unit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Unit unit) {
        // 届次必填：实体未带时取当前届次上下文，并做越权校验
        resolveMeetId(unit);
        // 新增忽略前端传入的主键，避免覆盖历史数据
        unit.setId(null);
        checkUnique(unit);
        if (!StringUtils.hasText(unit.getStatus())) {
            unit.setStatus(Unit.STATUS_ENABLED);
        }
        if (unit.getTotalQuota() == null) {
            unit.setTotalQuota(0);
        }
        this.save(unit);
        return unit.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Unit unit) {
        Unit exists = getDetail(id);
        // 所属届次不允许跨届次修改：未传时沿用原值，传入时必须与原值一致
        if (unit.getMeetId() == null) {
            unit.setMeetId(exists.getMeetId());
        } else if (!Objects.equals(unit.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改参赛单位");
        }
        unit.setId(id);
        checkUnique(unit);
        this.updateById(unit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Unit unit = getDetail(id);
        // 唯一键「删除时改写」：先把 unit_code 改写为「原值 + _d{id}」，释放原编码供后续复用，再逻辑删除
        Unit patch = new Unit();
        patch.setId(id);
        patch.setUnitCode(buildDeletedCode(unit.getUnitCode(), id));
        this.updateById(patch);
        this.removeById(id);
    }

    /**
     * 校验并补全届次 ID：实体为空时取当前届次上下文，仍为空则报参数错误；
     * 与上下文不一致时按越权处理（服务层二次校验，不依赖前端传参）。
     */
    private void resolveMeetId(Unit unit) {
        Long meetId = unit.getMeetId();
        if (meetId == null) {
            meetId = MeetContext.get();
            unit.setMeetId(meetId);
        }
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属届次 ID 不能为空");
        }
        Long currentMeetId = MeetContext.get();
        if (currentMeetId != null && !currentMeetId.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
    }

    /**
     * UK(meet_id, unit_code) 校验。
     */
    private void checkUnique(Unit unit) {
        if (!StringUtils.hasText(unit.getUnitCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "单位编码不能为空");
        }
        if (unit.getMeetId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属届次 ID 不能为空");
        }
        LambdaQueryWrapper<Unit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Unit::getMeetId, unit.getMeetId());
        wrapper.eq(Unit::getUnitCode, unit.getUnitCode());
        wrapper.ne(unit.getId() != null, Unit::getId, unit.getId());
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该届次下单位编码已存在：" + unit.getUnitCode());
        }
    }

    /**
     * 生成改写后的唯一键：原值 + _d{id}，超长时截断原值以保证不超出列长度。
     */
    private String buildDeletedCode(String code, Long id) {
        String suffix = DELETED_SUFFIX + id;
        String base = code == null ? "" : code;
        int maxBaseLength = CODE_MAX_LENGTH - suffix.length();
        if (base.length() > maxBaseLength) {
            base = base.substring(0, Math.max(maxBaseLength, 0));
        }
        return base + suffix;
    }
}
