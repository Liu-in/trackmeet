package com.trackmeet.modules.print.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 轻量投影：仅「一个主键 + 一段文本」，用于把小参照表（如竞赛单元的
 * {@code venue_ids_json}）一次批量装入内存，避免新增 N+1 查询。
 * <p>
 * 典型用法：{@code PrintMapper.selectSessionVenueIds} 返回若干条
 * {@code (id = schedule_session.id, text = venue_ids_json)}，
 * 服务层再按日程的 {@code session_id} 建立内存映射。
 *
 * @author TrackMeet
 */
@Data
public class LiteProjection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private Long id;

    /** 文本值（原样返回，由调用方决定如何解析，解析失败降级为空） */
    private String text;
}
