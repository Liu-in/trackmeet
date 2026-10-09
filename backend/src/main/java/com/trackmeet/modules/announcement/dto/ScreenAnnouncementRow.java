package com.trackmeet.modules.announcement.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 大屏公告投影：{@code announcement} 单表取数（已发布 + 推送大屏 + 未失效）。
 * <p>
 * {@code content_html} 原样取出（前端 {@code v-html} 展示），服务层另外生成
 * 纯文本摘要 {@code summary}。
 *
 * @author TrackMeet
 */
@Data
public class ScreenAnnouncementRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 公告标题 */
    private String title;

    /** 公告正文富文本（原样） */
    private String contentHtml;

    /** 公告类型：NOTICE / RESULT / SCHEDULE / URGENT */
    private String noticeType;

    /** 可见范围：PUBLIC / LEADER / REFEREE */
    private String scope;

    /** 是否置顶：1 置顶 / 0 不置顶 */
    private Integer isTop;

    /** 发布时间 */
    private LocalDateTime publishTime;

    /** 失效时间 */
    private LocalDateTime expireTime;

    /** 公告状态 */
    private String status;

    /** 发布人 ID */
    private Long publisherId;

    /** 发布人姓名快照 */
    private String publisherName;

    /** 是否推送大屏：1 是 / 0 否 */
    private Integer screenShow;

    /** 大屏停留秒数 */
    private Integer screenDuration;

    /** 排序号 */
    private Integer sortNo;

    /** 阅读次数 */
    private Integer readCount;

    /** 备注 */
    private String remark;
}
