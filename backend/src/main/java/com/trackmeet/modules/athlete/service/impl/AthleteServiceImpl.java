package com.trackmeet.modules.athlete.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.athlete.dto.AthleteQuery;
import com.trackmeet.modules.athlete.entity.Athlete;
import com.trackmeet.modules.athlete.mapper.AthleteMapper;
import com.trackmeet.modules.athlete.service.AthleteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 运动员业务实现。
 * <p>
 * 说明：meet_id 条件由 MyBatis-Plus 多租户拦截器（{@code MybatisPlusConfig}）按当前届次上下文自动追加，
 * 本实现只做服务层二次校验（届次一致性）与业务唯一性校验。
 *
 * @author TrackMeet
 */
@Service
public class AthleteServiceImpl extends ServiceImpl<AthleteMapper, Athlete> implements AthleteService {

    /** 运动员姓名最大长度，与 athlete.athlete_name VARCHAR(50) 一致 */
    private static final int ATHLETE_NAME_MAX_LENGTH = 50;

    /** 号码布编号最大长度，与 athlete.bib_no VARCHAR(32) 一致 */
    private static final int BIB_NO_MAX_LENGTH = 32;

    /** 逻辑删除时号码改写后缀前缀 */
    private static final String DELETED_BIB_SUFFIX = "_d";

    @Override
    public PageResult<Athlete> pageAthlete(AthleteQuery query) {
        AthleteQuery condition = (query == null) ? new AthleteQuery() : query;
        String keyword = condition.getKeyword();
        boolean hasKeyword = StringUtils.hasText(keyword);

        LambdaQueryWrapper<Athlete> wrapper = Wrappers.<Athlete>lambdaQuery()
                .eq(condition.getMeetId() != null, Athlete::getMeetId, condition.getMeetId())
                .eq(condition.getUnitId() != null, Athlete::getUnitId, condition.getUnitId())
                .eq(condition.getGroupId() != null, Athlete::getGroupId, condition.getGroupId())
                .eq(StringUtils.hasText(condition.getGender()), Athlete::getGender, condition.getGender())
                .eq(StringUtils.hasText(condition.getQualificationStatus()),
                        Athlete::getQualificationStatus, condition.getQualificationStatus())
                .eq(StringUtils.hasText(condition.getBibNo()), Athlete::getBibNo, condition.getBibNo())
                // keyword 同时匹配 姓名 / 证件号 / 号码
                .and(hasKeyword, w -> w.like(Athlete::getAthleteName, keyword)
                        .or().like(Athlete::getIdCard, keyword)
                        .or().like(Athlete::getBibNo, keyword))
                .orderByDesc(Athlete::getCreateTime)
                .orderByDesc(Athlete::getId);

        Page<Athlete> pageParam = condition.toPage();
        return PageResult.of(page(pageParam, wrapper));
    }

