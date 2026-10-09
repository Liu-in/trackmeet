package com.trackmeet.engine.score;

import com.trackmeet.engine.model.BonusRuleRow;
import com.trackmeet.engine.model.PointAward;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.ScoreRuleRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 积分计算单元测试：名次分、并列策略（均分/满额/顺延）、破纪录加分（叠加/取最高）、接力双倍。
 *
 * @author TrackMeet
 */
class ScoreCalculatorTest {

    @Test
    @DisplayName("名次分：按名次分表逐名次取值")
    void shouldCalculateRankPoint() {
        List<PointAward> awards = ScoreCalculator.calculate(
                Arrays.asList(record(1L, 1, "MALE"), record(2L, 2, "MALE"), record(3L, 3, "MALE")),
                defaultScoreRule(), new ArrayList<>(), false);

        assertThat(awards).hasSize(3);
        assertThat(awards).extracting(PointAward::getRankPoint)
                .allSatisfy(point -> assertThat(point).isNotNull());
        assertThat(awards.get(0).getRankPoint()).isEqualByComparingTo("9");
        assertThat(awards.get(1).getRankPoint()).isEqualByComparingTo("7");
        assertThat(awards.get(2).getRankPoint()).isEqualByComparingTo("6");
        assertThat(awards.get(0).getTotalPoint()).isEqualByComparingTo("9");
        assertThat(awards.get(0).getRelayFactor()).isEqualByComparingTo("1");
        assertThat(awards.get(0).getGender()).isEqualTo("MALE");
    }

