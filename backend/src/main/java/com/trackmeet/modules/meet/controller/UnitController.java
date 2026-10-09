package com.trackmeet.modules.meet.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.meet.dto.UnitQuery;
import com.trackmeet.modules.meet.entity.Unit;
import com.trackmeet.modules.meet.service.UnitService;
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
 * 参赛单位管理接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
@Validated
public class UnitController {

    private final UnitService unitService;

    /**
     * 参赛单位分页列表（meetId、keyword）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('meet:unit:list')")
    public R<PageResult<Unit>> page(UnitQuery query) {
        return R.ok(unitService.pageQuery(query));
    }

    /**
     * 参赛单位详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('meet:unit:query')")
    public R<Unit> detail(@PathVariable Long id) {
        return R.ok(unitService.getDetail(id));
    }

    /**
     * 新增参赛单位。
     */
    @PostMapping
    @PreAuthorize("@perm.has('meet:unit:add')")
    @OpLog(module = "MEET", action = "ADD", actionName = "新增参赛单位", targetType = "unit")
    public R<Long> add(@Valid @RequestBody Unit unit) {
        return R.ok(unitService.create(unit));
    }

    /**
     * 修改参赛单位。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('meet:unit:edit')")
    @OpLog(module = "MEET", action = "EDIT", actionName = "修改参赛单位", targetType = "unit")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody Unit unit) {
        unitService.update(id, unit);
        return R.ok();
    }

    /**
     * 删除参赛单位（先改写 unit_code 释放唯一键，再逻辑删除）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('meet:unit:delete')")
    @OpLog(module = "MEET", action = "DELETE", actionName = "删除参赛单位", targetType = "unit")
    public R<Void> remove(@PathVariable Long id) {
        unitService.delete(id);
        return R.ok();
    }
}
