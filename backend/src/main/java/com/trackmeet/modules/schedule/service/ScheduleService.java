package com.trackmeet.modules.schedule.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.modules.schedule.dto.ScheduleMoveDTO;
import com.trackmeet.modules.schedule.dto.ScheduleQuery;
import com.trackmeet.modules.schedule.dto.ScheduleSaveDTO;
import com.trackmeet.modules.schedule.entity.Schedule;
import com.trackmeet.modules.schedule.vo.GanttRowVO;
import com.trackmeet.modules.schedule.vo.ScheduleVO;

import java.util.List;

/**
 * 日程条目服务：分页/详情/甘特视图/手工增删改/拖拽移动/锁定/发布。
 *
 * @author TrackMeet
 */
public interface ScheduleService extends IService<Schedule> {

    /**
     * 日程分页查询：sessionId、venueId、date（按 {@code plan_start_time} 的日期）、groupId、
     * eventId、roundId、publishStatus、isLocked、conflictFlag、sourceType、keyword（项目名/轮次码）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ScheduleVO> pageSchedule(ScheduleQuery query);

    /**
     * 日程详情（含分组、组内人员与道次/出场顺序）。
     *
     * @param id 日程 ID
     * @return 日程视图
     */
    ScheduleVO getDetail(Long id);

    /**
     * 日程视图列表（批量，含项目/单元/场地冗余字段），供甘特视图与自动编排结果装配复用。
     *
     * @param meetId     届次 ID
     * @param sessionIds 单元过滤（为空取全部）
     * @return 日程视图列表
     */
    List<ScheduleVO> listDetail(Long meetId, List<Long> sessionIds);

    /**
     * 甘特/时间轴数据：按 单元 → 场地 分组，单元内条目按计划开始时间升序。
     *
     * @param meetId    届次 ID
     * @param sessionId 单元过滤（可空）
     * @return 甘特行列表
     */
    List<GanttRowVO> gantt(Long meetId, Long sessionId);

    /**
     * 新增日程：校验单元/项目/轮次同届次，派生预计时长、组数、单元内排序号与单元统计。
     *
     * @param dto 入参
     * @return 新增主键
     */
    Long createSchedule(ScheduleSaveDTO dto);

    /**
     * 修改日程：锁定日程抛 {@code CONFLICT}；不允许跨届次。
     *
     * @param id  日程 ID
     * @param dto 入参
     */
    void updateSchedule(Long id, ScheduleSaveDTO dto);

    /**
     * 删除日程：级联物理删除分组、组内人员、道次与田赛出场顺序；锁定日程抛 {@code CONFLICT}。
     *
     * @param id 日程 ID
     */
    void deleteSchedule(Long id);

    /**
     * 拖拽移动日程：{@code is_locked=1} 时抛 {@code BizException(CONFLICT,"日程已锁定，请先解锁")}；
     * 成功时 {@code move_count + 1} 并回填最近调整原因。
     *
     * @param id  日程 ID
     * @param dto 入参（sessionId / startTime / venueId / adjustReason）
     */
    void moveSchedule(Long id, ScheduleMoveDTO dto);

    /**
     * 锁定/解锁日程。
     *
     * @param id       日程 ID
     * @param isLocked 0 未锁定 / 1 已锁定
     */
    void lockSchedule(Long id, Integer isLocked);

    /**
     * 发布/撤回日程（同步更新发布人、发布时间与所属单元的发布状态）。
     *
     * @param id            日程 ID
     * @param publishStatus DRAFT / PUBLISHED
     */
    void publishSchedule(Long id, String publishStatus);

    /**
     * 日程实体存在性校验（不存在抛 404，跨届次抛 403）。
     *
     * @param id 日程 ID
     * @return 日程实体
     */
    Schedule getEntity(Long id);

    /**
     * 重算单元的已排条目数与已用时长的派生字段（手工增删改或自动编排后调用）。
     *
     * @param meetId    届次 ID
     * @param sessionId 单元 ID
     */
    void refreshSessionStat(Long meetId, Long sessionId);
}