    @Override
    public Athlete getAthleteDetail(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员 ID 不能为空");
        }
        Athlete athlete = getById(id);
        if (athlete == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运动员不存在");
        }
        return athlete;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAthlete(Athlete athlete) {
        if (athlete == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员信息不能为空");
        }
        // 届次必填且必须在当前届次上下文范围内
        resolveMeetId(athlete);
        // 必填项与长度校验
        checkRequiredFields(athlete);
        // 新增忽略前端传入的主键，避免覆盖历史数据
        athlete.setId(null);
        // 身份证号、号码届次内唯一
        checkUnique(athlete);
        if (!save(athlete)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "运动员新增失败");
        }
        return athlete.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAthlete(Long id, Athlete athlete) {
        if (id == null || athlete == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员 ID 与运动员信息不能为空");
        }
        Athlete exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运动员不存在");
        }
        // 届次不允许跨届次修改：未传时沿用原值，传入时必须与原值一致
        if (athlete.getMeetId() == null) {
            athlete.setMeetId(exists.getMeetId());
        } else if (!Objects.equals(athlete.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.PARAM_ERROR, "不允许跨届次修改运动员");
        }
        resolveMeetId(athlete);
        athlete.setId(id);
        checkRequiredFields(athlete);
        checkUnique(athlete);
        if (!updateById(athlete)) {
            throw new BizException(ResultCode.CONFLICT, "运动员数据已被他人修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAthlete(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员 ID 不能为空");
        }
        Athlete exists = getById(id);
        if (exists == null) {
            throw new BizException(ResultCode.NOT_FOUND, "运动员不存在");
        }
        // 删除策略：先改写唯一键（bib_no -> bib_no_d{id}）再逻辑删除，
        // 保证历史数据可保留且同届次内号码可复用。
        if (StringUtils.hasText(exists.getBibNo())) {
            Athlete rewrite = new Athlete();
            rewrite.setId(id);
            rewrite.setBibNo(buildDeletedBibNo(exists.getBibNo(), id));
            if (!updateById(rewrite)) {
                throw new BizException(ResultCode.CONFLICT, "运动员号码改写失败，请刷新后重试");
            }
        }
        if (!removeById(id)) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "运动员删除失败");
        }
    }

    /**
     * 校验并补全届次 ID：实体为空时取当前届次上下文，仍为空则报参数错误；
     * 与上下文不一致时按越权处理。
     *
     * @param athlete 运动员
     */
    private void resolveMeetId(Athlete athlete) {
        Long meetId = athlete.getMeetId();
        if (meetId == null) {
            meetId = MeetContext.get();
            athlete.setMeetId(meetId);
        }
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        Long currentMeetId = MeetContext.get();
        if (currentMeetId != null && !currentMeetId.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
    }

    /**
     * 必填项与长度校验。
     * Controller 层已由 jakarta.validation 注解完成首道校验，此处保证服务层
     * （含后续 Excel 导入等内部调用）同样安全。
     *
     * @param athlete 运动员
     */
    private void checkRequiredFields(Athlete athlete) {
        if (!StringUtils.hasText(athlete.getAthleteName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员姓名不能为空");
        }
        if (athlete.getAthleteName().length() > ATHLETE_NAME_MAX_LENGTH) {
            throw new BizException(ResultCode.PARAM_ERROR, "运动员姓名长度不能超过 " + ATHLETE_NAME_MAX_LENGTH);
        }
        if (athlete.getUnitId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属参赛单位不能为空");
        }
        if (athlete.getGroupId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "所属组别不能为空");
        }
    }

    /**
     * 唯一性校验：身份证号、号码在同届次内唯一（修改时排除自身）。
     *
     * @param athlete 运动员
     */
    private void checkUnique(Athlete athlete) {
        Long meetId = athlete.getMeetId();
        Long selfId = athlete.getId();

        if (StringUtils.hasText(athlete.getIdCard())) {
            long idCardCount = count(Wrappers.<Athlete>lambdaQuery()
                    .eq(Athlete::getMeetId, meetId)
                    .eq(Athlete::getIdCard, athlete.getIdCard())
                    .ne(selfId != null, Athlete::getId, selfId));
            if (idCardCount > 0) {
                throw new BizException(ResultCode.CONFLICT, "身份证号已存在");
            }
        }

        if (StringUtils.hasText(athlete.getBibNo())) {
            long bibNoCount = count(Wrappers.<Athlete>lambdaQuery()
                    .eq(Athlete::getMeetId, meetId)
                    .eq(Athlete::getBibNo, athlete.getBibNo())
                    .ne(selfId != null, Athlete::getId, selfId));
            if (bibNoCount > 0) {
                throw new BizException(ResultCode.CONFLICT, "号码已存在");
            }
        }
    }

    /**
     * 生成逻辑删除后的号码：{@code 原值 + _d + id}，超过列长度时截断原值，保证不超过 32 字符。
     *
     * @param bibNo 原号码
     * @param id    运动员 ID
     * @return 改写后的号码
     */
    private String buildDeletedBibNo(String bibNo, Long id) {
        String suffix = DELETED_BIB_SUFFIX + id;
        if (suffix.length() >= BIB_NO_MAX_LENGTH) {
            return suffix.substring(suffix.length() - BIB_NO_MAX_LENGTH);
        }
        if (bibNo.length() + suffix.length() <= BIB_NO_MAX_LENGTH) {
            return bibNo + suffix;
        }
        return bibNo.substring(0, BIB_NO_MAX_LENGTH - suffix.length()) + suffix;
    }
}
