package com.trackmeet.modules.schedule.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.schedule.dto.LaneAssignDTO;
import com.trackmeet.modules.schedule.dto.LaneSwapDTO;
import com.trackmeet.modules.schedule.service.LanesService;
import com.trackmeet.modules.schedule.vo.LaneVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 道次接口：批量分配、手工改道次、两道次交换。
 * <p>
 * 接口与权限编码对照：分配 {@code schedule:lane:assign}、调整（改道次/交换）{@code schedule:lane:edit}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/lanes")
@RequiredArgsConstructor
@Validated
public class LanesController {

    private final LanesService lanesService;

    /**
     * 道次分配（body：{@code {heatIds:[], mode, laneCount}}）。
     */
    @PostMapping("/assign")
    @PreAuthorize("@perm.has('schedule:lane:assign')")
    @OpLog(module = "SCHEDULE", action = "ASSIGN", actionName = "道次分配", targetType = "lane")
    public R<List<LaneVO>> assign(@RequestBody LaneAssignDTO dto) {
        return R.ok(lanesService.assign(dto));
    }

    /**
     * 手工修改道次号（body：{@code {"laneNo":4}}；目标道次已被占用返回 CONFLICT）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('schedule:lane:edit')")
    @OpLog(module = "SCHEDULE", action = "EDIT", actionName = "调整道次", targetType = "lane")
    public R<Void> edit(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        lanesService.updateLaneNo(id, body == null ? null : body.get("laneNo"));
        return R.ok();
    }

    /**
     * 交换同一分组内的两条道次（body：{@code {laneIdA, laneIdB}}）。
     */
    @PostMapping("/swap")
    @PreAuthorize("@perm.has('schedule:lane:edit')")
    @OpLog(module = "SCHEDULE", action = "SWAP", actionName = "交换道次", targetType = "lane")
    public R<Void> swap(@Valid @RequestBody LaneSwapDTO dto) {
        lanesService.swap(dto);
        return R.ok();
    }
}
