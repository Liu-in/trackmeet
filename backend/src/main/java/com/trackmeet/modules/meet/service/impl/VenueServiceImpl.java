package com.trackmeet.modules.meet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.meet.entity.Venue;
import com.trackmeet.modules.meet.mapper.VenueMapper;
import com.trackmeet.modules.meet.service.VenueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 场地服务实现：含编码唯一校验、届次一致性校验与删除时唯一键改写。
 *
 * @author TrackMeet
 */
@Service
public class VenueServiceImpl extends ServiceImpl<VenueMapper, Venue> implements VenueService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final int CODE_MAX_LENGTH = 64;
    private static final String DELETED_SUFFIX = "_d";

    @Override
    public List<Venue> listByMeet(Long meetId, String venueType) {
        Long effectiveMeetId = resolveMeetId(meetId);
        LambdaQueryWrapper<Venue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Venue::getMeetId, effectiveMeetId)
                .eq(StringUtils.hasText(venueType), Venue::getVenueType, venueType)
                .orderByAsc(Venue::getId);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVenue(Venue venue) {
        if (venue == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "场地参数不能为空");
        }
        venue.setMeetId(resolveMeetId(venue.getMeetId()));
        checkRequired(venue);
        checkUnique(venue, null);
        if (!StringUtils.hasText(venue.getStatus())) {
            venue.setStatus(STATUS_ENABLED);
        }
        this.save(venue);
        return venue.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVenue(Long id, Venue venue) {
        Venue exists = getExisting(id);
        if (venue == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "场地参数不能为空");
        }
        venue.setId(id);
        venue.setMeetId(exists.getMeetId());
        checkRequired(venue);
        checkUnique(venue, id);
        this.updateById(venue);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeVenue(Long id) {
        Venue exists = getExisting(id);
        if (StringUtils.hasText(exists.getVenueCode())) {
            Venue patch = new Venue();
            patch.setId(id);
            patch.setVenueCode(buildDeletedCode(exists.getVenueCode(), id));
            this.updateById(patch);
        }
        this.removeById(id);
    }

    private Venue getExisting(Long id) {
        Venue venue = this.getById(id);
        if (venue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场地不存在");
        }
        Long current = MeetContext.get();
        if (current != null && !Objects.equals(current, venue.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该届次的场地");
        }
        return venue;
    }

    private Long resolveMeetId(Long meetId) {
        Long effective = meetId != null ? meetId : MeetContext.get();
        if (effective == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "缺少届次参数 meetId（可通过请求头 X-Meet-Id 传递）");
        }
        Long current = MeetContext.get();
        if (current != null && !current.equals(effective)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该届次数据");
        }
        return effective;
    }

    private void checkRequired(Venue venue) {
        if (!StringUtils.hasText(venue.getVenueName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "场地名称不能为空");
        }
        if (!StringUtils.hasText(venue.getVenueCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "场地编码不能为空");
        }
        if (!StringUtils.hasText(venue.getVenueType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "场地类型不能为空（TRACK/FIELD/INDOOR）");
        }
        if (venue.getLaneCount() != null && venue.getLaneCount() > 0
                && venue.getLaneCount() != 6 && venue.getLaneCount() != 8
                && venue.getLaneCount() != 4 && venue.getLaneCount() != 9 && venue.getLaneCount() != 10) {
            throw new BizException(ResultCode.PARAM_ERROR, "道数一般应为 6 或 8（也支持 4/9/10 道）");
        }
    }

    private void checkUnique(Venue venue, Long selfId) {
        LambdaQueryWrapper<Venue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Venue::getMeetId, venue.getMeetId())
                .eq(Venue::getVenueCode, venue.getVenueCode())
                .ne(selfId != null, Venue::getId, selfId);
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "场地编码已存在：" + venue.getVenueCode());
        }
    }

    private String buildDeletedCode(String code, Long id) {
        String suffix = DELETED_SUFFIX + id;
        int maxOriginLength = CODE_MAX_LENGTH - suffix.length();
        String origin = code.length() > maxOriginLength ? code.substring(0, Math.max(0, maxOriginLength)) : code;
        return origin + suffix;
    }
}
