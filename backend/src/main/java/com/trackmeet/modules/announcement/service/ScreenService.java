package com.trackmeet.modules.announcement.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trackmeet.modules.announcement.dto.ScreenConfigSaveDTO;
import com.trackmeet.modules.announcement.entity.ScreenConfig;
import com.trackmeet.modules.announcement.vo.ScreenBoardVO;
import com.trackmeet.modules.announcement.vo.ScreenConfigVO;

/**
 * 大屏服务：大屏配置维护、访问令牌生成与大屏聚合取数。
 * <p>
 * 对应需求 FR-BIG 系列与《API 清单》第 11 节「大屏与公告」：
 * 配置读取（{@code screen:config:list}）、配置新增 / 修改（{@code screen:config:add} /
 * {@code screen:config:edit}）、令牌重生成（{@code screen:token}）、
 * 登录态大屏聚合（{@code screen:board}）与匿名大屏聚合（{@code /api/screen/public/**}）。
 *
 * @author TrackMeet
 */
public interface ScreenService extends IService<ScreenConfig> {

    /**
     * 读取届次的大屏配置；不存在时自动创建默认方案（轮播 15 秒、展示模块全开）并落库，
     * 令牌（{@code accessToken}）为空时自动生成并落库。
     *
     * @param meetId 届次 ID（须与届次上下文一致）
     * @return 大屏配置 VO
     */
    ScreenConfigVO getConfig(Long meetId);

    /**
     * 新增 / 保存大屏配置：一个届次维护一个活动方案，已存在时按该方案更新。
     *
     * @param dto 大屏配置入参
     * @return 大屏配置 ID
     */
    Long saveConfig(ScreenConfigSaveDTO dto);

    /**
     * 按 ID 修改大屏配置（不允许跨届次）。
     *
     * @param id  大屏配置 ID
     * @param dto 大屏配置入参
     * @return 修改后的大屏配置 VO
     */
    ScreenConfigVO updateConfig(Long id, ScreenConfigSaveDTO dto);

    /**
     * 重新生成大屏访问令牌（UUID 去横线，32 位）并落库。
     * <p>
     * 返回值直接是新令牌明文（前端 {@code refreshScreenToken()} 取 {@code data} 作为令牌展示），
     * 配置行详情可通过 {@link #getConfig(Long)} 重新读取。
     *
     * @param meetId 届次 ID（须与届次上下文一致；为空时取 X-Meet-Id）
     * @return 新的 32 位访问令牌
     */
    String regenerateToken(Long meetId);

    /**
     * 登录态大屏聚合：届次信息 + 已发布且推送大屏的公告 + 奖牌榜 + 团体总分 + 最新成绩 + 配置。
     *
     * @param meetId 届次 ID（须与届次上下文一致）
     * @return 大屏聚合数据
     */
    ScreenBoardVO board(Long meetId);

    /**
     * 匿名大屏聚合：按参数 {@code meetId} 取数，并校验 {@code token} 与
     * {@code screen_config} 中的访问令牌一致，不一致抛 {@code BizException(FORBIDDEN)}。
     *
     * @param meetId 届次 ID
     * @param token  大屏访问令牌
     * @return 大屏聚合数据（与 {@link #board} 结构相同）
     */
    ScreenBoardVO publicBoard(Long meetId, String token);
}
