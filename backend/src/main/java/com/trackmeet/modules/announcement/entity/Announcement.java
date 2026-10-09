package com.trackmeet.modules.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * announcement 公告表：届次公告与通知，支持置顶、可见范围与大屏展示开关。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code announcement} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, meet_id, title, content_html, notice_type, scope, is_top, publish_time, expire_time, status,
 * publisher_id, publisher_name, screen_show, screen_duration, sort_no, read_count, remark,
 * create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * <p>
 * <b>删除语义</b>：{@code announcement} 的唯一键只有主键，逻辑删除不会与后续新建公告争抢唯一键，
 * 因此删除仅置 {@code deleted = 1}（见 {@code AnnouncementServiceImpl#delete}），不改写任何业务字段。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("announcement")
public class Announcement extends BaseEntity {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 枚举取值（与 docs/12-05 第三节「本层枚举取值」逐字一致）
    // ------------------------------------------------------------------

    /** 公告类型：通知 */
    public static final String TYPE_NOTICE = "NOTICE";
    /** 公告类型：成绩 */
    public static final String TYPE_RESULT = "RESULT";
    /** 公告类型：日程 */
    public static final String TYPE_SCHEDULE = "SCHEDULE";
    /** 公告类型：紧急 */
    public static final String TYPE_URGENT = "URGENT";

    /** 可见范围：公开 */
    public static final String SCOPE_PUBLIC = "PUBLIC";
    /** 可见范围：领队可见 */
    public static final String SCOPE_LEADER = "LEADER";
    /** 可见范围：裁判可见 */
    public static final String SCOPE_REFEREE = "REFEREE";

    /** 公告状态：草稿 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 公告状态：已发布 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 公告状态：已下线 */
    public static final String STATUS_OFFLINE = "OFFLINE";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 公告标题 */
    @TableField("title")
    private String title;

    /** 公告正文富文本（原样存储与返回，服务端不做清洗） */
    @TableField("content_html")
    private String contentHtml;

    /** 公告类型：NOTICE 通知 / RESULT 成绩 / SCHEDULE 日程 / URGENT 紧急 */
    @TableField("notice_type")
    private String noticeType;

    /** 可见范围：PUBLIC 公开 / LEADER 领队可见 / REFEREE 裁判可见 */
    @TableField("scope")
    private String scope;

    /** 是否置顶：1 置顶 / 0 不置顶 */
    @TableField("is_top")
    private Integer isTop;

    /** 生效发布时间（发布时写入，为空时取当前时间） */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /** 失效时间，空为长期有效 */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /** 公告状态：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下线 */
    @TableField("status")
    private String status;

    /** 发布人 ID（sys_user.id） */
    @TableField("publisher_id")
    private Long publisherId;

    /** 发布人姓名快照 */
    @TableField("publisher_name")
    private String publisherName;

    /** 是否推送大屏轮播：1 推送 / 0 不推送 */
    @TableField("screen_show")
    private Integer screenShow;

    /** 大屏停留秒数 */
    @TableField("screen_duration")
    private Integer screenDuration;

    /** 排序号（越小越靠前） */
    @TableField("sort_no")
    private Integer sortNo;

    /** 阅读次数 */
    @TableField("read_count")
    private Integer readCount;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
