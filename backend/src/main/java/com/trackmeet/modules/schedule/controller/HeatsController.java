package com.trackmeet.modules.schedule.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.schedule.dto.HeatLockDTO;
import com.trackmeet.modules.schedule.dto.HeatMemberMoveDTO;
import com.trackmeet.modules.schedule.dto.HeatMemberSaveDTO;
import com.trackmeet.modules.schedule.dto.HeatsGenerateDTO;
import com.trackmeet.modules.schedule.service.HeatsService;
import com.trackmeet.modules.schedule.vo.HeatGenerateResultVO;
import com.trackmeet.modules.schedule.vo.HeatMemberVO;
import com.trackmeet.modules.schedule.vo.HeatVO;
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
 * 分组分道接口：自动分组、分组与成员查询、成员增删与换组、分组锁定。
 * <p>
 * 接口与权限编码对照：生成 {@code schedule:heat:generate}、查询 {@code schedule:heat:list}、
 * 调整（成员增删/换组）{@code schedule:heat:edit}、锁定 {@code schedule:heat:lock}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/heats")
@RequiredArgsConstructor
@Validated
public class HeatsController {

    private final HeatsService heatsService;

    /**
     * 生成分组分道（{@code apply=false} 仅预览；锁定分组整组保留）。
     */
    @PostMapping("/generate")
    @PreAuthorize("@perm.has('schedule:heat:generate')")
    @OpLog(module = "SCHEDULE", action = "GENERATE", actionName = "生成分组分道", targetType = "heat")
    public R<HeatGenerateResultVO> generate(@Valid @RequestBody HeatsGenerateDTO dto) {
        return R.ok(heatsService.generate(dto));
    }

    /**
     * 日程下的分组列表（含组内人员、道次与田赛出场顺序）。
     */
    @GetMapping("")
    @PreAuthorize("@perm.has('schedule:heat:list')")
    public R<List<HeatVO>> list(@RequestParam Long scheduleId) {
        return R.ok(heatsService.listHeats(scheduleId));
    }

    /**
     * 分组内的成员列表（按 {@code order_no} 升序）。
     */
    @GetMapping("/{id}/members")
    @PreAuthorize("@perm.has('schedule:heat:list')")
    public R<List<HeatMemberVO>> members(@PathVariable Long id) {
        return R.ok(heatsService.listMembers(id));
    }

    /**
     * 添加成员到分组（以报名记录为锚点，body：{@code {registrationId, orderNo}}）。
     */
    @PostMapping("/{id}/members")
    @PreAuthorize("@perm.has('schedule:heat:edit')")
    @OpLog(module = "SCHEDULE", action = "ADD_MEMBER", actionName = "添加组内人员", targetType = "heat")
    public R<Long> addMember(@PathVariable Long id, @Valid @RequestBody HeatMemberSaveDTO dto) {
        return R.ok(heatsService.addMember(id, dto));
    }

    /**
     * 从分组移除成员（同时清理其道次与田赛出场顺序）。
     */
    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("@perm.has('schedule:heat:edit')")
    @OpLog(module = "SCHEDULE", action = "REMOVE_MEMBER", actionName = "移除组内人员", targetType = "heat")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        heatsService.removeMember(id, memberId);
        return R.ok();
    }

    /**
     * 组间拖拽移动成员（body：{@code {memberId, targetHeatId, targetOrderNo}}）。
     */
    @PostMapping("/members/move")
    @PreAuthorize("@perm.has('schedule:heat:edit')")
    @OpLog(module = "SCHEDULE", action = "MOVE_MEMBER", actionName = "移动组内人员", targetType = "heat")
    public R<Void> moveMember(@Valid @RequestBody HeatMemberMoveDTO dto) {
        heatsService.moveMember(dto);
        return R.ok();
    }

    /**
     * 锁定/解锁分组（body：{@code {"isLocked":true|false}}）。
     */
    @PutMapping("/{id}/lock")
    @PreAuthorize("@perm.has('schedule:heat:lock')")
    @OpLog(module = "SCHEDULE", action = "LOCK", actionName = "锁定分组", targetType = "heat")
    public R<Void> lock(@PathVariable Long id, @Valid @RequestBody HeatLockDTO dto) {
        heatsService.lockHeat(id, dto.getIsLocked());
        return R.ok();
    }
}
