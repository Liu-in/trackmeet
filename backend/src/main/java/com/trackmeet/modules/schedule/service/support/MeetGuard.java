package com.trackmeet.modules.schedule.service.support;

import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;

import java.util.Objects;

/**
 * 编排模块的届次隔离守卫。
 * <p>
 * 口径与其它模块一致：入参 meetId 优先，缺省取 {@link MeetContext}；
 * 两者都存在且不一致时按越权处理（{@code FORBIDDEN}）。
 * 读取既有实体后还需调用 {@link #check(Long)} 做二次核对，防止绕过过滤器直连其它届次数据。
 *
 * @author TrackMeet
 */
public final class MeetGuard {

    private MeetGuard() {
    }

    /**
     * 解析并校验届次 ID。
     *
     * @param meetId 入参届次 ID（可空）
     * @return 生效届次 ID
     */
    public static Long resolve(Long meetId) {
        Long contextMeetId = MeetContext.get();
        Long resolved = meetId != null ? meetId : contextMeetId;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次 ID 不能为空");
        }
        if (contextMeetId != null && !Objects.equals(contextMeetId, resolved)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的编排数据");
        }
        return resolved;
    }

    /**
     * 校验实体所属届次与当前上下文一致。
     *
     * @param meetId 实体届次 ID
     */
    public static void check(Long meetId) {
        Long contextMeetId = MeetContext.get();
        if (contextMeetId != null && meetId != null && !Objects.equals(contextMeetId, meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作其他届次的编排数据");
        }
    }
}
