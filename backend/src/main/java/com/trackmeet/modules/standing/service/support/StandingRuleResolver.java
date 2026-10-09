package com.trackmeet.modules.standing.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackmeet.engine.model.BonusRuleRow;
import com.trackmeet.engine.model.ScoreRuleRow;
import com.trackmeet.modules.event.entity.BonusRule;
import com.trackmeet.modules.event.entity.Event;
import com.trackmeet.modules.event.entity.MeetRuleConfig;
import com.trackmeet.modules.event.entity.ScoreRule;
import com.trackmeet.modules.event.entity.ScoreRuleItem;
import com.trackmeet.modules.event.mapper.BonusRuleMapper;
import com.trackmeet.modules.event.mapper.MeetRuleConfigMapper;
import com.trackmeet.modules.event.mapper.ScoreRuleItemMapper;
import com.trackmeet.modules.event.mapper.ScoreRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 规则装配器：把库内的名次分 / 加分规则读成引擎模型。
 * <p>
 * 规则来源<b>全部读库</b>（需求 R-03 / R-04 / R-05 / R-06 / R-21 / R-22）：
 * <ul>
 *     <li>名次分：{@code score_rule} + {@code score_rule_item} → {@link ScoreRuleRow}；</li>
 *     <li>破纪录加分与接力倍数：{@code bonus_rule} → {@link BonusRuleRow}；</li>
 *     <li>届次默认与团体分口径：{@code meet_rule_config}。</li>
 * </ul>
 * 名次分方案的选择沿用 ADR-03「就近覆盖」：项目绑定方案（{@code event.score_rule_id}）
 * → 届次默认方案（{@code meet_rule_config.score_rule_id}）→ 届次默认标记方案
 * （{@code score_rule.is_default=1}）→ 按组别 / 性别 / 项目类型打分取最优。
 *
 * @author TrackMeet
 */
@Component
@RequiredArgsConstructor
public class StandingRuleResolver {

    /** 名次区间行缺省起点 */
    private static final int DEFAULT_RANK_FROM = 1;

    private final ScoreRuleMapper scoreRuleMapper;
    private final ScoreRuleItemMapper scoreRuleItemMapper;
    private final BonusRuleMapper bonusRuleMapper;
    private final MeetRuleConfigMapper meetRuleConfigMapper;

    /**
     * 届次级规则配置快照（团体分口径、奖牌榜排序）。
     * <p>
     * {@code meet_rule_config} 与届次一对一；缺失时返回 {@code null}，由调用方取默认值。
     *
     * @param meetId 届次 ID
     * @return 届次规则配置，不存在时为 {@code null}
     */
    public MeetRuleConfig loadMeetRuleConfig(Long meetId) {
        LambdaQueryWrapper<MeetRuleConfig> wrapper = new LambdaQueryWrapper<MeetRuleConfig>()
                .eq(MeetRuleConfig::getMeetId, meetId)
                .last("LIMIT 1");
        return meetRuleConfigMapper.selectOne(wrapper);
    }

    /**
     * 破纪录加分是否需要计入团体总分（R-21）。
     * <p>
     * 默认「计入」；仅当 {@code meet_rule_config.bonus_in_team_score = 0} 时排除。
     *
     * @param meetRuleConfig 届次规则配置（可空）
     * @return {@code true} 表示破纪录加分计入团体总分
     */
    public boolean bonusInTeamScore(MeetRuleConfig meetRuleConfig) {
        if (meetRuleConfig == null || meetRuleConfig.getBonusInTeamScore() == null) {
            return true;
        }
        return meetRuleConfig.getBonusInTeamScore() != 0;
    }

    /**
     * 奖牌榜排序规则（R-22），缺省 {@code GOLD_FIRST}。
     *
     * @param meetRuleConfig 届次规则配置（可空）
     * @return {@code GOLD_FIRST} / {@code TOTAL_FIRST} / {@code POINT_FIRST}
     */
    public String medalSort(MeetRuleConfig meetRuleConfig) {
        if (meetRuleConfig == null || meetRuleConfig.getMedalSort() == null
                || meetRuleConfig.getMedalSort().isBlank()) {
            return "GOLD_FIRST";
        }
        return meetRuleConfig.getMedalSort().trim().toUpperCase();
    }

    /**
     * 装载该届次全部启用中的加分规则（破纪录 + 接力双倍），转为引擎模型。
     *
     * @param meetId 届次 ID
     * @return 加分规则模型列表（按 {@code priority} 升序）
     */
    public List<BonusRuleRow> loadBonusRules(Long meetId) {
        List<BonusRule> entities = bonusRuleMapper.selectList(new LambdaQueryWrapper<BonusRule>()
                .eq(BonusRule::getMeetId, meetId)
                .eq(BonusRule::getStatus, BonusRule.STATUS_ENABLED)
                .orderByAsc(BonusRule::getPriority));
        return entities.stream().map(this::toBonusRuleRow).collect(Collectors.toList());
    }

