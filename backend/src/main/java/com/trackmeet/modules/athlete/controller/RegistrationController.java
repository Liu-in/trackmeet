package com.trackmeet.modules.athlete.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.PageResult;
import com.trackmeet.common.result.R;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.athlete.dto.RegistrationApproveDTO;
import com.trackmeet.modules.athlete.dto.RegistrationBatchDTO;
import com.trackmeet.modules.athlete.dto.RegistrationCheckDTO;
import com.trackmeet.modules.athlete.dto.RegistrationQuery;
import com.trackmeet.modules.athlete.dto.RegistrationRejectDTO;
import com.trackmeet.modules.athlete.dto.RegistrationSaveDTO;
import com.trackmeet.modules.athlete.service.RegistrationService;
import com.trackmeet.modules.athlete.vo.RegistrationCheckResultVO;
import com.trackmeet.modules.athlete.vo.RegistrationStatVO;
import com.trackmeet.modules.athlete.vo.RegistrationVO;
import com.trackmeet.modules.athlete.vo.RegistrationWarningVO;
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
import java.util.Map;

/**
 * 报名接口：分页/详情、新增/批量/修改/取消、限制校验、审核、统计预警与报名截止锁定。
 * <p>
 * 权限编码（与初始化数据一致）：
 * {@code registration:list}、{@code registration:add}、{@code registration:edit}、
 * {@code registration:delete}、{@code registration:check}、{@code registration:check:exec}、
 * {@code registration:approve}、{@code registration:lock}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
@Validated
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * 报名分页列表。
     * 条件：eventId、unitId、groupId、athleteId、status、isRelay、keyword（姓名/单位/项目/接力队名模糊）。
     */
    @GetMapping
    @PreAuthorize("@perm.has('registration:list')")
    public R<PageResult<RegistrationVO>> page(@Valid RegistrationQuery query) {
        return R.ok(registrationService.pageRegistration(query));
    }

    /**
     * 报名详情（含接力队员明细与校验快照）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('registration:list')")
    public R<RegistrationVO> detail(@PathVariable("id") Long id) {
        return R.ok(registrationService.getRegistrationDetail(id));
    }

    /**
     * 新增单条报名（个人或接力）。
     */
    @PostMapping
    @PreAuthorize("@perm.has('registration:add')")
    @OpLog(module = "REGISTRATION", action = "ADD", actionName = "新增报名", targetType = "registration")
    public R<Long> add(@Valid @RequestBody RegistrationSaveDTO dto) {
        return R.ok(registrationService.createRegistration(dto));
    }

    /**
     * 批量报名（一个单位一次提交一个项目的多名运动员；接力时 athleteIds 为一支队的队员）。
     */
    @PostMapping("/batch")
    @PreAuthorize("@perm.has('registration:add')")
    @OpLog(module = "REGISTRATION", action = "BATCH_ADD", actionName = "批量报名", targetType = "registration")
    public R<List<Long>> batch(@Valid @RequestBody RegistrationBatchDTO dto) {
        return R.ok(registrationService.batchRegistration(dto));
    }

    /**
     * 修改报名（更新种子成绩、接力队员）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('registration:edit')")
    @OpLog(module = "REGISTRATION", action = "EDIT", actionName = "修改报名", targetType = "registration")
    public R<Void> edit(@PathVariable("id") Long id, @Valid @RequestBody RegistrationSaveDTO dto) {
        registrationService.updateRegistration(id, dto);
        return R.ok();
    }

    /**
     * 取消报名（逻辑删除，状态置 CANCELLED）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('registration:delete')")
    @OpLog(module = "REGISTRATION", action = "CANCEL", actionName = "取消报名", targetType = "registration")
    public R<Void> cancel(@PathVariable("id") Long id) {
        registrationService.cancelRegistration(id);
        return R.ok();
    }

    /**
     * 批量限制校验（不落库，返回明细问题清单）。
     */
    @PostMapping("/check")
    @PreAuthorize("@perm.has('registration:check:exec')")
    @OpLog(module = "REGISTRATION", action = "CHECK", actionName = "报名限制校验", targetType = "registration")
    public R<RegistrationCheckResultVO> check(@Valid @RequestBody RegistrationCheckDTO dto) {
        return R.ok(registrationService.check(dto));
    }

    /**
     * 已落库报名的校验快照与问题清单。
     */
    @GetMapping("/check-result")
    @PreAuthorize("@perm.has('registration:check')")
    public R<RegistrationCheckResultVO> checkResult(@Valid RegistrationQuery query) {
        return R.ok(registrationService.getCheckResult(query));
    }

    /**
     * 批量审核通过。
     */
    @PostMapping("/approve")
    @PreAuthorize("@perm.has('registration:approve')")
    @OpLog(module = "REGISTRATION", action = "APPROVE", actionName = "批量审核报名", targetType = "registration")
    public R<Integer> approve(@Valid @RequestBody RegistrationApproveDTO dto) {
        return R.ok(registrationService.approve(dto));
    }

    /**
     * 退回报名（附退回原因）。
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("@perm.has('registration:approve')")
    @OpLog(module = "REGISTRATION", action = "REJECT", actionName = "退回报名", targetType = "registration")
    public R<Void> reject(@PathVariable("id") Long id, @Valid @RequestBody RegistrationRejectDTO dto) {
        registrationService.reject(id, dto);
        return R.ok();
    }

    /**
     * 报名统计：dimension = UNIT | EVENT | GROUP。
     */
    @GetMapping("/statistics")
    @PreAuthorize("@perm.has('registration:list')")
    public R<List<RegistrationStatVO>> statistics(
            @RequestParam(value = "dimension", required = false, defaultValue = "UNIT") String dimension) {
        return R.ok(registrationService.statistics(dimension));
    }

    /**
     * 报名不足预警（已通过报名数低于预警线的项目）。
     */
    @GetMapping("/warnings")
    @PreAuthorize("@perm.has('registration:list')")
    public R<List<RegistrationWarningVO>> warnings() {
        return R.ok(registrationService.warnings());
    }

    /**
     * 截止并锁定报名（届次状态 REG_OPEN → REG_CLOSED）。
     * 届次来源优先级：query 参数 meetId > 请求体 meetId > 请求头 X-Meet-Id 上下文。
     */
    @PostMapping("/lock")
    @PreAuthorize("@perm.has('registration:lock')")
    @OpLog(module = "REGISTRATION", action = "LOCK", actionName = "截止并锁定报名", targetType = "registration")
    public R<Void> lock(@RequestBody(required = false) Map<String, Object> body,
                        @RequestParam(value = "meetId", required = false) Long meetId) {
        Long targetMeetId = meetId;
        if (targetMeetId == null && body != null && body.get("meetId") != null) {
            Object value = body.get("meetId");
            try {
                targetMeetId = Long.valueOf(String.valueOf(value).trim());
            } catch (NumberFormatException ex) {
                throw new BizException(ResultCode.PARAM_ERROR, "届次 ID 格式不正确：" + value);
            }
        }
        registrationService.lockRegistration(targetMeetId);
        return R.ok();
    }
}
