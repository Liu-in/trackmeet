package com.trackmeet.modules.announcement.controller;

import com.trackmeet.common.annotation.OpLog;
import com.trackmeet.common.result.R;
import com.trackmeet.modules.announcement.dto.ScreenConfigSaveDTO;
import com.trackmeet.modules.announcement.dto.ScreenQuery;
import com.trackmeet.modules.announcement.service.ScreenService;
import com.trackmeet.modules.announcement.vo.ScreenBoardVO;
import com.trackmeet.modules.announcement.vo.ScreenConfigVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏接口：大屏配置、访问令牌与大屏聚合数据。
 * <p>
 * 权限编码（均已在 {@code sql/05-init-data.sql} 与 {@code sql/09-patch-stage5.sql} 初始化）：
 * <ul>
 *     <li>{@code GET /api/screen/config} → {@code screen:config:list}；</li>
 *     <li>{@code POST /api/screen/config} → {@code screen:config:add}；</li>
 *     <li>{@code PUT /api/screen/config/{id}} → {@code screen:config:edit}；</li>
 *     <li>{@code PUT /api/screen/config/token} → {@code screen:token}；</li>
 *     <li>{@code GET /api/screen/board} → {@code screen:board}；</li>
 *     <li>{@code GET /api/screen/public/board} → <b>匿名</b>（由 {@code SecurityConfig} 放行
 *         {@code /api/screen/**}），服务端自行校验 {@code token}，校验失败抛
 *         {@code BizException(FORBIDDEN)}。</li>
 * </ul>
 * 大屏一次请求最多 5 条查询：届次 + 配置（1）、公告（1）、奖牌榜（1）、团体总分（1）、最新成绩（1）。
 *
 * @author TrackMeet
 */
@Slf4j
@RestController
@RequestMapping("/api/screen")
@RequiredArgsConstructor
@Validated
public class ScreenController {

    private final ScreenService screenService;

    /**
     * 读取大屏配置：无配置时返回默认值（轮播 15 秒、显示模块全开）并落库默认方案；
     * 访问令牌为空时自动生成并落库。
     *
     * @param meetId 届次 ID（必填）
     * @return 大屏配置 VO（含 accessToken）
     */
    @GetMapping("/config")
    @PreAuthorize("@perm.has('screen:config:list')")
    public R<ScreenConfigVO> config(@RequestParam("meetId") @NotNull Long meetId) {
        return R.ok(screenService.getConfig(meetId));
    }

    /**
     * 新增 / 保存大屏配置（一个届次维护一个活动方案，已存在时按该方案更新）。
     *
     * @param dto 大屏配置入参（meetId 必填）
     * @return 大屏配置 ID
     */
    @PostMapping("/config")
    @PreAuthorize("@perm.has('screen:config:add')")
    @OpLog(module = "ANNOUNCEMENT", action = "ADD", actionName = "新增大屏配置", targetType = "screen_config")
    public R<Long> saveConfig(@Valid @RequestBody ScreenConfigSaveDTO dto) {
        return R.ok(screenService.saveConfig(dto));
    }

    /**
     * 重新生成大屏访问令牌（UUID 去横线，32 位）并返回令牌明文。
     * <p>
     * {@code meetId} 支持查询参数或请求体两种传法（都不传时取 X-Meet-Id 届次上下文），
     * 便于前端与脚本调用；返回值即前端 {@code refreshScreenToken()} 需要的令牌字符串。
     *
     * @param meetId 届次 ID（查询参数，可空）
     * @param body   请求体（可空，仅取 meetId）
     * @return 新的 32 位大屏访问令牌（{@code data}）
     */
    @PutMapping("/config/token")
    @PreAuthorize("@perm.has('screen:token')")
    @OpLog(module = "ANNOUNCEMENT", action = "TOKEN", actionName = "生成大屏令牌", targetType = "screen_config")
    public R<String> regenerateToken(@RequestParam(value = "meetId", required = false) Long meetId,
                                     @RequestBody(required = false) ScreenConfigSaveDTO body) {
        Long target = meetId != null ? meetId : (body == null ? null : body.getMeetId());
        return R.ok(screenService.regenerateToken(target));
    }

    /**
     * 修改大屏配置（不允许跨届次）。
     *
     * @param id  大屏配置 ID
     * @param dto 大屏配置入参
     * @return 修改后的大屏配置 VO
     */
    @PutMapping("/config/{id}")
    @PreAuthorize("@perm.has('screen:config:edit')")
    @OpLog(module = "ANNOUNCEMENT", action = "EDIT", actionName = "修改大屏配置", targetType = "screen_config")
    public R<ScreenConfigVO> updateConfig(@PathVariable("id") Long id,
                                          @Valid @RequestBody ScreenConfigSaveDTO dto) {
        return R.ok(screenService.updateConfig(id, dto));
    }

    /**
     * 登录态大屏聚合数据。
     *
     * @param query 查询条件（meetId 必填，须与 X-Meet-Id 一致）
     * @return 大屏聚合数据（届次信息 / 公告 / 奖牌榜 / 团体总分 / 最新成绩 / 配置 / 刷新时间）
     */
    @GetMapping("/board")
    @PreAuthorize("@perm.has('screen:board')")
    public R<ScreenBoardVO> board(@Valid ScreenQuery query) {
        return R.ok(screenService.board(query.getMeetId()));
    }

    /**
     * 匿名大屏聚合数据：按 {@code meetId} 取数并校验 {@code token}。
     * <p>
     * 令牌与 {@code screen_config} 中的访问令牌不一致（或该届次尚未生成令牌）时抛
     * {@code BizException(FORBIDDEN)}。
     *
     * @param query 查询条件（meetId、token 均必填）
     * @return 大屏聚合数据（与登录态 board 结构相同）
     */
    @GetMapping("/public/board")
    public R<ScreenBoardVO> publicBoard(@Valid ScreenQuery query) {
        return R.ok(screenService.publicBoard(query.getMeetId(), query.getToken()));
    }
}
