package com.trackmeet.modules.schedule.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.schedule.dto.SessionQuery;
import com.trackmeet.modules.schedule.dto.SessionSaveDTO;
import com.trackmeet.modules.schedule.entity.ScheduleSession;
import com.trackmeet.modules.schedule.vo.SessionVO;

import java.util.List;

/**
 * 竞赛单元服务：单元 CRUD（自动编排的时间容器）。
 *
 * @author TrackMeet
 */
public interface ScheduleSessionService extends IService<ScheduleSession> {

    /**
     * 单元分页查询（date、period、venueId、dayNo、publishStatus、isLocked、keyword）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SessionVO> pageSession(SessionQuery query);

    /**
     * 单元列表（自动编排装配用，按 日期 → 时段 → 开始时刻 排序）。
     *
     * @param meetId     届次 ID（为空取当前届次上下文）
     * @param sessionIds 指定单元 ID（为空取全部）
     * @param onlyUnlocked 是否只取未锁定单元
     * @return 单元实体列表（永不为 null）
     */
    List<ScheduleSession> listSessions(Long meetId, List<Long> sessionIds, boolean onlyUnlocked);

    /**
     * 单元实体存在性校验（不存在抛 404，跨届次抛 403）。
     *
     * @param id 单元 ID
     * @return 单元实体
     */
    ScheduleSession getEntity(Long id);

    /**
     * 新增单元：{@code (meet_id, session_date, period)} 届次内唯一；
     * {@code day_no} 缺省按比赛开始日期推算，{@code total_minutes} 缺省按起止时刻计算。
     *
     * @param dto 入参
     * @return 新增主键
     */
    Long createSession(SessionSaveDTO dto);

    /**
     * 修改单元（不允许跨届次；锁定单元仅允许调整锁定/发布字段）。
     *
     * @param id  单元 ID
     * @param dto 入参
     */
    void updateSession(Long id, SessionSaveDTO dto);

    /**
     * 删除单元：单元下存在日程条目时抛 {@code CONFLICT}；已锁定单元抛 {@code CONFLICT}。
     *
     * @param id 单元 ID
     */
    void deleteSession(Long id);

    /**
     * 单元视图对象列表（冗余主场地与可用场地名称）。
     *
     * @param sessions 单元实体列表
     * @return 视图对象列表
     */
    List<SessionVO> toVoList(List<ScheduleSession> sessions);
}
