package com.trackmeet.modules.meet.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.meet.dto.MeetGroupQuery;
import com.trackmeet.modules.meet.entity.MeetGroup;
import com.trackmeet.modules.meet.service.MeetGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组别管理接口（新增走 /api/meets/{id}/groups）。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Validated
public class MeetGroupController {

    private final MeetGroupService meetGroupService;

    /**
     * 组别分页列表（meetId、gender、keyword）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('meet:group:list')")
    public R<PageResult<MeetGroup>> page(MeetGroupQuery query) {
        return R.ok(meetGroupService.pageQuery(query));
    }

    /**
     * 组别详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('meet:group:query')")
    public R<MeetGroup> detail(@PathVariable Long id) {
        return R.ok(meetGroupService.getDetail(id));
    }

    /**
     * 修改组别。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('meet:group:edit')")
    @OpLog(module = "MEET", action = "EDIT", actionName = "修改组别", targetType = "meet_group")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody MeetGroup group) {
        meetGroupService.update(id, group);
        return R.ok();
    }

    /**
     * 删除组别（先改写 group_code 释放唯一键，再逻辑删除）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('meet:group:delete')")
    @OpLog(module = "MEET", action = "DELETE", actionName = "删除组别", targetType = "meet_group")
    public R<Void> remove(@PathVariable Long id) {
        meetGroupService.delete(id);
        return R.ok();
    }
}