    /**
     * 规则编码 → 规则 ID 映射（用于回填 {@code points.bonus_rule_ids}）。
     *
     * @param meetId 届次 ID
     * @return 映射（{@code rule_code} → {@code bonus_rule.id}）
     */
    public Map<String, Long> loadBonusRuleIdByCode(Long meetId) {
        List<BonusRule> entities = bonusRuleMapper.selectList(new LambdaQueryWrapper<BonusRule>()
                .eq(BonusRule::getMeetId, meetId));
        Map<String, Long> map = new HashMap<>(entities.size());
        for (BonusRule entity : entities) {
            if (entity.getRuleCode() != null) {
                map.putIfAbsent(entity.getRuleCode(), entity.getId());
            }
        }
        return map;
    }

    /**
     * 是否存在启用的接力加倍规则（R-06）；不存在时按默认倍数 2.00 处理。
     *
     * @param bonusRules 加分规则模型列表
     * @return {@code true} 表示接力项目计双倍分
     */
    public boolean relayDoubleEnabled(List<BonusRuleRow> bonusRules) {
        if (bonusRules == null) {
            return false;
        }
        return bonusRules.stream().anyMatch(rule -> BonusRule.TYPE_RELAY.equalsIgnoreCase(rule.getBonusType()));
    }

    /**
     * 装载该届次全部启用中的名次分方案及其明细。
     * <p>
     * 明细行的 {@code effectiveRoundCode} 取自方案主表 {@code score_rule.effective_round_code}
     * （{@code score_rule_item} 无该列），供引擎按轮次过滤计分规则，避免同一项目多轮次重复计分。
     *
     * @param meetId 届次 ID
     * @param rules  方案 ID → 方案实体（调用 {@link #loadScoreRules(Long)} 得到，避免重复查询方案主表）
     * @return 方案 ID → 明细行（按名次升序）
     */
    public Map<Long, List<ScoreRuleRow>> loadScoreRuleItems(Long meetId, Map<Long, ScoreRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> ruleIds = rules.values().stream().map(ScoreRule::getId).collect(Collectors.toList());
        List<ScoreRuleItem> items = scoreRuleItemMapper.selectList(new LambdaQueryWrapper<ScoreRuleItem>()
                .eq(ScoreRuleItem::getMeetId, meetId)
                .in(ScoreRuleItem::getRuleId, ruleIds)
                .orderByAsc(ScoreRuleItem::getRankNo));
        Map<Long, List<ScoreRuleRow>> grouped = new HashMap<>();
        for (ScoreRuleItem item : items) {
            List<ScoreRuleRow> rows = grouped.computeIfAbsent(item.getRuleId(), key -> new ArrayList<>());
            for (ScoreRuleRow row : expand(item, rules.get(item.getRuleId()))) {
                rows.add(row);
            }
        }
        for (List<ScoreRuleRow> rows : grouped.values()) {
            rows.sort(Comparator.comparingInt(ScoreRuleRow::getRankNo));
        }
        return grouped;
    }

    /**
     * 装载该届次全部名次分方案主表（用于方案匹配与编码回填）。
     *
     * @param meetId 届次 ID
     * @return 方案 ID → 方案实体
     */
    public Map<Long, ScoreRule> loadScoreRules(Long meetId) {
        List<ScoreRule> rules = scoreRuleMapper.selectList(new LambdaQueryWrapper<ScoreRule>()
                .eq(ScoreRule::getMeetId, meetId)
                .orderByAsc(ScoreRule::getId));
        Map<Long, ScoreRule> map = new HashMap<>(rules.size());
        for (ScoreRule rule : rules) {
            map.put(rule.getId(), rule);
        }
        return map;
    }

    /**
     * 为指定项目选择名次分方案（就近覆盖）。
     *
     * @param event          项目实体
     * @param meetRuleConfig 届次规则配置（可空）
     * @param rules          届次内全部方案（ID → 方案）
     * @return 命中的方案，无法匹配时返回 {@code null}
     */
    public ScoreRule resolveScoreRule(Event event, MeetRuleConfig meetRuleConfig, Map<Long, ScoreRule> rules) {
        if (event == null || rules == null || rules.isEmpty()) {
            return null;
        }
        // 1) 项目绑定方案
        ScoreRule bound = rules.get(event.getScoreRuleId());
        if (bound != null) {
            return bound;
        }
        // 2) 届次默认方案
        if (meetRuleConfig != null) {
            ScoreRule meetDefault = rules.get(meetRuleConfig.getScoreRuleId());
            if (meetDefault != null) {
                return meetDefault;
            }
        }
        List<ScoreRule> candidates = rules.values().stream()
                .filter(rule -> rule.getStatus() == null || ScoreRule.STATUS_ENABLED.equalsIgnoreCase(rule.getStatus()))
                .collect(Collectors.toList());
        // 3) 届次默认标记方案
        ScoreRule flagged = candidates.stream()
                .filter(rule -> rule.getIsDefault() != null && rule.getIsDefault() == 1)
                .min(Comparator.comparing(ScoreRule::getId))
                .orElse(null);
        if (flagged != null) {
            return flagged;
        }
        // 4) 按组别 / 性别 / 项目类型匹配度取最优
        return candidates.stream()
                .filter(rule -> matchGroup(rule, event))
                .filter(rule -> matchGender(rule, event))
                .filter(rule -> matchEventType(rule, event))
                .max(Comparator.comparingInt((ScoreRule rule) -> matchScore(rule, event))
                        .thenComparing(ScoreRule::getId, Comparator.reverseOrder()))
                .orElse(null);
    }

