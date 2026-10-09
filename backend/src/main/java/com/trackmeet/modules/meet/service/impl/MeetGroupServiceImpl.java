package com.trackmeet.modules.meet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.meet.dto.MeetGroupQuery;
import com.trackmeet.modules.meet.entity.MeetGroup;
import com.trackmeet.modules.meet.mapper.MeetGroupMapper;
import com.trackmeet.modules.meet.service.MeetGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 组别服务实现：含 UK(meet_id, group_code) 校验与「删除时改写唯一键」策略。
 *
 * @author TrackMeet
 */
@Service
public class MeetGroupServiceImpl extends ServiceImpl<MeetGroupMapper, MeetGroup> implements MeetGroupService {

    /** group_code 列长度上限 */
    private static final int CODE_MAX_LENGTH = 64;

    /** 逻辑删除时唯一键后缀前缀 */
    private static final String DELETED_SUFFIX = "_d";

    @Override
    public PageResult<MeetGroup> pageQuery(MeetGroupQuery query) {
        LambdaQueryWrapper<MeetGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getMeetId() != null, MeetGroup::getMeetId, query.getMeetId());
        wrapper.eq(StringUtils.hasText(query.getGender()), MeetGroup::getGender, query.getGender());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(MeetGroup::getGroupName, keyword)
                    .or().like(MeetGroup::getGroupCode, keyword));
        }
        wrapper.orderByAsc(MeetGroup::getSortNo).orderByAsc(MeetGroup::getId);
        Page<MeetGroup> page = query.toPage();
        return PageResult.of(this.page(page, wrapper));
    }

    @Override
    public MeetGroup getDetail(Long id) {
        MeetGroup group = this.getById(id);
        if (group == null) {
            throw new BizException(ResultCode.NOT_FOUND, "组别不存在");
        }
        return group;
    }

    @Override
    public List<MeetGroup> listByMeetId(Long meetId) {
        LambdaQueryWrapper<MeetGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MeetGroup::getMeetId, meetId)
                .orderByAsc(MeetGroup::getSortNo)
                .orderByAsc(MeetGroup::getId);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MeetGroup group) {
        // 届次必填：实体未带时取当前届次上下文，并做越权校验
        resolveMeetId(group);
        // 新增忽略前端传入的主键，避免覆盖历史数据
        group.setId(null);
        checkAgeRange(group);
        checkBirthDateRange(group);
        checkUnique(group);
        if (group.getSortNo() == null) {
            group.setSortNo(nextSortNo(group.getMeetId()));
        }
        if (!StringUtils.hasText(group.getStatus())) {
            group.setStatus(MeetGroup.STATUS_ENABLED);
        }
        if (group.getIsTeamScore() == null) {
            group.setIsTeamScore(0);
        }
        this.save(group);
        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MeetGroup group) {
        MeetGroup exists = getDetail(id);
        // 所属届次不允许跨届次修改：未传时沿用原值，传入时必须与原值一致
        if (group.getMeetId() == null) {
            group.setMeetId(exists.getMeetId());
        } else if (!Objects.equals(group.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改组别");
        }
        group.setId(id);
        checkAgeRange(group);
        checkBirthDateRange(group);
        checkUnique(group);
        this.updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MeetGroup group = getDetail(id);
        // 唯一键「删除时改写」：先把 group_code 改写为「原值 + _d{id}」，释放原编码供后续复用，再逻辑删除
        MeetGroup patch = new MeetGroup();
        patch.setId(id);
        patch.setGroupCode(buildDeletedCode(group.getGroupCode(), id));
        this.updateById(patch);
        this.removeById(id);
    }

    /**
     * 校验并补全届次 ID：实体为空时取当前届次上下文，仍为空则报参数错误；
     * 与上下文不一致时按越权处理（服务层二次校验，不依赖前端传参）。
     */
    private void resolveMeetId(MeetGroup group) {
        Long meetId = group.getMeetId();
        if (meetId == null) {
            meetId = MeetContext.get();
            group.setMeetId(meetId);
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
     * UK(meet_id, group_code) 校验。
     */
    private void checkUnique(MeetGroup group) {
        if (!StringUtils.hasText(group.getGroupCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "组别编码不能为空");
        }
        if (group.getMeetId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属届次 ID 不能为空");
        }
        LambdaQueryWrapper<MeetGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MeetGroup::getMeetId, group.getMeetId());
        wrapper.eq(MeetGroup::getGroupCode, group.getGroupCode());
        wrapper.ne(group.getId() != null, MeetGroup::getId, group.getId());
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该届次下组别编码已存在：" + group.getGroupCode());
        }
    }

    /**
     * 年龄区间合法性校验。
     */
    private void checkAgeRange(MeetGroup group) {
        if (group.getAgeMin() != null && group.getAgeMax() != null && group.getAgeMin() > group.getAgeMax()) {
            throw new BizException(ResultCode.PARAM_ERROR, "最小年龄不能大于最大年龄");
        }
    }

    /**
     * 出生日期区间合法性校验。
     */
    private void checkBirthDateRange(MeetGroup group) {
        if (group.getBirthDateStart() != null && group.getBirthDateEnd() != null
                && group.getBirthDateEnd().isBefore(group.getBirthDateStart())) {
            throw new BizException(ResultCode.PARAM_ERROR, "出生日期区间止不能早于区间起");
        }
    }

    /**
     * 未指定排序号时按现有组别数量顺延。
     */
    private int nextSortNo(Long meetId) {
        long count = this.count(new LambdaQueryWrapper<MeetGroup>().eq(MeetGroup::getMeetId, meetId));
        return (int) count + 1;
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
