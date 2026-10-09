package com.trackmeet.modules.athlete.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.athlete.dto.AthleteQuery;
import com.trackmeet.modules.athlete.entity.Athlete;
import com.trackmeet.modules.athlete.service.AthleteService;
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
 * 运动员接口：分页、详情、新增、修改、删除。
 * 权限编码：athlete:list / athlete:query / athlete:add / athlete:edit / athlete:delete。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/athletes")
@RequiredArgsConstructor
@Validated
public class AthleteController {

    private final AthleteService athleteService;

    /**
     * 运动员分页列表。
     * 条件：keyword（姓名/证件号/号码模糊）、unitId、groupId、gender、qualificationStatus。
     */
    @GetMapping
    @PreAuthorize("@perm.has('athlete:list')")
    public R<PageResult<Athlete>> page(@Valid AthleteQuery query) {
        return R.ok(athleteService.pageAthlete(query));
    }

    /**
     * 运动员详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('athlete:query')")
    public R<Athlete> detail(@PathVariable("id") Long id) {
        return R.ok(athleteService.getAthleteDetail(id));
    }

    /**
     * 新增运动员。
     */
    @PostMapping
    @PreAuthorize("@perm.has('athlete:add')")
    @OpLog(module = "ATHLETE", action = "ADD", actionName = "新增运动员", targetType = "athlete")
    public R<Long> add(@Valid @RequestBody Athlete athlete) {
        return R.ok(athleteService.createAthlete(athlete));
    }

    /**
     * 修改运动员。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('athlete:edit')")
    @OpLog(module = "ATHLETE", action = "EDIT", actionName = "修改运动员", targetType = "athlete")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody Athlete athlete) {
        athleteService.updateAthlete(id, athlete);
        return R.ok();
    }

    /**
     * 删除运动员（逻辑删除，删除前改写号码唯一键）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('athlete:delete')")
    @OpLog(module = "ATHLETE", action = "DELETE", actionName = "删除运动员", targetType = "athlete")
    public R<Void> remove(@PathVariable("id") Long id) {
        athleteService.removeAthlete(id);
        return R.ok();
    }
}