    /**
     * 取方案下指定项目可用的名次分明细行。
     *
     * @param ruleId 方案 ID
     * @param items  方案 ID → 明细行
     * @return 名次分表（无名次分方案时返回空列表）
     */
    public List<ScoreRuleRow> scoreRowsOf(Long ruleId, Map<Long, List<ScoreRuleRow>> items) {
        if (ruleId == null || items == null) {
            return new ArrayList<>();
        }
        List<ScoreRuleRow> rows = items.get(ruleId);
        return rows == null ? new ArrayList<>() : rows;
    }

    /**
     * 把库内加分规则转为引擎模型。
     */
    private BonusRuleRow toBonusRuleRow(BonusRule entity) {
        return BonusRuleRow.builder()
                .ruleCode(entity.getRuleCode())
                .bonusType(entity.getBonusType())
                .recordType(entity.getRecordType())
                .bonusPoint(entity.getBonusPoint())
                .stackable(entity.getIsStackable() != null && entity.getIsStackable() == 1)
                .factor(entity.getFactor())
                .inTeamScore(entity.getIsInTeamScore() == null || entity.getIsInTeamScore() == 1)
                .priority(entity.getPriority() == null ? 100 : entity.getPriority())
                .build();
    }

    /**
     * 名次分明细行展开：区间行按 {@code rank_from..rank_to} 摊平为逐名次行。
     * <p>
     * {@code effectiveRoundCode} 取自方案主表 {@code score_rule.effective_round_code}（明细行无该列），
     * 供引擎按轮次过滤计分规则，避免同一项目多轮次重复计分。
     *
     * @param item 名次分明细行
     * @param rule 所属方案主表行（携带生效轮次码，可为空）
     * @return 逐名次规则行
     */
    private List<ScoreRuleRow> expand(ScoreRuleItem item, ScoreRule rule) {
        List<ScoreRuleRow> rows = new ArrayList<>();
        if (item.getPointValue() == null) {
            return rows;
        }
        Integer from = item.getRankFrom() != null ? item.getRankFrom() : item.getRankNo();
        Integer to = item.getRankTo() != null ? item.getRankTo() : from;
        if (from == null) {
            return rows;
        }
        if (to == null || to < from) {
            to = from;
        }
        String effectiveRoundCode = rule == null ? null : rule.getEffectiveRoundCode();
        for (int rank = Math.max(from, DEFAULT_RANK_FROM); rank <= to; rank++) {
            rows.add(ScoreRuleRow.builder()
                    .rankNo(rank)
                    .pointValue(item.getPointValue())
                    .tiePolicy(item.getTiePolicy())
                    .effectiveRoundCode(effectiveRoundCode)
                    .build());
        }
        return rows;
    }

    private boolean matchGroup(ScoreRule rule, Event event) {
        return rule.getGroupId() == null || rule.getGroupId().equals(event.getGroupId());
    }

    private boolean matchGender(ScoreRule rule, Event event) {
        String apply = rule.getApplyGender();
        if (apply == null || apply.isBlank() || "ALL".equalsIgnoreCase(apply)) {
            return true;
        }
        return apply.equalsIgnoreCase(event.getGender());
    }

    private boolean matchEventType(ScoreRule rule, Event event) {
        String apply = rule.getApplyEventType();
        if (apply == null || apply.isBlank() || "ALL".equalsIgnoreCase(apply)) {
            return true;
        }
        return apply.equalsIgnoreCase(event.getEventType());
    }

    /**
     * 匹配度评分：组别精确 +2、性别精确 +1、项目类型精确 +1（越大越优先）。
     */
    private int matchScore(ScoreRule rule, Event event) {
        int score = 0;
        if (rule.getGroupId() != null && rule.getGroupId().equals(event.getGroupId())) {
            score += 2;
        }
        if (rule.getApplyGender() != null && rule.getApplyGender().equalsIgnoreCase(event.getGender())) {
            score += 1;
        }
        if (rule.getApplyEventType() != null && rule.getApplyEventType().equalsIgnoreCase(event.getEventType())) {
            score += 1;
        }
        return score;
    }

    /**
     * 便捷方法：把列表按给定取值函数索引为映射（保留首次出现的值）。
     *
     * @param list   源列表
     * @param keyOf  键函数
     * @param valueOf 值函数
     * @param <T>    元素类型
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 索引映射
     */
    public static <T, K, V> Map<K, V> index(List<T> list, Function<T, K> keyOf, Function<T, V> valueOf) {
        Map<K, V> map = new HashMap<>();
        if (list == null) {
            return map;
        }
        for (T item : list) {
            K key = keyOf.apply(item);
            if (key != null) {
                map.putIfAbsent(key, valueOf.apply(item));
            }
        }
        return map;
    }
}
