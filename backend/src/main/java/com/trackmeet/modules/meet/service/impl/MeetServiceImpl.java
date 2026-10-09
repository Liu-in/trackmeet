package com.trackmeet.modules.meet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.meet.dto.MeetQuery;
import com.trackmeet.modules.meet.entity.Meet;
import com.trackmeet.modules.meet.entity.MeetGroup;
import com.trackmeet.modules.meet.mapper.MeetMapper;
import com.trackmeet.modules.meet.service.MeetGroupService;
import com.trackmeet.modules.meet.service.MeetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 运动会（届次）服务实现：含唯一键校验、状态机与届次下组别管理。
 *
 * @author TrackMeet
 */
@Service
@RequiredArgsConstructor
public class MeetServiceImpl extends ServiceImpl<MeetMapper, Meet> implements MeetService {

    /** 届次状态机顺序：按序前进一步，ARCHIVED 为终态 */
    private static final List<String> STATUS_FLOW = List.of(
            Meet.STATUS_DRAFT,
            Meet.STATUS_REG_OPEN,
            Meet.STATUS_REG_CLOSED,
            Meet.STATUS_SCHEDULING,
            Meet.STATUS_SCHEDULED,
            Meet.STATUS_RUNNING,
            Meet.STATUS_FINISHED,
            Meet.STATUS_ARCHIVED
    );

    /** 届次下组别能力复用组别服务，保证唯一键策略一致 */
    private final MeetGroupService meetGroupService;

    @Override
    public PageResult<Meet> pageQuery(MeetQuery query) {
        LambdaQueryWrapper<Meet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getStatus()), Meet::getStatus, query.getStatus());
        if (query.getYear() != null) {
            // 无 year 列，按比赛开始日期所在年度区间过滤
            wrapper.between(Meet::getStartDate,
                    LocalDate.of(query.getYear(), 1, 1),
                    LocalDate.of(query.getYear(), 12, 31));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Meet::getMeetName, keyword)
                    .or().like(Meet::getEdition, keyword)
                    .or().like(Meet::getShortName, keyword));
        }
        wrapper.orderByDesc(Meet::getStartDate).orderByDesc(Meet::getId);
        Page<Meet> page = query.toPage();
        return PageResult.of(this.page(page, wrapper));
    }

    @Override
    public Meet getDetail(Long id) {
        Meet meet = this.getById(id);
        if (meet == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运动会不存在");
        }
        return meet;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Meet meet) {
        // 状态默认草稿，且只允许以草稿态创建
        if (!StringUtils.hasText(meet.getStatus())) {
            meet.setStatus(Meet.STATUS_DRAFT);
        } else if (!Meet.STATUS_DRAFT.equals(meet.getStatus())) {
            throw new BizException(ResultCode.MEET_STATUS_ERROR, "新建运动会只能为草稿状态");
        }
        checkDateRange(meet);
        checkUnique(meet);
        if (meet.getIsTemplate() == null) {
            meet.setIsTemplate(0);
        }
        // 说明：meet 为届次根实体，其表结构不含 meet_id 租户列（已在多租户拦截器忽略清单中）
        this.save(meet);
        return meet.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, Meet meet) {
        getDetail(id);
        meet.setId(id);
        // 状态仅允许通过状态流转接口变更，避免绕过状态机
        meet.setStatus(null);
        checkDateRange(meet);
        checkUnique(meet);
        this.updateById(meet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Meet meet = getDetail(id);
        String status = meet.getStatus();
        if (!Meet.STATUS_DRAFT.equals(status) && !Meet.STATUS_ARCHIVED.equals(status)) {
            throw new BizException(ResultCode.MEET_STATUS_ERROR, "仅草稿或已归档的运动会允许删除");
        }
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String targetStatus) {
        Meet meet = getDetail(id);
        String currentStatus = meet.getStatus();
        int currentIndex = STATUS_FLOW.indexOf(currentStatus);
        if (currentIndex < 0) {
            throw new BizException(ResultCode.MEET_STATUS_ERROR, "当前届次状态异常：" + currentStatus);
        }
        if (currentIndex == STATUS_FLOW.size() - 1) {
            throw new BizException(ResultCode.MEET_STATUS_ERROR, "已归档的届次为终态，不可再变更状态");
        }
        int targetIndex = STATUS_FLOW.indexOf(targetStatus);
        if (targetIndex < 0) {
            throw new BizException(ResultCode.MEET_STATUS_ERROR, "非法的目标状态：" + targetStatus);
        }
        // 只允许按顺序前进一步（FINISHED → ARCHIVED 即该顺序的最后一步）
        if (targetIndex != currentIndex + 1) {
            throw new BizException(ResultCode.MEET_STATUS_ERROR,
                    "状态只允许按顺序前进一步，不允许从 " + currentStatus + " 流转到 " + targetStatus);
        }
        Meet patch = new Meet();
        patch.setId(id);
        patch.setStatus(targetStatus);
        this.updateById(patch);
    }

    @Override
    public List<MeetGroup> listGroups(Long meetId) {
        getDetail(meetId);
        return meetGroupService.listByMeetId(meetId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addGroup(Long meetId, MeetGroup group) {
        getDetail(meetId);
        group.setMeetId(meetId);
        return meetGroupService.create(group);
    }

    /**
     * 唯一键 UK(meet_name, edition) 校验。
     */
    private void checkUnique(Meet meet) {
        if (!StringUtils.hasText(meet.getMeetName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动会名称不能为空");
        }
        LambdaQueryWrapper<Meet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Meet::getMeetName, meet.getMeetName());
        if (StringUtils.hasText(meet.getEdition())) {
            wrapper.eq(Meet::getEdition, meet.getEdition());
        } else {
            wrapper.isNull(Meet::getEdition);
        }
        wrapper.ne(meet.getId() != null, Meet::getId, meet.getId());
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT,
                    "同一届次标识下的运动会名称已存在：" + meet.getMeetName());
        }
    }

    /**
     * 比赛日期区间合法性校验。
     */
    private void checkDateRange(Meet meet) {
        if (meet.getStartDate() != null && meet.getEndDate() != null
                && meet.getEndDate().isBefore(meet.getStartDate())) {
            throw new BizException(ResultCode.PARAM_ERROR, "比赛结束日期不能早于开始日期");
        }
    }
}
