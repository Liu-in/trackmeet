package com.trackmeet.modules.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 公告发布 / 下线 / 转草稿入参。
 * <p>
 * 发布（{@code PUBLISHED}）时服务层写入：{@code publish_time}（原值为空时取当前时间）、
 * {@code publisher_id} / {@code publisher_name}（取自登录态）；下线（{@code OFFLINE}）时置
 * {@code status = OFFLINE} 并保留发布人留痕。
 *
 * @author TrackMeet
 */
@Data
public class AnnouncementPublishDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：PUBLISHED 发布 / OFFLINE 下线 / DRAFT 转草稿 */
    @NotBlank(message = "目标状态不能为空")
    private String status;

    /** 是否推送大屏轮播：1 推送 / 0 不推送（不传则保持原值） */
    private Integer screenShow;

    /** 大屏停留秒数（不传则保持原值） */
    private Integer screenDuration;
}
