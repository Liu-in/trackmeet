package com.trackmeet.engine.result;

import com.trackmeet.engine.model.ResultRecord;
import com.trackmeet.engine.model.TieBreakRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 名次解析与平局处理单元测试：径赛/田赛方向、特殊状态、次优成绩与轮次平局、并列名次顺延。
 *
 * @author TrackMeet
 */
class RankResolverTest {

    @Test
    @DisplayName("径赛：成绩越小名次越前（1、2、3）")
    void shouldRankTrackAscending() {
        List<ResultRecord> results = Arrays.asList(
                record(1L, 1, "11.50", "VALID"),
                record(2L, 1, "11.00", "VALID"),
                record(3L, 1, "11.20", "VALID"));

        List<ResultRecord> ranked = RankResolver.resolveRank(results, true, TieBreakRule.defaultRule());

        assertThat(ranked).extracting(ResultRecord::getRegistrationId).containsExactly(2L, 3L, 1L);
        assertThat(ranked).extracting(ResultRecord::getRankNo).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("田赛：成绩越大名次越前")
    void shouldRankFieldDescending() {
        List<ResultRecord> results = Arrays.asList(
                record(1L, 1, "5.80", "VALID"),
                record(2L, 1, "6.35", "VALID"),
                record(3L, 1, "6.10", "VALID"));

        List<ResultRecord> ranked = RankResolver.resolveRank(results, false, TieBreakRule.defaultRule());

        assertThat(ranked).extracting(ResultRecord::getRegistrationId).containsExactly(2L, 3L, 1L);
        assertThat(ranked).extracting(ResultRecord::getRankNo).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("特殊状态：DNS/DNF/DSQ/NM 不参与名次且排在最后")
    void shouldSkipInvalidStatus() {
        List<ResultRecord> results = new ArrayList<>();
        results.add(record(1L, 1, "11.20", "VALID"));
        results.add(record(2L, null, null, "DNS"));
        results.add(record(3L, 1, "11.10", "VALID"));
        results.add(record(4L, null, null, "DSQ"));
        results.add(record(5L, null, null, "DNF"));
        results.add(record(6L, null, null, "NM"));

        List<ResultRecord> ranked = RankResolver.resolveRank(results, true, TieBreakRule.defaultRule());

        assertThat(ranked).hasSize(6);
        assertThat(ranked.get(0).getRegistrationId()).isEqualTo(3L);
        assertThat(ranked.get(0).getRankNo()).isEqualTo(1);
        assertThat(ranked.get(1).getRegistrationId()).isEqualTo(1L);
        assertThat(ranked.get(1).getRankNo()).isEqualTo(2);
        for (int i = 2; i < 6; i++) {
            assertThat(ranked.get(i).getRankNo()).isNull();
        }
    }

    @Test
    @DisplayName("平局规则一：成绩相同比较次优成绩（田赛多次试跳）")
    void shouldBreakTieBySecondBest() {
        ResultRecord first = record(1L, 1, "5.00", "VALID");
        first.setAttempts(Arrays.asList(new BigDecimal("5.00"), new BigDecimal("4.50")));
        ResultRecord second = record(2L, 1, "5.00", "VALID");
        second.setAttempts(Arrays.asList(new BigDecimal("5.00"), new BigDecimal("4.80")));

        List<ResultRecord> ranked = RankResolver.resolveRank(Arrays.asList(first, second), false, TieBreakRule.defaultRule());

        // 次优成绩 4.80 > 4.50，故 2 号第一
        assertThat(ranked).extracting(ResultRecord::getRegistrationId).containsExactly(2L, 1L);
        assertThat(ranked).extracting(ResultRecord::getRankNo).containsExactly(1, 2);
    }

    @Test
    @DisplayName("平局规则二：次优成绩仍相同则比较最好成绩出现的轮次（越早越优）")
    void shouldBreakTieByBestAttemptRound() {
        ResultRecord early = record(1L, 1, "5.00", "VALID");
        early.setAttempts(Arrays.asList(new BigDecimal("5.00"), null));
        ResultRecord late = record(2L, 1, "5.00", "VALID");
        late.setAttempts(Arrays.asList(null, new BigDecimal("5.00")));

        List<ResultRecord> ranked = RankResolver.resolveRank(Arrays.asList(early, late), false, TieBreakRule.defaultRule());

        assertThat(ranked).extracting(ResultRecord::getRegistrationId).containsExactly(1L, 2L);
        assertThat(ranked).extracting(ResultRecord::getRankNo).containsExactly(1, 2);
    }

    @Test
    @DisplayName("完全平局：允许并列则名次为 1、1、3 且显示「（并列）」")
    void shouldAllowParallelRank() {
        List<ResultRecord> results = Arrays.asList(
                record(1L, 1, "11.00", "VALID"),
                record(2L, 1, "11.00", "VALID"),
                record(3L, 1, "11.50", "VALID"));

        List<ResultRecord> ranked = RankResolver.resolveRank(results, true, TieBreakRule.defaultRule());

        assertThat(ranked).extracting(ResultRecord::getRankNo).containsExactly(1, 1, 3);
        assertThat(ranked.get(0).getRankDisplay()).isEqualTo("1（并列）");
        assertThat(ranked.get(1).getRankDisplay()).isEqualTo("1（并列）");
        assertThat(ranked.get(2).getRankDisplay()).isEqualTo("3");
    }

    @Test
    @DisplayName("完全平局：禁止并列时按报名 ID 强制区分")
    void shouldForceOrderWhenParallelNotAllowed() {
        TieBreakRule rule = TieBreakRule.defaultRule();
        rule.setAllowParallelRank(false);
        List<ResultRecord> results = Arrays.asList(
                record(2L, 1, "11.00", "VALID"),
                record(1L, 1, "11.00", "VALID"));

        List<ResultRecord> ranked = RankResolver.resolveRank(results, true, rule);

        assertThat(ranked).extracting(ResultRecord::getRegistrationId).containsExactly(1L, 2L);
        assertThat(ranked).extracting(ResultRecord::getRankNo).containsExactly(1, 2);
        assertThat(ranked.get(0).getRankDisplay()).isEqualTo("1");
    }

    @Test
    @DisplayName("边界：空输入、全无效成绩、单条成绩")
    void shouldHandleBoundaryCases() {
        assertThat(RankResolver.resolveRank(new ArrayList<>(), true, null)).isEmpty();
        assertThat(RankResolver.resolveRank(null, true, null)).isEmpty();

        List<ResultRecord> allInvalid = Arrays.asList(record(1L, null, null, "DNS"), record(2L, null, null, "DNF"));
        List<ResultRecord> ranked = RankResolver.resolveRank(allInvalid, true, null);
        assertThat(ranked).hasSize(2);
        assertThat(ranked).allMatch(item -> item.getRankNo() == null);

        List<ResultRecord> single = RankResolver.resolveRank(
                new ArrayList<>(List.of(record(9L, 1, "11.00", "VALID"))), true, null);
        assertThat(single.get(0).getRankNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("辅助判定：isValid / sameValue")
    void shouldExposeHelpers() {
        assertThat(RankResolver.isValid(record(1L, 1, "11.00", "VALID"))).isTrue();
        assertThat(RankResolver.isValid(record(2L, null, null, "VALID"))).isFalse();
        assertThat(RankResolver.isValid(record(3L, 1, "11.00", "DNS"))).isFalse();
        assertThat(RankResolver.sameValue(record(1L, 1, "11.00", "VALID"), record(2L, 1, "11.00", "VALID"))).isTrue();
        assertThat(RankResolver.sameValue(record(1L, 1, "11.00", "VALID"), record(3L, 1, "11.20", "VALID"))).isFalse();
    }

    private ResultRecord record(Long registrationId, Integer rankNo, String value, String status) {
        return ResultRecord.builder()
                .registrationId(registrationId)
                .athleteId(1000L + registrationId)
                .athleteName("运动员" + registrationId)
                .unitId(10L + registrationId % 3)
                .heatNo(rankNo == null ? 1 : rankNo)
                .status(status)
                .resultRaw(value)
                .resultValue(value == null ? null : new BigDecimal(value))
                .build();
    }
}
