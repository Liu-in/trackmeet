package com.trackmeet.modules.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公告新增 / 修改入参。
 * <p>
 * {@code contentHtml} 为富文本原文，<b>服务端会做白名单净化（删除 script/iframe/svg 等危险元素、{@code on*} 事件属性与
 * {@code javascript:} 协议）后入库并返回</b>（前端 {@code v-html} 前的清洗为第二道防线）。
 * {@code status} 不在此 DTO 中：新建落 {@code DRAFT}，发布 / 下线走
 * {@code PUT /api/announcements/{id}/publish}。
 *
 * @author TrackMeet
 */
@Data
public class AnnouncementSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（可选，缺省取 X-Meet-Id 届次上下文；传入时必须与上下文一致） */
    private Long meetId;

    /** 公告标题（必填，最长 100 字符） */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题最长 100 个字符")
    private String title;

    /** 公告正文富文本（写入前由服务端白名单净化） */
    private String contentHtml;

    /** 公告类型：NOTICE 通知（默认）/ RESULT 成绩 / SCHEDULE 日程 / URGENT 紧急 */
    private String noticeType;

    /** 可见范围：PUBLIC 公开（默认）/ LEADER 领队可见 / REFEREE 裁判可见 */
    private String scope;

    /** 是否置顶：1 置顶 / 0 不置顶（缺省 0） */
    private Integer isTop;

    /** 失效时间，空为长期有效 */
    private LocalDateTime expireTime;

    /** 是否推送大屏轮播：1 推送 / 0 不推送（缺省 0） */
    private Integer screenShow;

    /** 大屏停留秒数 */
    private Integer screenDuration;

    /** 排序号（越小越靠前，缺省 0） */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 200, message = "备注最长 200 个字符")
    private String remark;
}
