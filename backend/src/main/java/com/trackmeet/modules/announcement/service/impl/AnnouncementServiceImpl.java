package com.trackmeet.modules.announcement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.common.util.HtmlSanitizer;
import com.trackmeet.common.util.RequestUtils;
import com.trackmeet.common.util.SecurityUtils;
import com.trackmeet.modules.announcement.dto.AnnouncementPublishDTO;
import com.trackmeet.modules.announcement.dto.AnnouncementQuery;
import com.trackmeet.modules.announcement.dto.AnnouncementSaveDTO;
import com.trackmeet.modules.announcement.dto.ScreenAnnouncementRow;
import com.trackmeet.modules.announcement.entity.Announcement;
import com.trackmeet.modules.announcement.mapper.AnnouncementMapper;
import com.trackmeet.modules.announcement.service.AnnouncementService;
import com.trackmeet.modules.announcement.vo.AnnouncementVO;
import com.trackmeet.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 公告服务实现：分页查询、详情、增删改与发布 / 下线。
 * <p>
 * <b>关键约定</b>：
 * <ol>
 *     <li><b>多租户</b>：{@link #resolveMeetId(Long)} 保证「入参届次优先、缺省取上下文、与上下文冲突即
 *         {@code FORBIDDEN}」，写操作前再按数据行的 {@code meet_id} 复核；</li>
 *     <li><b>删除</b>：{@code announcement} 唯一键只有主键，删除仅置 {@code deleted = 1}，
 *         不改写任何业务字段；已发布公告必须先下线；</li>
 *     <li><b>发布</b>：写 {@code publish_time}（原值为空时取 now）、{@code publisher_id} /
 *         {@code publisher_name}（登录态快照），并按需同步 {@code screen_show} /
 *         {@code screen_duration}（推送大屏轮播）；</li>
 *     <li><b>正文</b>：{@code content_html} 在写入与返回两侧均经 {@code HtmlSanitizer} 白名单净化（防存储型 XSS），
 *         仅额外生成纯文本摘要 {@code summary}（去标签截断 80 字）；</li>
 *     <li><b>更新语义</b>：修改与发布都用 {@code LambdaUpdateWrapper} 显式 {@code SET}，
 *         以便把 {@code expire_time} 等字段真正清空（{@code updateById} 会忽略 null）。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement>
        implements AnnouncementService {

    /** 摘要最大字符数 */
    private static final int SUMMARY_MAX_LENGTH = 80;

    /** 大屏公告最大条数 */
    private static final int SCREEN_ANNOUNCEMENT_LIMIT = 20;

    /** 发布人姓名快照列长度 */
    private static final int PUBLISHER_NAME_MAX_LENGTH = 50;

    /** HTML 标签 */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    /** 连续空白 */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** 合法公告类型 */
    private static final Set<String> NOTICE_TYPES = Set.of(
            Announcement.TYPE_NOTICE, Announcement.TYPE_RESULT,
            Announcement.TYPE_SCHEDULE, Announcement.TYPE_URGENT);

    /** 合法可见范围 */
    private static final Set<String> SCOPES = Set.of(
            Announcement.SCOPE_PUBLIC, Announcement.SCOPE_LEADER, Announcement.SCOPE_REFEREE);

    /** 合法公告状态 */
    private static final Set<String> STATUSES = Set.of(
            Announcement.STATUS_DRAFT, Announcement.STATUS_PUBLISHED, Announcement.STATUS_OFFLINE);

    // ==================================================================================
    // 一、查询
    // ==================================================================================

    @Override
    public PageResult<AnnouncementVO> pageQuery(AnnouncementQuery query) {
        Long meetId = resolveMeetId(query == null ? null : query.getMeetId());
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Announcement::getMeetId, meetId);
        if (query != null) {
            wrapper.eq(StringUtils.hasText(query.getNoticeType()),
                    Announcement::getNoticeType, upper(query.getNoticeType()));
            wrapper.eq(StringUtils.hasText(query.getStatus()),
                    Announcement::getStatus, upper(query.getStatus()));
            wrapper.eq(StringUtils.hasText(query.getScope()),
                    Announcement::getScope, upper(query.getScope()));
            wrapper.eq(query.getIsTop() != null, Announcement::getIsTop, query.getIsTop());
            wrapper.ge(query.getBeginTime() != null, Announcement::getPublishTime, query.getBeginTime());
            wrapper.le(query.getEndTime() != null, Announcement::getPublishTime, query.getEndTime());
            if (StringUtils.hasText(query.getKeyword())) {
                String keyword = query.getKeyword().trim();
                wrapper.and(w -> w.like(Announcement::getTitle, keyword)
                        .or().like(Announcement::getContentHtml, keyword)
                        .or().like(Announcement::getRemark, keyword));
            }
        }
        wrapper.orderByDesc(Announcement::getIsTop)
                .orderByAsc(Announcement::getSortNo)
                .orderByDesc(Announcement::getPublishTime)
                .orderByDesc(Announcement::getId);
        Page<Announcement> page = query == null ? new Page<>(1L, 20L) : query.toPage();
        IPage<Announcement> result = this.page(page, wrapper);
        List<AnnouncementVO> records = new ArrayList<>(result.getRecords().size());
        for (Announcement entity : result.getRecords()) {
            records.add(toVO(entity));
        }
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AnnouncementVO getDetail(Long id) {
        return toVO(requireAnnouncement(id));
    }

    @Override
    public List<AnnouncementVO> listScreenAnnouncements(Long meetId) {
        List<AnnouncementVO> result = new ArrayList<>();
        if (meetId == null) {
            return result;
        }
        List<ScreenAnnouncementRow> rows =
                baseMapper.selectScreenAnnouncements(meetId, SCREEN_ANNOUNCEMENT_LIMIT);
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (ScreenAnnouncementRow row : rows) {
            AnnouncementVO vo = new AnnouncementVO();
            vo.setId(row.getId());
            vo.setMeetId(row.getMeetId());
            vo.setTitle(row.getTitle());
            vo.setContentHtml(HtmlSanitizer.sanitize(row.getContentHtml()));
            vo.setSummary(buildSummary(row.getContentHtml()));
            vo.setNoticeType(row.getNoticeType());
            vo.setScope(row.getScope());
            vo.setIsTop(row.getIsTop());
            vo.setPublishTime(row.getPublishTime());
            vo.setExpireTime(row.getExpireTime());
            vo.setStatus(row.getStatus());
            vo.setPublisherId(row.getPublisherId());
            vo.setPublisherName(row.getPublisherName());
            vo.setScreenShow(row.getScreenShow());
            vo.setScreenDuration(row.getScreenDuration());
            vo.setSortNo(row.getSortNo());
            vo.setReadCount(row.getReadCount());
            vo.setRemark(row.getRemark());
            result.add(vo);
        }
        return result;
    }

    // ==================================================================================
    // 二、写入
    // ==================================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AnnouncementSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "公告内容不能为空");
        }
        Long meetId = resolveMeetId(dto.getMeetId());
        Announcement entity = new Announcement();
        entity.setMeetId(meetId);
        entity.setTitle(dto.getTitle() == null ? null : dto.getTitle().trim());
        entity.setContentHtml(HtmlSanitizer.sanitize(dto.getContentHtml()));
        entity.setNoticeType(normalize(dto.getNoticeType(), Announcement.TYPE_NOTICE, NOTICE_TYPES, "公告类型"));
        entity.setScope(normalize(dto.getScope(), Announcement.SCOPE_PUBLIC, SCOPES, "可见范围"));
        entity.setIsTop(flag(dto.getIsTop()));
        entity.setExpireTime(dto.getExpireTime());
        entity.setStatus(Announcement.STATUS_DRAFT);
        entity.setScreenShow(flag(dto.getScreenShow()));
        entity.setScreenDuration(dto.getScreenDuration());
        entity.setSortNo(dto.getSortNo() == null ? 0 : dto.getSortNo());
        entity.setReadCount(0);
        entity.setRemark(dto.getRemark());
        this.save(entity);
        log.info("新增公告：id={}, meetId={}, title={}", entity.getId(), meetId, entity.getTitle());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVO update(Long id, AnnouncementSaveDTO dto) {
        Announcement exists = requireAnnouncement(id);
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "公告内容不能为空");
        }
        if (dto.getMeetId() != null && !Objects.equals(dto.getMeetId(), exists.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "不允许跨届次修改公告");
        }
        checkMeetContext(exists.getMeetId());
        String title = dto.getTitle() == null ? null : dto.getTitle().trim();
        if (!StringUtils.hasText(title)) {
            throw new BizException(ResultCode.PARAM_ERROR, "公告标题不能为空");
        }
        String noticeType = normalize(dto.getNoticeType(), exists.getNoticeType(), NOTICE_TYPES, "公告类型");
        String scope = normalize(dto.getScope(), exists.getScope(), SCOPES, "可见范围");
        Integer isTop = dto.getIsTop() == null ? exists.getIsTop() : flag(dto.getIsTop());
        Integer screenShow = dto.getScreenShow() == null ? exists.getScreenShow() : flag(dto.getScreenShow());
        Integer screenDuration = dto.getScreenDuration() == null
                ? exists.getScreenDuration() : dto.getScreenDuration();
        Integer sortNo = dto.getSortNo() == null ? exists.getSortNo() : dto.getSortNo();
        LambdaUpdateWrapper<Announcement> wrapper = Wrappers.<Announcement>lambdaUpdate()
                .eq(Announcement::getId, id)
                .set(Announcement::getTitle, title)
                .set(Announcement::getContentHtml, HtmlSanitizer.sanitize(dto.getContentHtml()))
                .set(Announcement::getNoticeType, noticeType)
                .set(Announcement::getScope, scope)
                .set(Announcement::getIsTop, isTop)
                .set(Announcement::getExpireTime, dto.getExpireTime())
                .set(Announcement::getScreenShow, screenShow)
                .set(Announcement::getScreenDuration, screenDuration)
                .set(Announcement::getSortNo, sortNo)
                .set(Announcement::getRemark, dto.getRemark())
                .set(Announcement::getUpdateBy, SecurityUtils.getUserId())
                .set(Announcement::getUpdateTime, LocalDateTime.now());
        this.update(wrapper);
        log.info("修改公告：id={}, title={}", id, title);
        return getDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Announcement exists = requireAnnouncement(id);
        checkMeetContext(exists.getMeetId());
        if (Announcement.STATUS_PUBLISHED.equals(exists.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "公告已发布，请先下线（status=OFFLINE）后再删除");
        }
        // 唯一键只有主键，删除无需改写任何字段，仅置 deleted = 1（@TableLogic 逻辑删除）
        this.removeById(id);
        log.info("删除公告：id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVO publish(Long id, AnnouncementPublishDTO dto) {
        Announcement exists = requireAnnouncement(id);
        checkMeetContext(exists.getMeetId());
        if (dto == null || !StringUtils.hasText(dto.getStatus())) {
            throw new BizException(ResultCode.PARAM_ERROR, "目标状态不能为空");
        }
        String status = upper(dto.getStatus());
        if (!STATUSES.contains(status)) {
            throw new BizException(ResultCode.PARAM_ERROR, "非法的公告状态：" + dto.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Announcement> wrapper = Wrappers.<Announcement>lambdaUpdate()
                .eq(Announcement::getId, id)
                .set(Announcement::getStatus, status)
                .set(Announcement::getUpdateBy, SecurityUtils.getUserId())
                .set(Announcement::getUpdateTime, now);
        if (dto.getScreenShow() != null) {
            wrapper.set(Announcement::getScreenShow, flag(dto.getScreenShow()));
        }
        if (dto.getScreenDuration() != null) {
            wrapper.set(Announcement::getScreenDuration, dto.getScreenDuration());
        }
        if (Announcement.STATUS_PUBLISHED.equals(status)) {
            if (exists.getPublishTime() == null) {
                wrapper.set(Announcement::getPublishTime, now);
            }
            LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
            if (loginUser != null) {
                wrapper.set(Announcement::getPublisherId, loginUser.getId());
                wrapper.set(Announcement::getPublisherName,
                        RequestUtils.truncate(displayName(loginUser), PUBLISHER_NAME_MAX_LENGTH));
            }
        }
        this.update(wrapper);
        log.info("公告状态流转：id={}, status={}, screenShow={}", id, status, dto.getScreenShow());
        return getDetail(id);
    }

    // ==================================================================================
    // 三、装配与校验
    // ==================================================================================

    @Override
    public AnnouncementVO toVO(Announcement entity) {
        AnnouncementVO vo = new AnnouncementVO();
        if (entity == null) {
            return vo;
        }
        vo.setId(entity.getId());
        vo.setMeetId(entity.getMeetId());
        vo.setTitle(entity.getTitle());
        vo.setContentHtml(HtmlSanitizer.sanitize(entity.getContentHtml()));
        vo.setSummary(buildSummary(entity.getContentHtml()));
        vo.setNoticeType(entity.getNoticeType());
        vo.setScope(entity.getScope());
        vo.setIsTop(entity.getIsTop());
        vo.setPublishTime(entity.getPublishTime());
        vo.setExpireTime(entity.getExpireTime());
        vo.setStatus(entity.getStatus());
        vo.setPublisherId(entity.getPublisherId());
        vo.setPublisherName(entity.getPublisherName());
        vo.setScreenShow(entity.getScreenShow());
        vo.setScreenDuration(entity.getScreenDuration());
        vo.setSortNo(entity.getSortNo());
        vo.setReadCount(entity.getReadCount());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 生成纯文本摘要：去 HTML 标签 → 反转义常见实体 → 折叠空白 → 截断 80 字。
     * <p>
     * 正文本身不清洗，摘要仅用于列表页与不支持富文本的场景。
     *
     * @param contentHtml 公告正文富文本
     * @return 纯文本摘要，正文为空时返回 {@code null}
     */
    static String buildSummary(String contentHtml) {
        if (!StringUtils.hasText(contentHtml)) {
            return null;
        }
        String text = HTML_TAG.matcher(contentHtml).replaceAll(" ");
        text = text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (text.length() <= SUMMARY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, SUMMARY_MAX_LENGTH);
    }

    private Announcement requireAnnouncement(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "公告 ID 不能为空");
        }
        Announcement entity = this.getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "公告不存在");
        }
        return entity;
    }

    /**
     * 解析届次：入参优先，其次 {@link MeetContext}；与上下文不一致按越权处理。
     */
    private Long resolveMeetId(Long meetId) {
        Long current = MeetContext.get();
        Long resolved = meetId != null ? meetId : current;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "届次不能为空（请携带 X-Meet-Id 请求头或在参数中指定 meetId）");
        }
        if (current != null && !current.equals(resolved)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
        return resolved;
    }

    /**
     * 按数据行届次二次校验（不依赖前端传参）。
     */
    private void checkMeetContext(Long meetId) {
        Long current = MeetContext.get();
        if (current != null && !current.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的数据");
        }
    }

    private String normalize(String value, String fallback, Set<String> allowed, String label) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String upper = upper(value);
        if (!allowed.contains(upper)) {
            throw new BizException(ResultCode.PARAM_ERROR, "非法的" + label + "：" + value);
        }
        return upper;
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private Integer flag(Integer value) {
        if (value == null) {
            return 0;
        }
        return value == 0 ? 0 : 1;
    }

    private String displayName(LoginUser loginUser) {
        return StringUtils.hasText(loginUser.getRealName()) ? loginUser.getRealName() : loginUser.getUsername();
    }
}
