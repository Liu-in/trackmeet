package com.trackmeet.modules.announcement.mapper;

import com.trackmeet.modules.announcement.dto.ScreenAnnouncementRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 公告 Mapper：公告实体 CRUD 交给 MyBatis-Plus（{@code ServiceImpl}），
 * 此处只补充大屏所需的投影查询。
 * <p>
 * 取数约定（与 {@code modules/standing/mapper/StandingMapper} 同一风格）：
 * <ol>
 *     <li>只读 {@code @Select} 投影，返回本模块 DTO，<b>不引用其它模块的实体</b>；</li>
 *     <li>自带 {@code meet_id = #{meetId}} 与 {@code deleted = 0} 条件，不依赖多租户拦截器
 *         （拦截器仍会叠加相同条件，属冗余而非错误）；</li>
 *     <li>大屏公告一次取全，避免 N+1。</li>
 * </ol>
 *
 * @author TrackMeet
 */
@Mapper
public interface AnnouncementMapper {

    /**
     * 大屏公告投影：已发布（{@code PUBLISHED}）、推送大屏（{@code screen_show = 1}）
     * 且未失效（{@code expire_time} 为空或晚于当前时间）的公告。
     * <p>
     * 排序：置顶优先 → 排序号升序 → 发布时间降序 → 主键降序。
     *
     * @param meetId 届次 ID
     * @param limit  最大条数（调用方传入，控制大屏轮播数据量）
     * @return 公告投影列表
     */
    @Select("<script>"
            + "SELECT a.id AS id, a.meet_id AS meetId, a.title AS title, a.content_html AS contentHtml, "
            + "a.notice_type AS noticeType, a.scope AS scope, a.is_top AS isTop, "
            + "a.publish_time AS publishTime, a.expire_time AS expireTime, a.status AS status, "
            + "a.publisher_id AS publisherId, a.publisher_name AS publisherName, "
            + "a.screen_show AS screenShow, a.screen_duration AS screenDuration, "
            + "a.sort_no AS sortNo, a.read_count AS readCount, a.remark AS remark "
            + "FROM announcement a "
            + "WHERE a.deleted = 0 AND a.meet_id = #{meetId} "
            + "AND a.status = 'PUBLISHED' AND a.screen_show = 1 "
            + "AND (a.expire_time IS NULL OR a.expire_time &gt; NOW()) "
            + "ORDER BY a.is_top DESC, a.sort_no ASC, a.publish_time DESC, a.id DESC "
            + "LIMIT #{limit}"
            + "</script>")
    List<ScreenAnnouncementRow> selectScreenAnnouncements(@Param("meetId") Long meetId,
                                                         @Param("limit") int limit);
}
