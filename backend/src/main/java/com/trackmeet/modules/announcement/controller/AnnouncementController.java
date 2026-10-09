package com.trackmeet.modules.announcement.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.announcement.dto.AnnouncementPublishDTO;
import com.trackmeet.modules.announcement.dto.AnnouncementQuery;
import com.trackmeet.modules.announcement.dto.AnnouncementSaveDTO;
import com.trackmeet.modules.announcement.service.AnnouncementService;
import com.trackmeet.modules.announcement.vo.AnnouncementVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公告接口：公告的增删改查与发布 / 下线。
 * <p>
 * 权限编码（均已在 {@code sql/05-init-data.sql} 与 {@code sql/09-patch-stage5.sql} 初始化）：
 * <ul>
 *     <li>{@code GET /api/announcements}、{@code GET /api/announcements/{id}} → {@code notice:list}；</li>
 *     <li>{@code POST /api/announcements} → {@code notice:add}；</li>
 *     <li>{@code PUT /api/announcements/{id}} → {@code notice:edit}；</li>
 *     <li>{@code DELETE /api/announcements/{id}} → {@code notice:delete}；</li>
 *     <li>{@code PUT /api/announcements/{id}/publish} → {@code notice:publish}。</li>
 * </ul>
 * <b>正文安全提示</b>：{@code contentHtml} 在写入与返回前均经服务端白名单净化（{@code HtmlSanitizer}：
 * 删除 script/iframe/svg 等危险元素、{@code on*} 事件属性与 {@code javascript:} 协议，仅保留安全标签）；
 * 前端以 {@code v-html} 渲染，存在存储型 XSS 风险，需由输入侧（编辑器白名单 / 前端净化）控制。
 *
 * @author TrackMeet
 */
@Slf4j
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@Validated
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /**
     * 公告分页列表。
     *
     * @param query 查询条件（meetId 缺省取 X-Meet-Id；noticeType / status / scope / isTop /
     *              keyword / beginTime / endTime；page / size）
     * @return 分页结果（含正文与纯文本摘要）
     */
    @GetMapping("")
    @PreAuthorize("@perm.has('notice:list')")
    public R<PageResult<AnnouncementVO>> list(@Valid AnnouncementQuery query) {
        return R.ok(announcementService.pageQuery(query));
    }

    /**
     * 公告详情。
     *
     * @param id 公告 ID
     * @return 公告 VO（contentHtml 为净化后的安全 HTML）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('notice:list')")
    public R<AnnouncementVO> detail(@PathVariable("id") Long id) {
        return R.ok(announcementService.getDetail(id));
    }

    /**
     * 新增公告（落 DRAFT 草稿，发布走 publish 接口）。
     *
     * @param dto 公告内容
     * @return 新公告 ID
     */
    @PostMapping("")
    @PreAuthorize("@perm.has('notice:add')")
    @OpLog(module = "ANNOUNCEMENT", action = "ADD", actionName = "新增公告", targetType = "announcement")
    public R<Long> create(@Valid @RequestBody AnnouncementSaveDTO dto) {
        return R.ok(announcementService.create(dto));
    }

    /**
     * 修改公告（不改变 status / publish_time / 发布人留痕）。
     *
     * @param id  公告 ID
     * @param dto 公告内容
     * @return 修改后的公告 VO
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('notice:edit')")
    @OpLog(module = "ANNOUNCEMENT", action = "EDIT", actionName = "修改公告", targetType = "announcement")
    public R<AnnouncementVO> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AnnouncementSaveDTO dto) {
        return R.ok(announcementService.update(id, dto));
    }

    /**
     * 删除公告（逻辑删除，仅置 deleted = 1；已发布公告须先下线）。
     *
     * @param id 公告 ID
     * @return 统一返回体
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('notice:delete')")
    @OpLog(module = "ANNOUNCEMENT", action = "DELETE", actionName = "删除公告", targetType = "announcement")
    public R<Void> delete(@PathVariable("id") Long id) {
        announcementService.delete(id);
        return R.ok();
    }

    /**
     * 发布 / 下线 / 转草稿。
     * <p>
     * 发布（{@code PUBLISHED}）写入 {@code publish_time}（原值为空取当前时间）与
     * {@code publisher_id} / {@code publisher_name}（登录态），并可按需同步 {@code screen_show} /
     * {@code screen_duration}；下线（{@code OFFLINE}）仅改状态。
     *
     * @param id  公告 ID
     * @param dto 目标状态与大屏推送参数
     * @return 发布后的公告 VO
     */
    @PutMapping("/{id}/publish")
    @PreAuthorize("@perm.has('notice:publish')")
    @OpLog(module = "ANNOUNCEMENT", action = "PUBLISH", actionName = "发布/下线公告", targetType = "announcement")
    public R<AnnouncementVO> publish(@PathVariable("id") Long id,
                                     @Valid @RequestBody AnnouncementPublishDTO dto) {
        return R.ok(announcementService.publish(id, dto));
    }
}