    @Test
    @DisplayName("并列 AVERAGE：并列第 1 各得 (9+7)/2 = 8 分")
    void shouldAverageForParallelRank() {
        List<PointAward> awards = ScoreCalculator.calculate(
                Arrays.asList(record(1L, 1, "MALE"), record(2L, 1, "MALE"), record(3L, 3, "MALE")),
                defaultScoreRule(), new ArrayList<>(), false);

        assertThat(awards).hasSize(3);
        assertThat(awards.get(0).getRankPoint()).isEqualByComparingTo("8");
        assertThat(awards.get(1).getRankPoint()).isEqualByComparingTo("8");
        assertThat(awards.get(2).getRankPoint()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("并列 FULL：并列者各取本名次分值（各 9 分）")
    void shouldGiveFullPointForParallelRank() {
        List<ScoreRuleRow> rule = Arrays.asList(
                row(1, "9", "FULL"), row(2, "7", "FULL"), row(3, "6", "FULL"));

        List<PointAward> awards = ScoreCalculator.calculate(
                Arrays.asList(record(1L, 1, "MALE"), record(2L, 1, "MALE")), rule, new ArrayList<>(), false);

        assertThat(awards.get(0).getRankPoint()).isEqualByComparingTo("9");
        assertThat(awards.get(1).getRankPoint()).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("并列 ORDER：按列表顺序依次取名次分值（9、7）")
    void shouldOrderPointForParallelRank() {
        List<ScoreRuleRow> rule = Arrays.asList(
                row(1, "9", "ORDER"), row(2, "7", "ORDER"), row(3, "6", "ORDER"));

        List<PointAward> awards = ScoreCalculator.calculate(
                Arrays.asList(record(1L, 1, "MALE"), record(2L, 1, "MALE")), rule, new ArrayList<>(), false);

        assertThat(awards.get(0).getRankPoint()).isEqualByComparingTo("9");
        assertThat(awards.get(1).getRankPoint()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("破纪录加分（不叠加）：取最高分并记录命中的规则编码")
    void shouldApplyHighestRecordBonus() {
        ResultRecord champion = record(1L, 1, "MALE");
        champion.setRecordCandidate(true);
        champion.setRecordType("MEET");

        List<BonusRuleRow> bonusRules = Arrays.asList(
                bonus("R-MEET-2", "RECORD", "MEET", "2", false),
                bonus("R-MEET-5", "RECORD", "MEET", "5", false));

        List<PointAward> awards = ScoreCalculator.calculate(
                new ArrayList<>(List.of(champion)), defaultScoreRule(), bonusRules, false);

        assertThat(awards.get(0).getBonusPoint()).isEqualByComparingTo("5");
        assertThat(awards.get(0).getAppliedBonusCodes()).containsExactly("R-MEET-5");
        assertThat(awards.get(0).getTotalPoint()).isEqualByComparingTo("14");
    }

    @Test
    @DisplayName("破纪录加分（叠加）：多个层级相加")
    void shouldStackRecordBonus() {
        ResultRecord champion = record(1L, 2, "MALE");
        champion.setRecordCandidate(true);
        champion.setRecordType("MEET");

        List<BonusRuleRow> bonusRules = Arrays.asList(
                bonus("R-MEET-2", "RECORD", "MEET", "2", true),
                bonus("R-MEET-3", "RECORD", "MEET", "3", true));

        List<PointAward> awards = ScoreCalculator.calculate(
                new ArrayList<>(List.of(champion)), defaultScoreRule(), bonusRules, false);

        assertThat(awards.get(0).getBonusPoint()).isEqualByComparingTo("5");
        assertThat(awards.get(0).getAppliedBonusCodes()).containsExactlyInAnyOrder("R-MEET-2", "R-MEET-3");
        assertThat(awards.get(0).getTotalPoint()).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("接力双倍：(名次分 + 加分) × 2")
    void shouldApplyRelayFactor() {
        ResultRecord relay = record(1L, 1, "MALE");
        relay.setRelay(true);

        List<BonusRuleRow> bonusRules = new ArrayList<>(List.of(
                BonusRuleRow.builder().ruleCode("R-RELAY").bonusType("RELAY")
                        .factor(new BigDecimal("2.00")).priority(10).build()));

        List<PointAward> awards = ScoreCalculator.calculate(
                new ArrayList<>(List.of(relay)), defaultScoreRule(), bonusRules, true);

        assertThat(awards.get(0).getRelayFactor()).isEqualByComparingTo("2.00");
        assertThat(awards.get(0).getTotalPoint()).isEqualByComparingTo("18");
    }

    @Test
    @DisplayName("接力双倍关闭 / 非接力项目：倍数保持 1")
    void shouldNotApplyRelayFactorWhenDisabled() {
        ResultRecord relay = record(1L, 1, "MALE");
        relay.setRelay(true);

        List<PointAward> disabled = ScoreCalculator.calculate(
                new ArrayList<>(List.of(relay)), defaultScoreRule(), new ArrayList<>(), false);
        assertThat(disabled.get(0).getRelayFactor()).isEqualByComparingTo("1");

        List<PointAward> single = ScoreCalculator.calculate(
                Arrays.asList(record(2L, 1, "MALE")), defaultScoreRule(), new ArrayList<>(), true);
        assertThat(single.get(0).getRelayFactor()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("边界：无名次成绩跳过、空输入、名次分表缺行按 0 分、超风速不产生纪录加分")
    void shouldHandleBoundaryCases() {
        assertThat(ScoreCalculator.calculate(new ArrayList<>(), defaultScoreRule(), new ArrayList<>(), false)).isEmpty();
        assertThat(ScoreCalculator.calculate(null, null, null, false)).isEmpty();

        ResultRecord noRank = record(1L, null, "MALE");
        assertThat(ScoreCalculator.calculate(new ArrayList<>(List.of(noRank)), defaultScoreRule(),
                new ArrayList<>(), false)).isEmpty();

        List<PointAward> outOfRange = ScoreCalculator.calculate(
                Arrays.asList(record(2L, 12, "MALE")), defaultScoreRule(), new ArrayList<>(), false);
        assertThat(outOfRange.get(0).getRankPoint()).isEqualByComparingTo("0");

        ResultRecord windIllegal = record(3L, 1, "MALE");
        windIllegal.setWindLegal(false);
        List<PointAward> noBonus = ScoreCalculator.calculate(new ArrayList<>(List.of(windIllegal)),
                defaultScoreRule(), new ArrayList<>(List.of(bonus("R-MEET", "RECORD", "MEET", "5", false))), false);
        assertThat(noBonus.get(0).getBonusPoint()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("按轮次过滤计分规则：仅生效轮次匹配的规则参与计分")
    void shouldFilterScoreRuleByRound() {
        List<ScoreRuleRow> rule = new ArrayList<>();
        rule.add(row(1, "9", null));
        rule.add(row(2, "7", null));
        ScoreRuleRow finalOnly = ScoreRuleRow.builder().rankNo(1).pointValue(new BigDecimal("3"))
                .effectiveRoundCode("FINAL").build();
        rule.add(finalOnly);

        ResultRecord champion = record(1L, 1, "MALE");

        // 传入 FINAL 时命中 FINAL 专属规则（同名次后写入者覆盖）
        List<PointAward> finalRound = ScoreCalculator.calculate(
                new ArrayList<>(List.of(champion)), rule, new ArrayList<>(), false, "FINAL");
        assertThat(finalRound.get(0).getRankPoint()).isEqualByComparingTo("3");

        // 传入 PRE 时剔除 FINAL 专属规则，回退通用规则
        List<PointAward> preRound = ScoreCalculator.calculate(
                new ArrayList<>(List.of(champion)), rule, new ArrayList<>(), false, "PRE");
        assertThat(preRound.get(0).getRankPoint()).isEqualByComparingTo("9");

        // 不传轮次（4 参重载）时不做过滤，向后兼容
        List<PointAward> noFilter = ScoreCalculator.calculate(
                new ArrayList<>(List.of(champion)), rule, new ArrayList<>(), false);
        assertThat(noFilter.get(0).getRankPoint()).isEqualByComparingTo("3");
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private List<ScoreRuleRow> defaultScoreRule() {
        return Arrays.asList(
                row(1, "9", null), row(2, "7", null), row(3, "6", null), row(4, "5", null),
                row(5, "4", null), row(6, "3", null), row(7, "2", null), row(8, "1", null));
    }

    private ScoreRuleRow row(int rankNo, String point, String tiePolicy) {
        return ScoreRuleRow.builder().rankNo(rankNo).pointValue(new BigDecimal(point)).tiePolicy(tiePolicy).build();
    }

    private BonusRuleRow bonus(String code, String type, String recordType, String point, boolean stackable) {
        return BonusRuleRow.builder().ruleCode(code).bonusType(type).recordType(recordType)
                .bonusPoint(new BigDecimal(point)).stackable(stackable).build();
    }

    private ResultRecord record(Long registrationId, Integer rankNo, String gender) {
        return ResultRecord.builder()
                .registrationId(registrationId)
                .athleteId(1000L + registrationId)
                .athleteName("运动员" + registrationId)
                .unitId(10L)
                .groupId(1L)
                .gender(gender)
                .rankNo(rankNo)
                .status("VALID")
                .resultValue(new BigDecimal("11.00"))
                .windLegal(true)
                .build();
    }
}
