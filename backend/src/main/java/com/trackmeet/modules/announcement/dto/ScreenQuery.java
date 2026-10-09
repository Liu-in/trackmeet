package com.trackmeet.modules.announcement.dto;

import com.trackmeet.common.base.BaseQuery;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 大屏查询条件：登录态大屏（{@code /api/screen/board}）与匿名大屏
 * （{@code /api/screen/public/board}）共用。
 * <p>
 * 登录态接口的 {@code meetId} 必须与 {@code MeetContext.get()} 一致；
 * 匿名接口按 {@code meetId} 取数并校验 {@code token}，不依赖登录态。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScreenQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填） */
    @NotNull(message = "届次不能为空")
    private Long meetId;

    /** 大屏访问令牌（仅匿名接口 /api/screen/public/board 使用） */
    private String token;
}
