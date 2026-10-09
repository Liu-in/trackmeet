package com.trackmeet.modules.system.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.system.dto.DictItemSaveDTO;
import com.trackmeet.modules.system.dto.DictTypeQuery;
import com.trackmeet.modules.system.dto.DictTypeSaveDTO;
import com.trackmeet.modules.system.entity.SysDictType;
import com.trackmeet.modules.system.service.SysDictService;
import com.trackmeet.modules.system.vo.DictItemVO;
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
 * 字典管理接口：字典类型 + 字典项。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/system/dicts")
@RequiredArgsConstructor
@Validated
public class SysDictController {

    private final SysDictService sysDictService;

    /**
     * 字典类型分页列表（条件：keyword、dictGroup、status）。
     */
    @GetMapping("/types")
    @PreAuthorize("@perm.has('system:dict:list')")
    public R<PageResult<SysDictType>> pageTypes(DictTypeQuery query) {
        return R.ok(sysDictService.pageTypes(query));
    }

    /**
     * 字典类型详情。
     */
    @GetMapping("/types/{id}")
    @PreAuthorize("@perm.has('system:dict:list')")
    public R<SysDictType> getType(@PathVariable("id") Long id) {
        return R.ok(sysDictService.getType(id));
    }

    /**
     * 新增字典类型。
     */
    @PostMapping("/types")
    @PreAuthorize("@perm.has('system:dict:add')")
    @OpLog(module = "SYSTEM", action = "ADD", actionName = "新增字典类型", targetType = "dictType")
    public R<Long> addType(@Valid @RequestBody DictTypeSaveDTO dto) {
        return R.ok(sysDictService.createType(dto));
    }

    /**
     * 修改字典类型。
     */
    @PutMapping("/types/{id}")
    @PreAuthorize("@perm.has('system:dict:edit')")
    @OpLog(module = "SYSTEM", action = "EDIT", actionName = "修改字典类型", targetType = "dictType")
    public R<Void> editType(@PathVariable("id") Long id, @Valid @RequestBody DictTypeSaveDTO dto) {
        sysDictService.updateType(id, dto);
        return R.ok();
    }

    /**
     * 删除字典类型（系统内置禁止删除，级联逻辑删除其下字典项）。
     */
    @DeleteMapping("/types/{id}")
    @PreAuthorize("@perm.has('system:dict:delete')")
    @OpLog(module = "SYSTEM", action = "DELETE", actionName = "删除字典类型", targetType = "dictType")
    public R<Void> deleteType(@PathVariable("id") Long id) {
        sysDictService.deleteType(id);
        return R.ok();
    }

    /**
     * 按字典类型编码列出启用字典项（登录即可访问，无需按钮权限）。
     */
    @GetMapping("/items")
    public R<List<DictItemVO>> listItems(@RequestParam("dictType") String dictType) {
        return R.ok(sysDictService.listItemsByType(dictType));
    }

    /**
     * 新增字典项。
     */
    @PostMapping("/items")
    @PreAuthorize("@perm.has('system:dict:add')")
    @OpLog(module = "SYSTEM", action = "ADD", actionName = "新增字典项", targetType = "dictItem")
    public R<Long> addItem(@Valid @RequestBody DictItemSaveDTO dto) {
        return R.ok(sysDictService.createItem(dto));
    }

    /**
     * 修改字典项。
     */
    @PutMapping("/items/{id}")
    @PreAuthorize("@perm.has('system:dict:edit')")
    @OpLog(module = "SYSTEM", action = "EDIT", actionName = "修改字典项", targetType = "dictItem")
    public R<Void> editItem(@PathVariable("id") Long id, @Valid @RequestBody DictItemSaveDTO dto) {
        sysDictService.updateItem(id, dto);
        return R.ok();
    }

    /**
     * 删除字典项（逻辑删除）。
     */
    @DeleteMapping("/items/{id}")
    @PreAuthorize("@perm.has('system:dict:delete')")
    @OpLog(module = "SYSTEM", action = "DELETE", actionName = "删除字典项", targetType = "dictItem")
    public R<Void> deleteItem(@PathVariable("id") Long id) {
        sysDictService.deleteItem(id);
        return R.ok();
    }
}
