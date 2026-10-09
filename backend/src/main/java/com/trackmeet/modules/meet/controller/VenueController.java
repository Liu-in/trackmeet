package com.trackmeet.modules.meet.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.meet.entity.Venue;
import com.trackmeet.modules.meet.service.VenueService;
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
 * 场地接口：列表、详情、新增、修改、删除。
 * 权限编码：meet:venue:list / meet:venue:add / meet:venue:edit / meet:venue:delete。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@Validated
public class VenueController {

    private final VenueService venueService;

    /**
     * 场地列表（编排页面据此选择场地与道数）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('meet:venue:list')")
    public R<List<Venue>> list(@RequestParam(value = "meetId", required = false) Long meetId,
                               @RequestParam(value = "venueType", required = false) String venueType) {
        return R.ok(venueService.listByMeet(meetId, venueType));
    }

    /**
     * 场地详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('meet:venue:list')")
    public R<Venue> detail(@PathVariable("id") Long id) {
        return R.ok(venueService.getById(id));
    }

    /**
     * 新增场地。
     */
    @PostMapping
    @PreAuthorize("@perm.has('meet:venue:add')")
    @OpLog(module = "MEET", action = "ADD", actionName = "新增场地", targetType = "venue")
    public R<Long> add(@Valid @RequestBody Venue venue) {
        return R.ok(venueService.createVenue(venue));
    }

    /**
     * 修改场地。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('meet:venue:edit')")
    @OpLog(module = "MEET", action = "EDIT", actionName = "修改场地", targetType = "venue")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody Venue venue) {
        venueService.updateVenue(id, venue);
        return R.ok();
    }

    /**
     * 删除场地。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('meet:venue:delete')")
    @OpLog(module = "MEET", action = "DELETE", actionName = "删除场地", targetType = "venue")
    public R<Void> remove(@PathVariable("id") Long id) {
        venueService.removeVenue(id);
        return R.ok();
    }
}
