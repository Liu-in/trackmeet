package com.trackmeet.modules.announcement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trackmeet.common.context.MeetContext;
import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.trackmeet.modules.announcement.dto.ScreenBoardBaseRow;
import com.trackmeet.modules.announcement.dto.ScreenConfigSaveDTO;
import com.trackmeet.modules.announcement.dto.ScreenMedalRow;
import com.trackmeet.modules.announcement.dto.ScreenResultRow;
import com.trackmeet.modules.announcement.dto.ScreenTeamScoreRow;
import com.trackmeet.modules.announcement.entity.ScreenConfig;
import com.trackmeet.modules.announcement.mapper.ScreenConfigMapper;
import com.trackmeet.modules.announcement.service.AnnouncementService;
import com.trackmeet.modules.announcement.service.ScreenService;
import com.trackmeet.modules.announcement.vo.ScreenBoardVO;
import com.trackmeet.modules.announcement.vo.ScreenConfigVO;
import com.trackmeet.modules.announcement.vo.ScreenMedalRowVO;
import com.trackmeet.modules.announcement.vo.ScreenMeetInfoVO;
import com.trackmeet.modules.announcement.vo.ScreenResultRowVO;
import com.trackmeet.modules.announcement.vo.ScreenTeamScoreRowVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 大屏服务实现：配置维护 + 访问令牌 + 一次聚合取数。
 * <p>
 * <b>一次大屏请求恰好 5 条查询</b>（{@link #assemble}）：
 * <ol>
 *     <li>{@code ScreenConfigMapper#selectBoardBase}：{@code meet} 届次信息 + 活动大屏配置（1 条）；</li>
 *     <li>{@code AnnouncementMapper#selectScreenAnnouncements}：已发布且推送大屏的公告（1 条）；</li>
 *     <li>{@code ScreenConfigMapper#selectTopMedals}：奖牌榜 {@code sort_score} 降序前 20（1 条）；</li>
 *     <li>{@code ScreenConfigMapper#selectTopTeamScores}：团体总分 {@code TOTAL} 降序前 20（1 条）；</li>
 *     <li>{@code ScreenConfigMapper#selectLatestResults}：已审核有效成绩按 {@code update_time} 降序前 20（1 条）。</li>
 * </ol>
 * <b>令牌存储</b>：交付要求令牌落在 {@code screen_config.access_token}，但建表脚本（sql/04 第 494~517 行）
 * 与字段清单（docs/12-05 第 321~338 行）均无该列，且本模块不得改动 SQL；为守住
 * 「实体列与 DDL 逐字一致」，令牌持久化在 {@code theme_json} 的保留键 {@code accessToken} 上，
 * 对外接口字段名仍为 {@code accessToken}。待 SQL 增列后只需替换
 * {@link #extractToken(String)} / {@link #writeToken(String, String)} 两处实现。
 *
 * @author TrackMeet
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenServiceImpl extends ServiceImpl<ScreenConfigMapper, ScreenConfig> implements ScreenService {

    /** 大屏奖牌榜取数条数 */
    private static final int SCREEN_MEDAL_LIMIT = 20;

    /** 大屏团体总分取数条数 */
    private static final int SCREEN_TEAM_SCORE_LIMIT = 20;

    /** 大屏最新成绩取数条数 */
    private static final int SCREEN_RESULT_LIMIT = 20;

    /** 默认模块轮播间隔（秒） */
    private static final int DEFAULT_ROTATE_SECONDS = 15;

    /** 默认自动刷新间隔（秒，与 sys_config 的 screen.refresh.seconds 一致） */
    private static final int DEFAULT_REFRESH_INTERVAL = 15;

    /** 默认分辨率 */
    private static final String DEFAULT_RESOLUTION = "1920x1080";

    /** 默认大屏方案名称 */
    private static final String DEFAULT_SCREEN_NAME = "默认大屏方案";

    /** 令牌在 theme_json 中的保留键 */
    private static final String TOKEN_KEY = "accessToken";

    /** 主题模式在 theme_json 中的键（DARK / LIGHT） */
    private static final String THEME_MODE_KEY = "mode";

    /** 默认主题模式 */
    private static final String DEFAULT_THEME_MODE = "DARK";

    /** modules_json 的模块开关容器键 */
    private static final String MODULES_ENABLED_KEY = "enabled";

    /** modules_json 的模块顺序键 */
    private static final String MODULES_ORDER_KEY = "order";

    /** 模块编码：公告 */
    private static final String MODULE_ANNOUNCEMENT = "ANNOUNCEMENT";

    /** 模块编码：奖牌榜 */
    private static final String MODULE_MEDAL = "MEDAL";

    /** 模块编码：团体总分 */
    private static final String MODULE_TEAM_SCORE = "TEAM_SCORE";

    /** 模块编码：最新成绩 */
    private static final String MODULE_RESULT = "RESULT";

    /** 默认展示模块顺序与开关（全开） */
    private static final String DEFAULT_MODULES_JSON =
            "{\"order\":[\"MEDAL\",\"TEAM_SCORE\",\"RESULT\",\"ANNOUNCEMENT\"],"
                    + "\"enabled\":{\"MEDAL\":true,\"TEAM_SCORE\":true,\"RESULT\":true,\"ANNOUNCEMENT\":true}}";

    /** 默认主题 */
    private static final String DEFAULT_THEME_JSON =
            "{\"mode\":\"DARK\",\"primaryColor\":\"#0B4C8C\",\"secondaryColor\":\"#F5A623\","
                    + "\"fontFamily\":\"Microsoft YaHei\"}";

    /** 合法播放模式 */
    private static final Set<String> PLAY_MODES =
            Set.of(ScreenConfig.PLAY_MODE_CYCLE, ScreenConfig.PLAY_MODE_FIXED);

    /** 合法大屏状态 */
    private static final Set<String> CONFIG_STATUSES =
            Set.of(ScreenConfig.STATUS_ENABLED, ScreenConfig.STATUS_DISABLED);

    private final AnnouncementService announcementService;

    private final ObjectMapper objectMapper;

    // ==================================================================================
    // 一、配置读取与维护
    // ==================================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenConfigVO getConfig(Long meetId) {
        Long target = requireMeetId(meetId);
        ScreenConfig config = baseMapper.selectActiveConfig(target);
        if (config == null) {
            // 无配置：返回默认值（轮播 15 秒、显示模块全开）并落库，保证匿名大屏可直接取得令牌
            return toConfigVO(createDefaultConfig(target, null));
        }
        if (!StringUtils.hasText(extractToken(config.getThemeJson()))) {
            config.setThemeJson(writeToken(config.getThemeJson(), newToken()));
            this.updateById(config);
            log.info("大屏访问令牌为空，已自动生成：meetId={}, configId={}", target, config.getId());
        }
        return toConfigVO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveConfig(ScreenConfigSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "大屏配置不能为空");
        }
        Long meetId = requireMeetId(dto.getMeetId());
        validateJson(dto.getModulesJson(), "展示模块配置");
        validateJson(dto.getThemeJson(), "主题配置");
        ScreenConfig config = baseMapper.selectActiveConfig(meetId);
        boolean created = config == null;
        checkCodeUnique(meetId, dto.getScreenCode(), created ? null : config.getId());
        if (created) {
            config = new ScreenConfig();
            config.setMeetId(meetId);
            config.setScreenCode(buildDefaultCode(meetId));
            config.setIsDefault(1);
            config.setStatus(ScreenConfig.STATUS_ENABLED);
        }
        // 令牌：先取既有令牌，再套用请求内容，请求未携带时沿用既有令牌，无既有令牌则生成
        String existingToken = created ? null : extractToken(config.getThemeJson());
        applySaveDTO(config, dto);
        String token = extractToken(config.getThemeJson());
        if (!StringUtils.hasText(token)) {
            token = StringUtils.hasText(existingToken) ? existingToken : newToken();
        }
        config.setThemeJson(writeToken(config.getThemeJson(), token));
        if (created) {
            this.save(config);
        } else {
            this.updateById(config);
        }
        log.info("保存大屏配置：meetId={}, configId={}, created={}", meetId, config.getId(), created);
        return config.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenConfigVO updateConfig(Long id, ScreenConfigSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "大屏配置不能为空");
        }
        ScreenConfig config = requireConfig(id);
        if (dto.getMeetId() != null && !Objects.equals(dto.getMeetId(), config.getMeetId())) {
            throw new BizException(ResultCode.FORBIDDEN, "不允许跨届次修改大屏配置");
        }
        checkMeetContext(config.getMeetId());
        validateJson(dto.getModulesJson(), "展示模块配置");
        validateJson(dto.getThemeJson(), "主题配置");
        checkCodeUnique(config.getMeetId(), dto.getScreenCode(), id);
        String existingToken = extractToken(config.getThemeJson());
        applySaveDTO(config, dto);
        String token = extractToken(config.getThemeJson());
        if (!StringUtils.hasText(token)) {
            token = StringUtils.hasText(existingToken) ? existingToken : newToken();
        }
        config.setThemeJson(writeToken(config.getThemeJson(), token));
        this.updateById(config);
        log.info("修改大屏配置：id={}, meetId={}", id, config.getMeetId());
        return toConfigVO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String regenerateToken(Long meetId) {
        Long target = requireMeetId(meetId);
        String token = newToken();
        ScreenConfig config = baseMapper.selectActiveConfig(target);
        if (config == null) {
            config = createDefaultConfig(target, token);
        } else {
            config.setThemeJson(writeToken(config.getThemeJson(), token));
            this.updateById(config);
        }
        log.info("重新生成大屏访问令牌：meetId={}, configId={}", target, config.getId());
        return token;
    }

    // ==================================================================================
    // 二、大屏聚合（登录态 / 匿名共用，一次请求 5 条查询）
    // ==================================================================================

    @Override
    public ScreenBoardVO board(Long meetId) {
        Long target = requireMeetId(meetId);
        ScreenBoardBaseRow base = baseMapper.selectBoardBase(target);
        if (base == null || base.getMeetId() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "届次不存在");
        }
        ScreenBoardVO vo = assemble(target, base);
        vo.setMessage("大屏数据加载完成：公告 " + vo.getAnnouncements().size()
                + " 条，奖牌榜 " + vo.getMedals().size()
                + " 行，团体总分 " + vo.getTeamScores().size()
                + " 行，最新成绩 " + vo.getLatestResults().size() + " 条");
        return vo;
    }

    @Override
    public ScreenBoardVO publicBoard(Long meetId, String token) {
        if (meetId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "届次不能为空");
        }
        // 匿名接口不依赖登录态与届次上下文：按参数 meetId 取数，并自行校验令牌
        ScreenBoardBaseRow base = baseMapper.selectBoardBase(meetId);
        if (base == null || base.getMeetId() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "届次不存在");
        }
        String expected = extractToken(base.getThemeJson());
        if (!StringUtils.hasText(expected)) {
            throw new BizException(ResultCode.FORBIDDEN, "该届次尚未生成大屏访问令牌");
        }
        if (!expected.equals(token == null ? null : token.trim())) {
            throw new BizException(ResultCode.FORBIDDEN, "大屏访问令牌无效");
        }
        ScreenBoardVO vo = assemble(meetId, base);
        vo.setMessage("公开大屏数据加载完成（令牌校验通过）");
        return vo;
    }

    /**
     * 装配大屏数据（参数 {@code base} 已包含届次信息与配置，避免重复查询）。
     */
    private ScreenBoardVO assemble(Long meetId, ScreenBoardBaseRow base) {
        ScreenBoardVO vo = new ScreenBoardVO();
        vo.setMeetId(meetId);
        vo.setMeetInfo(toMeetInfo(base));
        vo.setConfig(toBoardConfig(meetId, base));
        vo.setAnnouncements(announcementService.listScreenAnnouncements(meetId));
        vo.setMedals(toMedalVOList(baseMapper.selectTopMedals(meetId, SCREEN_MEDAL_LIMIT)));
        vo.setTeamScores(toTeamScoreVOList(baseMapper.selectTopTeamScores(meetId, SCREEN_TEAM_SCORE_LIMIT)));
        vo.setLatestResults(toResultVOList(baseMapper.selectLatestResults(meetId, SCREEN_RESULT_LIMIT)));
        vo.setUpdatedAt(LocalDateTime.now());
        return vo;
    }

    // ==================================================================================
    // 三、装配
    // ==================================================================================

    private ScreenMeetInfoVO toMeetInfo(ScreenBoardBaseRow base) {
        ScreenMeetInfoVO vo = new ScreenMeetInfoVO();
        vo.setMeetId(base.getMeetId());
        vo.setMeetName(base.getMeetName());
        vo.setMeetShortName(base.getMeetShortName());
        vo.setEdition(base.getEdition());
        vo.setVenue(base.getVenue());
        vo.setStartDate(base.getStartDate());
        vo.setEndDate(base.getEndDate());
        vo.setDateRange(buildDateRange(base.getStartDate(), base.getEndDate()));
        vo.setStatus(base.getMeetStatus());
        vo.setStatusText(meetStatusText(base.getMeetStatus()));
        return vo;
    }

    private ScreenConfigVO toConfigVO(ScreenConfig config) {
        ScreenConfigVO vo = defaultConfigVO(config.getMeetId());
        vo.setId(config.getId());
        vo.setScreenName(config.getScreenName());
        vo.setScreenCode(config.getScreenCode());
        vo.setModulesJson(config.getModulesJson());
        vo.setRefreshInterval(config.getRefreshInterval());
        vo.setThemeJson(config.getThemeJson());
        vo.setResolution(config.getResolution());
        vo.setRotateSeconds(config.getRotateSeconds());
        vo.setPlayMode(config.getPlayMode());
        vo.setStatus(config.getStatus());
        vo.setIsDefault(config.getIsDefault());
        vo.setAccessToken(extractToken(config.getThemeJson()));
        vo.setRemark(config.getRemark());
        fillConfigDefaults(vo);
        return vo;
    }

    /**
     * 由「届次 + 配置」投影装配配置 VO：无配置行或配置列为空时回落到默认值。
     */
    private ScreenConfigVO toBoardConfig(Long meetId, ScreenBoardBaseRow base) {
        ScreenConfigVO vo = defaultConfigVO(meetId);
        if (base.getConfigId() != null) {
            vo.setId(base.getConfigId());
            vo.setScreenName(base.getScreenName());
            vo.setScreenCode(base.getScreenCode());
            vo.setModulesJson(base.getModulesJson());
            vo.setRefreshInterval(base.getRefreshInterval());
            vo.setThemeJson(base.getThemeJson());
            vo.setResolution(base.getResolution());
            vo.setRotateSeconds(base.getRotateSeconds());
            vo.setPlayMode(base.getPlayMode());
            vo.setStatus(base.getConfigStatus());
            vo.setIsDefault(base.getIsDefault());
            vo.setAccessToken(extractToken(base.getThemeJson()));
            vo.setRemark(base.getConfigRemark());
        }
        fillConfigDefaults(vo);
        return vo;
    }

    /**
     * 默认大屏配置：轮播 15 秒、自动刷新 15 秒、展示模块全开。
     */
    private ScreenConfigVO defaultConfigVO(Long meetId) {
        ScreenConfigVO vo = new ScreenConfigVO();
        vo.setMeetId(meetId);
        vo.setScreenName(DEFAULT_SCREEN_NAME);
        vo.setModulesJson(DEFAULT_MODULES_JSON);
        vo.setRefreshInterval(DEFAULT_REFRESH_INTERVAL);
        vo.setRotateSeconds(DEFAULT_ROTATE_SECONDS);
        vo.setResolution(DEFAULT_RESOLUTION);
        vo.setPlayMode(ScreenConfig.PLAY_MODE_CYCLE);
        vo.setStatus(ScreenConfig.STATUS_ENABLED);
        vo.setIsDefault(1);
        return vo;
    }

    /**
     * 空值兜底：轮播 15 秒、刷新 15 秒、模块全开、播放模式 CYCLE、状态 ENABLED；
     * 并派生前端同名字段（{@code refreshSeconds} / {@code theme} / {@code showXxx}）。
     */
    private void fillConfigDefaults(ScreenConfigVO vo) {
        if (!StringUtils.hasText(vo.getScreenName())) {
            vo.setScreenName(DEFAULT_SCREEN_NAME);
        }
        if (!StringUtils.hasText(vo.getModulesJson())) {
            vo.setModulesJson(DEFAULT_MODULES_JSON);
        }
        if (vo.getRefreshInterval() == null) {
            vo.setRefreshInterval(DEFAULT_REFRESH_INTERVAL);
        }
        if (vo.getRotateSeconds() == null) {
            vo.setRotateSeconds(DEFAULT_ROTATE_SECONDS);
        }
        if (!StringUtils.hasText(vo.getResolution())) {
            vo.setResolution(DEFAULT_RESOLUTION);
        }
        if (!StringUtils.hasText(vo.getPlayMode())) {
            vo.setPlayMode(ScreenConfig.PLAY_MODE_CYCLE);
        }
        if (!StringUtils.hasText(vo.getStatus())) {
            vo.setStatus(ScreenConfig.STATUS_ENABLED);
        }
        if (vo.getIsDefault() == null) {
            vo.setIsDefault(1);
        }
        // 前端同名字段（别名）派生
        vo.setRefreshSeconds(vo.getRefreshInterval());
        vo.setTheme(extractThemeMode(vo.getThemeJson()));
        fillModuleFlags(vo);
    }

    /**
     * 由 {@code modulesJson.enabled} 派生前端同名字段 showAnnouncement / showMedal / showPoints / showResult，
     * 缺失或 JSON 非法时按 1（显示）处理。
     */
    private void fillModuleFlags(ScreenConfigVO vo) {
        ObjectNode modules = readObjectNode(vo.getModulesJson());
        JsonNode enabled = modules == null ? null : modules.path(MODULES_ENABLED_KEY);
        vo.setShowAnnouncement(moduleEnabled(enabled, MODULE_ANNOUNCEMENT));
        vo.setShowMedal(moduleEnabled(enabled, MODULE_MEDAL));
        vo.setShowPoints(moduleEnabled(enabled, MODULE_TEAM_SCORE));
        vo.setShowResult(moduleEnabled(enabled, MODULE_RESULT));
    }

    private Integer moduleEnabled(JsonNode enabled, String moduleCode) {
        if (enabled == null || !enabled.isObject()) {
            return 1;
        }
        JsonNode value = enabled.path(moduleCode);
        if (value.isMissingNode() || value.isNull()) {
            return 1;
        }
        return value.asBoolean(true) ? 1 : 0;
    }

    private List<ScreenMedalRowVO> toMedalVOList(List<ScreenMedalRow> rows) {
        List<ScreenMedalRowVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (ScreenMedalRow row : rows) {
            ScreenMedalRowVO vo = new ScreenMedalRowVO();
            vo.setId(row.getId());
            vo.setUnitId(row.getUnitId());
            vo.setUnitName(row.getUnitName());
            vo.setUnitShortName(row.getUnitShortName());
            vo.setGroupId(row.getGroupId());
            vo.setGroupName(row.getGroupName());
            vo.setGold(row.getGold());
            vo.setSilver(row.getSilver());
            vo.setBronze(row.getBronze());
            vo.setTotalMedal(row.getTotalMedal());
            vo.setSortScore(row.getSortScore());
            vo.setMedalSort(row.getMedalSort());
            vo.setRankNo(row.getRankNo());
            vo.setRankDisplay(row.getRankDisplay());
            vo.setCalcTime(row.getCalcTime());
            vo.setIsPublished(row.getIsPublished());
            vo.setRemark(row.getRemark());
            result.add(vo);
        }
        return result;
    }

    private List<ScreenTeamScoreRowVO> toTeamScoreVOList(List<ScreenTeamScoreRow> rows) {
        List<ScreenTeamScoreRowVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (ScreenTeamScoreRow row : rows) {
            ScreenTeamScoreRowVO vo = new ScreenTeamScoreRowVO();
            vo.setId(row.getId());
            vo.setUnitId(row.getUnitId());
            vo.setUnitName(row.getUnitName());
            vo.setUnitShortName(row.getUnitShortName());
            vo.setGroupId(row.getGroupId());
            vo.setGroupName(row.getGroupName());
            vo.setScoreType(row.getScoreType());
            vo.setRankPointSum(row.getRankPointSum());
            vo.setBonusPointSum(row.getBonusPointSum());
            vo.setRelayPointSum(row.getRelayPointSum());
            vo.setTotalPoint(row.getTotalPoint());
            vo.setRankNo(row.getRankNo());
            vo.setRankDisplay(row.getRankDisplay());
            vo.setEventCount(row.getEventCount());
            vo.setAthleteCount(row.getAthleteCount());
            vo.setCalcTime(row.getCalcTime());
            vo.setIsPublished(row.getIsPublished());
            result.add(vo);
        }
        return result;
    }

    private List<ScreenResultRowVO> toResultVOList(List<ScreenResultRow> rows) {
        List<ScreenResultRowVO> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (ScreenResultRow row : rows) {
            ScreenResultRowVO vo = new ScreenResultRowVO();
            vo.setResultId(row.getResultId());
            vo.setMeetId(row.getMeetId());
            vo.setEventId(row.getEventId());
            vo.setEventName(row.getEventName());
            vo.setEventCode(row.getEventCode());
            vo.setEventType(row.getEventType());
            vo.setGender(row.getGender());
            vo.setRoundId(row.getRoundId());
            vo.setRoundName(row.getRoundName());
            vo.setRoundNo(row.getRoundNo());
            vo.setAthleteId(row.getAthleteId());
            vo.setAthleteName(row.getAthleteName());
            vo.setBibNo(row.getBibNo());
            vo.setUnitId(row.getUnitId());
            vo.setUnitName(row.getUnitName());
            vo.setUnitShortName(row.getUnitShortName());
            vo.setGroupId(row.getGroupId());
            vo.setGroupName(row.getGroupName());
            vo.setResultRaw(row.getResultRaw());
            vo.setResultValue(row.getResultValue());
            vo.setResultUnit(row.getResultUnit());
            vo.setRankNo(row.getRankNo());
            vo.setRankDisplay(row.getRankDisplay());
            vo.setStatus(row.getStatus());
            vo.setWindSpeed(row.getWindSpeed());
            vo.setIsRecordConfirmed(row.getIsRecordConfirmed());
            vo.setUpdateTime(row.getUpdateTime());
            result.add(vo);
        }
        return result;
    }

    // ==================================================================================
    // 四、令牌（theme_json 保留键 accessToken）
    // ==================================================================================

    /**
     * 生成 32 位大屏访问令牌（UUID 去横线）。
     */
    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从 {@code theme_json} 中读取令牌；JSON 非法或键缺失时返回 {@code null}。
     */
    private String extractToken(String themeJson) {
        ObjectNode node = readObjectNode(themeJson);
        if (node == null) {
            return null;
        }
        JsonNode token = node.path(TOKEN_KEY);
        return token.isMissingNode() || token.isNull() ? null : token.asText(null);
    }

    /**
     * 把令牌写入 {@code theme_json}（保留原有主题键）；原 JSON 非法时以空对象重建并告警。
     */
    private String writeToken(String themeJson, String token) {
        ObjectNode node = themeObjectNode(themeJson);
        node.put(TOKEN_KEY, token);
        return node.toString();
    }

    /**
     * 读取主题模式（{@code theme_json.mode}）：DARK 深色 / LIGHT 浅色，缺失时默认 DARK。
     */
    private String extractThemeMode(String themeJson) {
        ObjectNode node = readObjectNode(themeJson);
        if (node == null) {
            return DEFAULT_THEME_MODE;
        }
        JsonNode mode = node.path(THEME_MODE_KEY);
        if (mode.isMissingNode() || mode.isNull() || !StringUtils.hasText(mode.asText(null))) {
            return DEFAULT_THEME_MODE;
        }
        return mode.asText(DEFAULT_THEME_MODE).trim().toUpperCase();
    }

    /**
     * 写入主题模式（{@code theme_json.mode}），保留令牌等其它键。
     */
    private String writeThemeMode(String themeJson, String mode) {
        ObjectNode node = themeObjectNode(themeJson);
        node.put(THEME_MODE_KEY, mode);
        return node.toString();
    }

    /**
     * 取 {@code theme_json} 的对象节点；空值或非法 JSON 时返回新的空对象（并告警）。
     */
    private ObjectNode themeObjectNode(String themeJson) {
        ObjectNode node = readObjectNode(themeJson);
        if (node == null) {
            if (StringUtils.hasText(themeJson)) {
                log.warn("大屏主题 JSON 非法，将以空主题重建");
            }
            node = objectMapper.createObjectNode();
        }
        return node;
    }

    // ==================================================================================
    // 五、写入辅助与校验
    // ==================================================================================

    /**
     * 套用入参到实体：空值不覆盖既有值，缺省值补默认（轮播 15 秒、模块全开、1920x1080）；
     * 同时兼容前端既有字段名（refreshSeconds / theme / showXxx）。
     */
    private void applySaveDTO(ScreenConfig config, ScreenConfigSaveDTO dto) {
        if (StringUtils.hasText(dto.getScreenName())) {
            config.setScreenName(dto.getScreenName().trim());
        }
        if (StringUtils.hasText(dto.getScreenCode())) {
            config.setScreenCode(dto.getScreenCode().trim());
        }
        if (StringUtils.hasText(dto.getModulesJson())) {
            config.setModulesJson(dto.getModulesJson());
        }
        if (dto.getRefreshInterval() != null) {
            config.setRefreshInterval(dto.getRefreshInterval());
        }
        if (StringUtils.hasText(dto.getThemeJson())) {
            config.setThemeJson(dto.getThemeJson());
        }
        if (StringUtils.hasText(dto.getResolution())) {
            config.setResolution(dto.getResolution().trim());
        }
        if (dto.getRotateSeconds() != null) {
            config.setRotateSeconds(dto.getRotateSeconds());
        }
        if (StringUtils.hasText(dto.getPlayMode())) {
            config.setPlayMode(normalize(dto.getPlayMode(), PLAY_MODES, "播放模式"));
        }
        if (StringUtils.hasText(dto.getStatus())) {
            config.setStatus(normalize(dto.getStatus(), CONFIG_STATUSES, "大屏状态"));
        }
        if (dto.getIsDefault() != null) {
            config.setIsDefault(dto.getIsDefault() == 0 ? 0 : 1);
        }
        if (dto.getRemark() != null) {
            config.setRemark(dto.getRemark());
        }
        // 前端同名字段（别名）：refreshSeconds / theme 模式 / showXxx 模块开关
        if (dto.getRefreshSeconds() != null) {
            config.setRefreshInterval(dto.getRefreshSeconds());
        }
        if (StringUtils.hasText(dto.getTheme())) {
            config.setThemeJson(writeThemeMode(config.getThemeJson(), dto.getTheme()));
        }
        if (hasModuleFlag(dto)) {
            config.setModulesJson(mergeModuleFlags(config.getModulesJson(),
                    dto.getShowAnnouncement(), dto.getShowMedal(), dto.getShowPoints(), dto.getShowResult()));
        }
        if (!StringUtils.hasText(config.getModulesJson())) {
            config.setModulesJson(DEFAULT_MODULES_JSON);
        }
        if (config.getRefreshInterval() == null) {
            config.setRefreshInterval(DEFAULT_REFRESH_INTERVAL);
        }
        if (config.getRotateSeconds() == null) {
            config.setRotateSeconds(DEFAULT_ROTATE_SECONDS);
        }
        if (!StringUtils.hasText(config.getResolution())) {
            config.setResolution(DEFAULT_RESOLUTION);
        }
        if (!StringUtils.hasText(config.getPlayMode())) {
            config.setPlayMode(ScreenConfig.PLAY_MODE_CYCLE);
        }
        if (!StringUtils.hasText(config.getStatus())) {
            config.setStatus(ScreenConfig.STATUS_ENABLED);
        }
        if (config.getIsDefault() == null) {
            config.setIsDefault(1);
        }
    }

    private boolean hasModuleFlag(ScreenConfigSaveDTO dto) {
        return dto.getShowAnnouncement() != null || dto.getShowMedal() != null
                || dto.getShowPoints() != null || dto.getShowResult() != null;
    }

    /**
     * 把前端模块开关合并进 {@code modules_json} 的 {@code enabled} 容器（保留其它键）。
     *
     * @param modulesJson        既有 modules_json（可空）
     * @param showAnnouncement   是否显示公告（可空表示不修改）
     * @param showMedal          是否显示奖牌榜（可空表示不修改）
     * @param showPoints         是否显示团体总分（可空表示不修改）
     * @param showResult         是否显示最新成绩（可空表示不修改）
     * @return 合并后的 JSON 文本
     */
    private String mergeModuleFlags(String modulesJson, Integer showAnnouncement, Integer showMedal,
                                    Integer showPoints, Integer showResult) {
        ObjectNode root = readObjectNode(modulesJson);
        if (root == null) {
            root = objectMapper.createObjectNode();
        }
        JsonNode enabledNode = root.path(MODULES_ENABLED_KEY);
        ObjectNode enabled = enabledNode.isObject()
                ? (ObjectNode) enabledNode : root.putObject(MODULES_ENABLED_KEY);
        putFlag(enabled, MODULE_ANNOUNCEMENT, showAnnouncement);
        putFlag(enabled, MODULE_MEDAL, showMedal);
        putFlag(enabled, MODULE_TEAM_SCORE, showPoints);
        putFlag(enabled, MODULE_RESULT, showResult);
        if (!root.has(MODULES_ORDER_KEY) || !root.path(MODULES_ORDER_KEY).isArray()) {
            root.putArray(MODULES_ORDER_KEY)
                    .add(MODULE_MEDAL).add(MODULE_TEAM_SCORE).add(MODULE_RESULT).add(MODULE_ANNOUNCEMENT);
        }
        return root.toString();
    }

    private void putFlag(ObjectNode enabled, String moduleCode, Integer flag) {
        if (flag != null) {
            enabled.put(moduleCode, flag != 0);
        }
    }

    /**
     * 解析 JSON 文本为对象节点；空文本或非法 JSON 返回 {@code null}。
     */
    private ObjectNode readObjectNode(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node instanceof ObjectNode objectNode ? objectNode : null;
        } catch (JsonProcessingException ex) {
            log.warn("大屏 JSON 无法解析：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * 新建默认大屏方案并落库（含令牌），保证首次访问即可拿到可用令牌。
     */
    private ScreenConfig createDefaultConfig(Long meetId, String token) {
        ScreenConfig config = new ScreenConfig();
        config.setMeetId(meetId);
        config.setScreenName(DEFAULT_SCREEN_NAME);
        config.setScreenCode(buildDefaultCode(meetId));
        config.setModulesJson(DEFAULT_MODULES_JSON);
        config.setRefreshInterval(DEFAULT_REFRESH_INTERVAL);
        config.setThemeJson(writeToken(DEFAULT_THEME_JSON, StringUtils.hasText(token) ? token : newToken()));
        config.setResolution(DEFAULT_RESOLUTION);
        config.setRotateSeconds(DEFAULT_ROTATE_SECONDS);
        config.setPlayMode(ScreenConfig.PLAY_MODE_CYCLE);
        config.setStatus(ScreenConfig.STATUS_ENABLED);
        config.setIsDefault(1);
        config.setRemark("首次访问大屏配置时自动创建");
        this.save(config);
        log.info("自动创建默认大屏配置：meetId={}, configId={}, screenCode={}",
                meetId, config.getId(), config.getScreenCode());
        return config;
    }

    /**
     * 默认方案编码：{@code SCREEN-{meetId}-{8 位随机串}}。
     * <p>
     * 唯一键 {@code uk_meet_screen_code (meet_id, screen_code)} 不含 {@code deleted}，
     * 逻辑删除行仍占用编码，因此用随机后缀避免与历史（含已删除）行冲突。
     */
    private String buildDefaultCode(Long meetId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "SCREEN-" + meetId + "-" + suffix;
    }

    /**
     * UK(meet_id, screen_code) 校验（逻辑删除行不参与比较）。
     */
    private void checkCodeUnique(Long meetId, String screenCode, Long excludeId) {
        if (!StringUtils.hasText(screenCode)) {
            return;
        }
        LambdaQueryWrapper<ScreenConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScreenConfig::getMeetId, meetId);
        wrapper.eq(ScreenConfig::getScreenCode, screenCode.trim());
        wrapper.ne(excludeId != null, ScreenConfig::getId, excludeId);
        if (this.count(wrapper) > 0) {
            throw new BizException(ResultCode.CONFLICT, "该届次下大屏方案编码已存在：" + screenCode);
        }
    }

    private void validateJson(String text, String label) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        try {
            objectMapper.readTree(text);
        } catch (JsonProcessingException ex) {
            throw new BizException(ResultCode.PARAM_ERROR, label + "必须是合法的 JSON 文本");
        }
    }

    private String normalize(String value, Set<String> allowed, String label) {
        String upper = value.trim().toUpperCase();
        if (!allowed.contains(upper)) {
            throw new BizException(ResultCode.PARAM_ERROR, "非法的" + label + "：" + value);
        }
        return upper;
    }

    private ScreenConfig requireConfig(Long id) {
        if (id == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "大屏配置 ID 不能为空");
        }
        ScreenConfig config = this.getById(id);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "大屏配置不存在");
        }
        return config;
    }

    /**
     * 解析届次：入参优先，其次 {@link MeetContext}；与上下文不一致按越权处理。
     */
    private Long requireMeetId(Long meetId) {
        Long current = MeetContext.get();
        Long resolved = meetId != null ? meetId : current;
        if (resolved == null) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "届次不能为空（请携带 X-Meet-Id 请求头或在参数中指定 meetId）");
        }
        checkMeetContext(resolved);
        return resolved;
    }

    private void checkMeetContext(Long meetId) {
        Long current = MeetContext.get();
        if (current != null && !current.equals(meetId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该届次数据");
        }
    }

    // ==================================================================================
    // 六、文本
    // ==================================================================================

    /**
     * 日期区间显示文本：{@code 2025-05-01 ~ 2025-05-03}，单日只显示一天。
     */
    private String buildDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null) {
            return end.toString();
        }
        if (end == null) {
            return start.toString();
        }
        if (start.isEqual(end)) {
            return start.toString();
        }
        return start + " ~ " + end;
    }

    /**
     * 届次状态显示名（大屏副标题）。
     */
    private String meetStatusText(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        switch (status) {
            case "DRAFT":
                return "筹备中";
            case "REG_OPEN":
                return "报名中";
            case "REG_CLOSED":
                return "报名截止";
            case "SCHEDULING":
                return "编排中";
            case "SCHEDULED":
                return "编排完成";
            case "RUNNING":
                return "比赛进行中";
            case "FINISHED":
                return "已结束";
            case "ARCHIVED":
                return "已归档";
            default:
                return status;
        }
    }
}
