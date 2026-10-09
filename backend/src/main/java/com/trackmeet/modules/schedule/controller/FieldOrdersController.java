package com.trackmeet.modules.schedule.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.schedule.dto.FieldOrderGenerateDTO;
import com.trackmeet.modules.schedule.dto.FieldOrderReorderDTO;
import com.trackmeet.modules.schedule.service.FieldOrdersService;
import com.trackmeet.modules.schedule.vo.FieldOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 田赛出场顺序接口：生成与顺序调整。
 * <p>
 * 接口与权限编码对照：生成 {@code schedule:field:generate}、调整 {@code schedule:field:edit}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/field-orders")
@RequiredArgsConstructor
@Validated
public class FieldOrdersController {

    private final FieldOrdersService fieldOrdersService;

    /**
     * 生成出场顺序（body：{@code {scheduleId, mode(RANDOM|SEED|UNIT|BIB), apply}}）。
     */
    @PostMapping("/generate")
    @PreAuthorize("@perm.has('schedule:field:generate')")
    @OpLog(module = "SCHEDULE", action = "GENERATE", actionName = "生成田赛出场顺序", targetType = "heat")
    public R<List<FieldOrderVO>> generate(@Valid @RequestBody FieldOrderGenerateDTO dto) {
        return R.ok(fieldOrdersService.generate(dto));
    }

    /**
     * 出场顺序调整（body：{@code {heatId, memberIds}}，按数组顺序重写 {@code order_no}）。
     */
    @PutMapping("/reorder")
    @PreAuthorize("@perm.has('schedule:field:edit')")
    @OpLog(module = "SCHEDULE", action = "REORDER", actionName = "调整田赛出场顺序", targetType = "heat")
    public R<Void> reorder(@Valid @RequestBody FieldOrderReorderDTO dto) {
        fieldOrdersService.reorder(dto);
        return R.ok();
    }
}
