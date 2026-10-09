package com.trackmeet.engine.score;

import com.trackmeet.engine.model.MedalRow;
import com.trackmeet.engine.model.PointAward;
import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.TeamScoreRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 团体总分与奖牌榜单元测试：三档口径、同分并列、金银铜统计、排序规则切换、无效成绩剔除。
 *
 * @author TrackMeet
 */
class StandingCalculatorTest {

    private static final Map<Long, String> UNIT_NAMES = new HashMap<>();

    static {
        UNIT_NAMES.put(1L, "第一中学");
        UNIT_NAMES.put(2L, "第二中学");
        UNIT_NAMES.put(3L, "第三中学");
    }

    @Test
    @DisplayName("团体总分：按 男子/女子/总 三档口径分别汇总")
    void shouldAggregateThreeScoreTypes() {
        List<PointAward> awards = Arrays.asList(
                award(1L, 1L, "MALE", "9", "2"),
                award(1L, 1L, "FEMALE", "7", "0"),
                award(2L, 1L, "MALE", "6", "0"));

        List<TeamScoreRow> rows = StandingCalculator.teamScores(awards, UNIT_NAMES);

        assertThat(rows).hasSize(5);
        assertThat(find(rows, 1L, "MALE").getTotalPoint()).isEqualByComparingTo("11");
        assertThat(find(rows, 1L, "FEMALE").getTotalPoint()).isEqualByComparingTo("7");
        assertThat(find(rows, 1L, "TOTAL").getTotalPoint()).isEqualByComparingTo("18");
        assertThat(find(rows, 2L, "MALE").getTotalPoint()).isEqualByComparingTo("6");
        assertThat(find(rows, 2L, "TOTAL").getTotalPoint()).isEqualByComparingTo("6");
        assertThat(find(rows, 1L, "MALE").getUnitName()).isEqualTo("第一中学");
        // 名次在「组别 × 口径」分区内计算
        assertThat(find(rows, 1L, "MALE").getRankNo()).isEqualTo(1);
        assertThat(find(rows, 2L, "MALE").getRankNo()).isEqualTo(2);
        assertThat(find(rows, 1L, "TOTAL").getRankNo()).isEqualTo(1);
        assertThat(find(rows, 1L, "FEMALE").getRankNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("团体总分：同分并列且后续名次顺延（1、1、3）")
    void shouldRankTiedTeamsInParallel() {
        List<PointAward> awards = Arrays.asList(
                award(1L, 1L, "MALE", "10", "0"),
                award(2L, 1L, "MALE", "10", "0"),
                award(3L, 1L, "MALE", "5", "0"));

        List<TeamScoreRow> rows = StandingCalculator.teamScores(awards, UNIT_NAMES);
        List<TeamScoreRow> maleRows = new ArrayList<>();
        for (TeamScoreRow row : rows) {
            if ("MALE".equals(row.getScoreType())) {
                maleRows.add(row);
            }
        }

        assertThat(maleRows).hasSize(3);
        assertThat(maleRows.get(0).getRankNo()).isEqualTo(1);
        assertThat(maleRows.get(1).getRankNo()).isEqualTo(1);
        assertThat(maleRows.get(2).getRankNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("奖牌榜：仅决赛有效名次 1/2/3 计牌并统计总数")
    void shouldCountMedals() {
        List<ResultRecord> finalResults = Arrays.asList(
                finalRecord(1L, 1, "VALID"),
                finalRecord(2L, 2, "VALID"),
                finalRecord(3L, 3, "VALID"),
                finalRecord(4L, 4, "VALID"),
                finalRecord(5L, 1, "VALID"));

        List<MedalRow> rows = StandingCalculator.medals(finalResults, UNIT_NAMES);

        assertThat(rows).hasSize(2);
        MedalRow first = findByUnit(rows, 1L);
        assertThat(first.getGold()).isEqualTo(1);
        assertThat(first.getSilver()).isEqualTo(1);
        assertThat(first.getBronze()).isEqualTo(1);
        assertThat(first.getTotalMedal()).isEqualTo(3);
        assertThat(first.getRankNo()).isEqualTo(1);

        MedalRow second = findByUnit(rows, 5L);
        assertThat(second.getGold()).isEqualTo(1);
        assertThat(second.getTotalMedal()).isEqualTo(1);
        assertThat(second.getRankNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("奖牌榜排序：默认金牌优先，可切换奖牌总数优先")
    void shouldSupportMedalSortPolicy() {
        List<ResultRecord> finalResults = Arrays.asList(
                finalRecord(1L, 1, "VALID"),
                finalRecord(2L, 2, "VALID"),
                finalRecord(2L, 3, "VALID"),
                finalRecord(3L, 2, "VALID"),
                finalRecord(3L, 3, "VALID"));

        List<MedalRow> goldFirst = StandingCalculator.medals(finalResults, UNIT_NAMES,
                StandingCalculator.MEDAL_SORT_GOLD_FIRST);
        // 1 号单位 1 金 → 第一；2、3 号单位各 1 银 1 铜且完全相同 → 并列第二
        assertThat(goldFirst.get(0).getUnitId()).isEqualTo(1L);
        assertThat(goldFirst.get(1).getRankNo()).isEqualTo(2);
        assertThat(goldFirst.get(2).getRankNo()).isEqualTo(2);

        List<MedalRow> totalFirst = StandingCalculator.medals(finalResults, UNIT_NAMES,
                StandingCalculator.MEDAL_SORT_TOTAL_FIRST);
        assertThat(totalFirst).hasSize(3);
        assertThat(totalFirst.get(0).getTotalMedal()).isGreaterThanOrEqualTo(totalFirst.get(2).getTotalMedal());
    }

    @Test
    @DisplayName("奖牌榜：DSQ/DNF 等无效成绩与名次 4 名以后不计牌")
    void shouldIgnoreInvalidResults() {
        List<ResultRecord> finalResults = Arrays.asList(
                finalRecord(1L, 1, "DSQ"),
                finalRecord(2L, 2, "DNF"),
                finalRecord(3L, 4, "VALID"),
                finalRecord(4L, null, "VALID"));

        assertThat(StandingCalculator.medals(finalResults, UNIT_NAMES)).isEmpty();
    }

    @Test
    @DisplayName("边界：空输入返回空列表")
    void shouldHandleEmptyInput() {
        assertThat(StandingCalculator.teamScores(new ArrayList<>(), UNIT_NAMES)).isEmpty();
        assertThat(StandingCalculator.teamScores(null, null)).isEmpty();
        assertThat(StandingCalculator.medals(new ArrayList<>(), UNIT_NAMES)).isEmpty();
        assertThat(StandingCalculator.medals(null, null)).isEmpty();
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private PointAward award(Long unitId, Long groupId, String gender, String rankPoint, String bonusPoint) {
        return PointAward.builder()
                .registrationId(unitId * 100)
                .athleteId(unitId * 100 + 1)
                .unitId(unitId)
                .groupId(groupId)
                .gender(gender)
                .rankNo(1)
                .rankPoint(new BigDecimal(rankPoint))
                .bonusPoint(new BigDecimal(bonusPoint))
                .relayFactor(BigDecimal.ONE)
                .totalPoint(new BigDecimal(rankPoint).add(new BigDecimal(bonusPoint)))
                .build();
    }

    private ResultRecord finalRecord(Long unitId, Integer rankNo, String status) {
        return ResultRecord.builder()
                .registrationId(unitId * 100 + (rankNo == null ? 0 : rankNo))
                .athleteId(unitId * 100 + 7)
                .athleteName("运动员" + unitId)
                .unitId(unitId)
                .groupId(1L)
                .gender("MALE")
                .rankNo(rankNo)
                .status(status)
                .resultValue(new BigDecimal("11.00"))
                .build();
    }

    private TeamScoreRow find(List<TeamScoreRow> rows, Long unitId, String scoreType) {
        return rows.stream()
                .filter(row -> unitId.equals(row.getUnitId()) && scoreType.equals(row.getScoreType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 " + unitId + "/" + scoreType + " 的团体分"));
    }

    private MedalRow findByUnit(List<MedalRow> rows, Long unitId) {
        return rows.stream()
                .filter(row -> unitId.equals(row.getUnitId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到单位 " + unitId + " 的奖牌行"));
    }
}
