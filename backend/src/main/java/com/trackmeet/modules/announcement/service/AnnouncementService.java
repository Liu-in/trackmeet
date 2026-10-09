package com.trackmeet.modules.announcement.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.announcement.dto.AnnouncementPublishDTO;
import com.trackmeet.modules.announcement.dto.AnnouncementQuery;
import com.trackmeet.modules.announcement.dto.AnnouncementSaveDTO;
import com.trackmeet.modules.announcement.entity.Announcement;
import com.trackmeet.modules.announcement.vo.AnnouncementVO;

import java.util.List;

/**
 * 公告服务：公告的增删改查与发布 / 下线。
 * <p>
 * 对应需求 FR-ANN 系列与《API 清单》第 11 节「大屏与公告」：
 * 列表 / 详情（{@code notice:list}）、新增（{@code notice:add}）、修改（{@code notice:edit}）、
 * 删除（{@code notice:delete}）、发布 / 下线（{@code notice:publish}）。
 * <p>
 * 所有读写都做届次校验：请求届次必须与 {@code MeetContext.get()} 一致，否则抛 {@code FORBIDDEN}。
 *
 * @author TrackMeet
 */
public interface AnnouncementService extends IService<Announcement> {

    /**
     * 公告分页查询（条件：noticeType / status / scope / isTop / keyword / 发布时间区间）。
     *
     * @param query 查询条件（meetId 缺省取届次上下文）
     * @return 分页结果（含纯文本摘要 summary）
     */
    PageResult<AnnouncementVO> pageQuery(AnnouncementQuery query);

    /**
     * 公告详情（正文 {@code contentHtml} 为服务端净化后的安全 HTML）。
     *
     * @param id 公告 ID
     * @return 公告 VO
     */
    AnnouncementVO getDetail(Long id);

    /**
     * 新增公告：落 {@code DRAFT} 草稿，主键忽略入参。
     *
     * @param dto 公告内容
     * @return 新公告 ID
     */
    Long create(AnnouncementSaveDTO dto);

    /**
     * 修改公告：仅更新内容字段，不改 {@code status} / {@code publish_time} / 发布人留痕
     * （发布与下线走 {@link #publish}）。
     *
     * @param id  公告 ID
     * @param dto 公告内容
     * @return 修改后的公告 VO
     */
    AnnouncementVO update(Long id, AnnouncementSaveDTO dto);

    /**
     * 逻辑删除公告：仅置 {@code deleted = 1}，不改写其它字段；
     * 已发布（{@code PUBLISHED}）的公告必须先下线（{@code OFFLINE}）才能删除。
     *
     * @param id 公告 ID
     */
    void delete(Long id);

    /**
     * 发布 / 下线 / 转草稿：发布时写 {@code publish_time}（原值为空取当前时间）与
     * {@code publisher_id} / {@code publisher_name}（取自登录态），并可同时更新大屏推送开关。
     *
     * @param id  公告 ID
     * @param dto 目标状态与大屏推送参数
     * @return 发布后的公告 VO
     */
    AnnouncementVO publish(Long id, AnnouncementPublishDTO dto);

    /**
     * 大屏公告列表：已发布 + {@code screen_show = 1} + 未失效，置顶优先（供大屏聚合调用）。
     *
     * @param meetId 届次 ID
     * @return 公告 VO 列表
     */
    List<AnnouncementVO> listScreenAnnouncements(Long meetId);

    /**
     * 实体转 VO（列表与详情共用，含摘要生成）。
     *
     * @param entity 公告实体
     * @return 公告 VO
     */
    AnnouncementVO toVO(Announcement entity);
}
