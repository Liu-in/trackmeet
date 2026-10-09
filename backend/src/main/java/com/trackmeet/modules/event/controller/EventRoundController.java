package com.trackmeet.modules.event.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.event.dto.EventRoundSaveDTO;
import com.trackmeet.modules.event.service.EventRoundService;
import com.trackmeet.modules.event.vo.EventRoundVO;
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
 * 轮次管理接口。
 * <p>
 * 权限编码对照：详情 {@code event:round:list}、修改 {@code event:round:edit}、删除 {@code event:round:delete}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
@Validated
public class EventRoundController {

    private final EventRoundService eventRoundService;

    /**
     * 轮次详情（含轮次级规则，未配置时 rule 为 null）。
     */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('event:round:list')")
    public R<EventRoundVO> detail(@PathVariable Long id) {
        return R.ok(eventRoundService.getRoundDetail(id));
    }

    /**
     * 修改轮次（round_code / round_name / plan_heat_count / plan_advance_count / status）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('event:round:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "修改轮次", targetType = "event_round")
    public R<Void> edit(@PathVariable Long id, @Valid @RequestBody EventRoundSaveDTO dto) {
        eventRoundService.updateRound(id, dto);
        return R.ok();
    }

    /**
     * 删除轮次（存在下级轮次引用时返回 CONFLICT）。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('event:round:delete')")
    @OpLog(module = "EVENT", action = "DELETE", actionName = "删除轮次", targetType = "event_round")
    public R<Void> remove(@PathVariable Long id) {
        eventRoundService.deleteRound(id);
        return R.ok();
    }
}
