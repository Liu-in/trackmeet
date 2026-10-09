package com.trackmeet.modules.event.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.event.dto.MeetRuleConfigSaveDTO;
import com.trackmeet.modules.event.service.MeetRuleConfigService;
import com.trackmeet.modules.event.vo.MeetRuleConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 届次级默认规则接口（三层继承最外层）。
 * <p>
 * 权限编码对照：读取 {@code event:rule:list}、保存 {@code event:rule:edit}。
 *
 * @author TrackMeet
 */
@RestController
@RequestMapping("/api/meets")
@RequiredArgsConstructor
@Validated
public class MeetRuleConfigController {

    private final MeetRuleConfigService meetRuleConfigService;

    /**
     * 届次级默认规则（未配置字段返回系统兜底默认，保证前端始终拿到生效值）。
     */
    @GetMapping("/{id}/rule-config")
    @PreAuthorize("@perm.has('event:rule:list')")
    public R<MeetRuleConfigVO> ruleConfig(@PathVariable Long id) {
        return R.ok(meetRuleConfigService.getEffective(id));
    }

    /**
     * 保存届次级规则（按 meet_id 不存在则插入、存在则更新，唯一键 uk_meet_id）。
     */
    @PutMapping("/{id}/rule-config")
    @PreAuthorize("@perm.has('event:rule:edit')")
    @OpLog(module = "EVENT", action = "EDIT", actionName = "保存届次级规则", targetType = "meet")
    public R<Long> saveRuleConfig(@PathVariable Long id, @Valid @RequestBody MeetRuleConfigSaveDTO dto) {
        return R.ok(meetRuleConfigService.saveByMeetId(id, dto));
    }
}
