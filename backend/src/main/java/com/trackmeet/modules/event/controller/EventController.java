package com.trackmeet.modules.event.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.event.dto.EventBatchGenerateDTO;
import com.trackmeet.modules.event.dto.EventQuery;
import com.trackmeet.modules.event.dto.EventRoundSaveDTO;
import com.trackmeet.modules.event.dto.EventSaveDTO;
import com.trackmeet.modules.event.service.EventRoundService;
import com.trackmeet.modules.event.service.EventService;
import com.trackmeet.modules.event.vo.EventBatchGenerateResultVO;
import com.trackmeet.modules.event.vo.EventDetailVO;
import com.trackmeet.modules.event.vo.EventRoundVO;
import com.trackmeet.modules.event.vo.EventVO;
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

import java.util.List;

/**
 * 项目管理接口（含项目内轮次列表与新增轮次）。
 * <p>
 * 接口与权限编码对照：分页 {@code event:list}、详情 {@code event:query}、
 * 新增 {@code event:add}、修改 {@code event:edit}、删除 {@code event:delete}、
 * 批量生成 {@code event:batch}、轮次列表 {@code event:round:list}、新增轮次 {@code event:round:add}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Validated
public class EventController {

    private final EventService eventService;
    private final EventRoundService eventRoundService;

    /**
     * 项目分页列表（groupId、eventType、gender、subType、status、keyword）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('event:list')")
    public R<PageResult<EventVO>> page(EventQuery query) {
        return R.ok(eventService.pageEvent(query));
    }

    /**
     * 项目详情（项目 + 轮次列表 + 项目级规则 + 届次默认规则）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('event:query', 'event:list')")
    public R<EventDetailVO> detail(@PathVariable Long id) {
        return R.ok(eventService.getDetail(id));
    }

    /**
     * 新增项目（{@code event_code} 届次内唯一，默认 status=ENABLED、round_count=1）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('event:add')")
    @OpLog(module = "EVENT", action = "ADD", actionName = "新增项目", targetType = "event")
    public R<Long> add(@Valid @RequestBody EventSaveDTO dto) {
        return R.ok(eventService.createEvent(dto));
    }

    /**
     * 修改项目。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('event:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "修改项目", targetType = "event")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody EventSaveDTO dto) {
        eventService.updateEvent(id, dto);
        return R.ok();
    }

    /**
     * 删除项目（项目下存在未取消报名或被日程引用时返回 CONFLICT）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('event:delete')")
    @OpLog(module = "EVENT", action = "DELETE", actionName = "删除项目", targetType = "event")
    public R<Void> remove(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return R.ok();
    }

    /**
     * 批量生成标准项目（组别列表 × 性别 × 模板列表，已存在的同名项目跳过）。
     */
    @PostMapping("/batch")
    @PreAuthorize("@perm.has('event:batch')")
    @OpLog(module = "EVENT", action = "BATCH", actionName = "批量生成标准项目", targetType = "event")
    public R<EventBatchGenerateResultVO> batch(@Valid @RequestBody EventBatchGenerateDTO dto) {
        return R.ok(eventService.batchGenerate(dto));
    }

    /**
     * 项目下的轮次列表（按 round_no 升序，含轮次级规则）。
     */
    @GetMapping("/{id}/rounds")
    @PreAuthorize("@perm.has('event:round:list')")
    public R<List<EventRoundVO>> rounds(@PathVariable Long id) {
        return R.ok(eventRoundService.listByEventId(id));
    }

    /**
     * 新增轮次（round_code 项目内唯一，自动维护 round_no 顺序与 is_final）。
     */
    @PostMapping("/{id}/rounds")
    @PreAuthorize("@perm.has('event:round:add')")
    @OpLog(module = "EVENT", action = "ADD", actionName = "新增轮次", targetType = "event_round")
    public R<Long> addRound(@PathVariable Long id, @Valid @RequestBody EventRoundSaveDTO dto) {
        return R.ok(eventRoundService.createRound(id, dto));
    }
}
