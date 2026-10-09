package com.trackmeet.modules.schedule.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.schedule.dto.ScheduleAutoDTO;
import com.trackmeet.modules.schedule.dto.ScheduleLockDTO;
import com.trackmeet.modules.schedule.dto.ScheduleMoveDTO;
import com.trackmeet.modules.schedule.dto.SchedulePublishDTO;
import com.trackmeet.modules.schedule.dto.ScheduleQuery;
import com.trackmeet.modules.schedule.dto.ScheduleSaveDTO;
import com.trackmeet.modules.schedule.service.AutoScheduleService;
import com.trackmeet.modules.schedule.service.ScheduleService;
import com.trackmeet.modules.schedule.vo.AutoPlanVO;
import com.trackmeet.modules.schedule.vo.GanttRowVO;
import com.trackmeet.modules.schedule.vo.ScheduleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日程编排接口：分页 / 详情 / 甘特视图 / 手工增删改 / 拖拽移动 / 锁定 / 发布 / 自动编排。
 * <p>
 * 接口与权限编码对照：查询类 {@code schedule:list}、新增 {@code schedule:add}、
 * 修改与拖拽 {@code schedule:edit}、删除 {@code schedule:delete}、
 * 自动编排 {@code schedule:auto}、锁定 {@code schedule:lock}、发布 {@code schedule:publish}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
@Validated
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final AutoScheduleService autoScheduleService;

    /**
     * 日程分页（sessionId / venueId / date / groupId / eventId / publishStatus / keyword）。
     * <p>
     * {@code date} 按 {@code schedule.plan_start_time} 的日期过滤。
     */
    @GetMapping("")
    @PreAuthorize("@perm.has('schedule:list')")
    public R<PageResult<ScheduleVO>> page(ScheduleQuery query) {
        return R.ok(scheduleService.pageSchedule(query));
    }

    /**
     * 日程详情（含分组、组内人员、道次与田赛出场顺序）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('schedule:list')")
    public R<ScheduleVO> detail(@PathVariable Long id) {
        return R.ok(scheduleService.getDetail(id));
    }

    /**
     * 甘特/时间轴数据（按 单元 → 场地 分组）。
     */
    @GetMapping("/gantt")
    @PreAuthorize("@perm.has('schedule:list')")
    public R<List<GanttRowVO>> gantt(@RequestParam(required = false) Long meetId,
                                     @RequestParam(required = false) Long sessionId) {
        return R.ok(scheduleService.gantt(meetId, sessionId));
    }

    /**
     * 新增日程条目。
     */
    @PostMapping("")
    @PreAuthorize("@perm.has('schedule:add')")
    @OpLog(module = "SCHEDULE", action = "ADD", actionName = "新增日程", targetType = "schedule")
    public R<Long> add(@Valid @RequestBody ScheduleSaveDTO dto) {
        return R.ok(scheduleService.createSchedule(dto));
    }

    /**
     * 修改日程条目（锁定日程返回 CONFLICT）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('schedule:edit')")
    @OpLog(module = "SCHEDULE", action = "EDIT", actionName = "修改日程", targetType = "schedule")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody ScheduleSaveDTO dto) {
        scheduleService.updateSchedule(id, dto);
        return R.ok();
    }

    /**
     * 删除日程条目（级联清理分组、组内人员、道次与出场顺序）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('schedule:delete')")
    @OpLog(module = "SCHEDULE", action = "DELETE", actionName = "删除日程", targetType = "schedule")
    public R<Void> remove(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return R.ok();
    }

    /**
     * 拖拽移动日程；{@code is_locked=1} 时返回 CONFLICT（「日程已锁定，请先解锁」），
     * 成功时 {@code move_count + 1}。
     */
    @PostMapping("/{id}/move")
    @PreAuthorize("@perm.has('schedule:edit')")
    @OpLog(module = "SCHEDULE", action = "MOVE", actionName = "拖拽移动日程", targetType = "schedule")
    public R<Void> move(@PathVariable Long id, @RequestBody ScheduleMoveDTO dto) {
        scheduleService.moveSchedule(id, dto);
        return R.ok();
    }

    /**
     * 锁定/解锁日程（body：{@code {"isLocked":true|false}}）。
     */
    @PutMapping("/{id}/lock")
    @PreAuthorize("@perm.has('schedule:lock')")
    @OpLog(module = "SCHEDULE", action = "LOCK", actionName = "锁定日程", targetType = "schedule")
    public R<Void> lock(@PathVariable Long id, @Valid @RequestBody ScheduleLockDTO dto) {
        scheduleService.lockSchedule(id, dto.getIsLocked());
        return R.ok();
    }

    /**
     * 发布/撤回日程（body：{@code {"publishStatus":"PUBLISHED"|"DRAFT"}}）。
     */
    @PutMapping("/{id}/publish")
    @PreAuthorize("@perm.has('schedule:publish')")
    @OpLog(module = "SCHEDULE", action = "PUBLISH", actionName = "发布日程", targetType = "schedule")
    public R<Void> publish(@PathVariable Long id, @Valid @RequestBody SchedulePublishDTO dto) {
        scheduleService.publishSchedule(id, dto.getPublishStatus());
        return R.ok();
    }

    /**
     * 自动编排（{@code apply=false} 预览 / {@code true} 落库；锁定日程保持不变）。
     */
    @PostMapping("/auto")
    @PreAuthorize("@perm.has('schedule:auto')")
    @OpLog(module = "SCHEDULE", action = "AUTO", actionName = "自动编排日程", targetType = "schedule")
    public R<AutoPlanVO> auto(@RequestBody ScheduleAutoDTO dto) {
        return R.ok(autoScheduleService.autoSchedule(dto));
    }

    /**
     * 重新生成日程（强制 apply=true，锁定日程保持不变）。
     */
    @PostMapping("/regenerate")
    @PreAuthorize("@perm.has('schedule:auto')")
    @OpLog(module = "SCHEDULE", action = "REGENERATE", actionName = "重新生成日程", targetType = "schedule")
    public R<AutoPlanVO> regenerate(@RequestBody ScheduleAutoDTO dto) {
        return R.ok(autoScheduleService.regenerate(dto));
    }
}
