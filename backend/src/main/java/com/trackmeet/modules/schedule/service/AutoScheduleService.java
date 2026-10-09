package com.trackmeet.modules.schedule.service;

import com.trackmeet.modules.schedule.dto.ScheduleAutoDTO;
import com.trackmeet.modules.schedule.vo.AutoPlanVO;

/**
 * 日程自动编排服务：装配「单元 + 任务」→ 调用 {@code SchedulePacker} → 预览或落库。
 *
 * @author TrackMeet
 */
public interface AutoScheduleService {

    /**
     * 执行自动编排。
     * <p>
     * {@code apply=false} 仅返回预览（数据库无任何写入）；{@code apply=true} 事务内落库：
     * 锁定日程（{@code is_locked=1}）保持不变，其它日程按 {@code (meet_id, event_id, round_id)} 匹配更新或插入。
     *
     * @param dto 入参（meetId / scope / sessionIds / groupIds / eventIds / keepLocked /
     *            athleteIntervalMin / parallelFieldEvents / apply）
     * @return 编排计划视图
     */
    AutoPlanVO autoSchedule(ScheduleAutoDTO dto);

    /**
     * 重新生成日程（强制 {@code apply=true}）。
     *
     * @param dto 入参
     * @return 编排计划视图
     */
    AutoPlanVO regenerate(ScheduleAutoDTO dto);
}
