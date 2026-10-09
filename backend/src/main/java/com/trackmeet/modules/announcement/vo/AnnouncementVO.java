package com.trackmeet.modules.announcement.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公告 VO：{@code announcement} 一行的可读展开。
 * <p>
 * {@code contentHtml} 为服务端白名单净化（{@code HtmlSanitizer}）后的安全 HTML，前端以 {@code v-html} 渲染；
 * {@code summary} 为服务层生成的纯文本摘要（去标签后截断 80 字），
 * 供列表页与无需富文本的场景使用。
 *
 * @author TrackMeet
 */
@Data
public class AnnouncementVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 公告 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 公告标题 */
    private String title;

    /** 公告正文富文本（服务端白名单净化后的安全 HTML） */
    private String contentHtml;

    /** 纯文本摘要（去 HTML 标签、折叠空白后截断 80 字） */
    private String summary;

    /** 公告类型：NOTICE / RESULT / SCHEDULE / URGENT */
    private String noticeType;

    /** 可见范围：PUBLIC / LEADER / REFEREE */
    private String scope;

    /** 是否置顶：1 置顶 / 0 不置顶 */
    private Integer isTop;

    /** 生效发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    /** 失效时间，空为长期有效 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 公告状态：DRAFT / PUBLISHED / OFFLINE */
    private String status;

    /** 发布人 ID */
    private Long publisherId;

    /** 发布人姓名快照 */
    private String publisherName;

    /** 是否推送大屏轮播：1 推送 / 0 不推送 */
    private Integer screenShow;

    /** 大屏停留秒数 */
    private Integer screenDuration;

    /** 排序号 */
    private Integer sortNo;

    /** 阅读次数 */
    private Integer readCount;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
