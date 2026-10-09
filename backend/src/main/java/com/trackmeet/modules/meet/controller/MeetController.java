package com.trackmeet.modules.meet.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.meet.dto.MeetQuery;
import com.trackmeet.modules.meet.entity.Meet;
import com.trackmeet.modules.meet.entity.MeetGroup;
import com.trackmeet.modules.meet.service.MeetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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
 * 运动会（届次）管理接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/meets")
@RequiredArgsConstructor
@Validated
public class MeetController {

    private final MeetService meetService;

    /**
     * 运动会分页列表（keyword、status、year）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('meet:list')")
    public R<PageResult<Meet>> page(MeetQuery query) {
        return R.ok(meetService.pageQuery(query));
    }

    /**
     * 运动会详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('meet:query')")
    public R<Meet> detail(@PathVariable Long id) {
        return R.ok(meetService.getDetail(id));
    }

    /**
     * 新增运动会（状态默认 DRAFT）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('meet:add')")
    @OpLog(module = "MEET", action = "ADD", actionName = "新增运动会", targetType = "meet")
    public R<Long> add(@Valid @RequestBody Meet meet) {
        return R.ok(meetService.create(meet));
    }

    /**
     * 修改运动会。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('meet:edit')")
    @OpLog(module = "MEET", action = "EDIT", actionName = "修改运动会", targetType = "meet")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody Meet meet) {
        meetService.update(id, meet);
        return R.ok();
    }

    /**
     * 删除运动会（仅 DRAFT / ARCHIVED 允许）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('meet:delete')")
    @OpLog(module = "MEET", action = "DELETE", actionName = "删除运动会", targetType = "meet")
    public R<Void> remove(@PathVariable Long id) {
        meetService.delete(id);
        return R.ok();
    }

    /**
     * 届次状态流转（按状态机顺序前进一步）。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('meet:status')")
    @OpLog(module = "MEET", action = "STATUS", actionName = "届次状态流转", targetType = "meet")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody MeetStatusDTO dto) {
        meetService.changeStatus(id, dto.getStatus());
        return R.ok();
    }

    /**
     * 该届次下的组别列表（不分页）。
     */
    @GetMapping("/{id}/groups")
    @PreAuthorize("@perm.has('meet:group:list')")
    public R<List<MeetGroup>> groups(@PathVariable Long id) {
        return R.ok(meetService.listGroups(id));
    }

    /**
     * 在该届次下新增组别（自动设置 meetId）。
     */
    @PostMapping("/{id}/groups")
    @PreAuthorize("@perm.has('meet:group:add')")
    @OpLog(module = "MEET", action = "ADD", actionName = "新增组别", targetType = "meet_group")
    public R<Long> addGroup(@PathVariable Long id, @Valid @RequestBody MeetGroup group) {
        return R.ok(meetService.addGroup(id, group));
    }

    /**
     * 状态流转请求体。
     */
    @Data
    public static class MeetStatusDTO {

        /** 目标状态：REG_OPEN/REG_CLOSED/SCHEDULING/SCHEDULED/RUNNING/FINISHED/ARCHIVED */
        @NotBlank(message = "目标状态不能为空")
        private String status;
    }
}
