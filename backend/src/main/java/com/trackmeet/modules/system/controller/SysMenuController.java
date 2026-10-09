package com.trackmeet.modules.system.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.system.dto.MenuSaveDTO;
import com.trackmeet.modules.system.dto.SysMenuQuery;
import com.trackmeet.modules.system.entity.SysMenu;
import com.trackmeet.modules.system.service.SysMenuService;
import com.trackmeet.modules.system.vo.MenuTreeVO;
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
 * 菜单管理接口。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
@Validated
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /**
     * 菜单树（默认全部启用菜单，可按 keyword / menuType / status 过滤）。
     */
    @GetMapping("/tree")
    @PreAuthorize("@perm.has('system:menu:list')")
    public R<List<MenuTreeVO>> tree(SysMenuQuery query) {
        return R.ok(sysMenuService.menuTree(query));
    }

    /**
     * 菜单详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('system:menu:query')")
    public R<SysMenu> detail(@PathVariable("id") Long id) {
        return R.ok(sysMenuService.getMenuDetail(id));
    }

    /**
     * 新增菜单。
     */
    @PostMapping
    @PreAuthorize("@perm.has('system:menu:add')")
    @OpLog(module = "SYSTEM", action = "ADD", actionName = "新增菜单", targetType = "menu")
    public R<Long> add(@Valid @RequestBody MenuSaveDTO dto) {
        return R.ok(sysMenuService.createMenu(dto));
    }

    /**
     * 修改菜单。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('system:menu:edit')")
    @OpLog(module = "SYSTEM", action = "EDIT", actionName = "修改菜单", targetType = "menu")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody MenuSaveDTO dto) {
        sysMenuService.updateMenu(id, dto);
        return R.ok();
    }

    /**
     * 删除菜单，存在子节点时禁止删除。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('system:menu:delete')")
    @OpLog(module = "SYSTEM", action = "DELETE", actionName = "删除菜单", targetType = "menu")
    public R<Void> delete(@PathVariable("id") Long id) {
        sysMenuService.deleteMenu(id);
        return R.ok();
    }
}
