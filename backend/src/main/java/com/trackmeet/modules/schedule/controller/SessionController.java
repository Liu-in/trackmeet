package com.trackmeet.modules.schedule.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.schedule.dto.SessionQuery;
import com.trackmeet.modules.schedule.dto.SessionSaveDTO;
import com.trackmeet.modules.schedule.service.ScheduleSessionService;
import com.trackmeet.modules.schedule.vo.SessionVO;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 竞赛单元接口（自动编排的时间容器）。
 * <p>
 * 接口与权限编码对照：分页 / 详情 {@code schedule:session:list}、新增 {@code schedule:session:add}、
 * 修改 {@code schedule:session:edit}、删除 {@code schedule:session:delete}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Validated
public class SessionController {

    private final ScheduleSessionService scheduleSessionService;

    /**
     * 单元分页列表（meetId、date、keyword；同时支持 period / venueId / dayNo / publishStatus / isLocked）。
     */
    @GetMapping("")
    @PreAuthorize("@perm.has('schedule:session:list')")
    public R<PageResult<SessionVO>> page(SessionQuery query) {
        return R.ok(scheduleSessionService.pageSession(query));
    }

    /**
     * 单元详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('schedule:session:list')")
    public R<SessionVO> detail(@PathVariable Long id) {
        return R.ok(scheduleSessionService.toVoList(
                java.util.Collections.singletonList(scheduleSessionService.getEntity(id))).get(0));
    }

    /**
     * 新增单元（{@code (meet_id, session_date, period)} 届次内唯一）。
     */
    @PostMapping("")
    @PreAuthorize("@perm.has('schedule:session:add')")
    @OpLog(module = "SCHEDULE", action = "ADD", actionName = "新增竞赛单元", targetType = "session")
    public R<Long> add(@Valid @RequestBody SessionSaveDTO dto) {
        return R.ok(scheduleSessionService.createSession(dto));
    }

    /**
     * 修改单元（锁定单元只允许调整锁定/发布相关字段）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('schedule:session:edit')")
    @OpLog(module = "SCHEDULE", action = "EDIT", actionName = "修改竞赛单元", targetType = "session")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody SessionSaveDTO dto) {
        scheduleSessionService.updateSession(id, dto);
        return R.ok();
    }

    /**
     * 删除单元（单元下存在日程条目或单元已锁定时返回 CONFLICT）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('schedule:session:delete')")
    @OpLog(module = "SCHEDULE", action = "DELETE", actionName = "删除竞赛单元", targetType = "session")
    public R<Void> remove(@PathVariable Long id) {
        scheduleSessionService.deleteSession(id);
        return R.ok();
    }
}
