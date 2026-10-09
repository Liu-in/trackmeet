package com.trackmeet.engine.advance;

import com.trackmeet.engine.model.AdvanceResult;
import com.trackmeet.engine.model.AdvanceRule;
import com.trackmeet.engine.model.ResultRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动晋级算法单元测试：每组前 N、最好成绩递补、目标人数、超风速与无效成绩剔除、边界并列。
 *
 * @author TrackMeet
 */
class AdvanceCalculatorTest {

    @Test
    @DisplayName("每组前 2 名 + 1 名最好成绩递补")
    void shouldAdvanceGroupTopAndBestLoser() {
        List<ResultRecord> results = Arrays.asList(
                record(1L, 1, 1, "11.00"),
                record(2L, 2, 2, "11.10"),
                record(3L, 1, 3, "11.20"),
                record(4L, 2, 4, "11.30"),
                record(5L, 1, 5, "11.50"),
                record(6L, 2, 6, "11.60"));

        List<AdvanceResult> advanceList = AdvanceCalculator.calculate(results, AdvanceRule.builder()
                .groupTopN(2)
                .bestLoserCount(1)
                .targetCount(0)
                .build());

        assertThat(advanceList).hasSize(5);
        assertThat(advanceList).extracting(AdvanceResult::getRegistrationId).containsExactly(1L, 3L, 2L, 4L, 5L);
        assertThat(advanceList).extracting(AdvanceResult::getAdvanceType)
                .containsExactly("GROUP_TOP", "GROUP_TOP", "GROUP_TOP", "GROUP_TOP", "BEST_LOSER");
        assertThat(advanceList).extracting(AdvanceResult::getAdvanceOrder).containsExactly(1, 2, 3, 4, 5);
        assertThat(advanceList.get(4).getSourceRank()).isEqualTo(5);
        assertThat(advanceList.get(0).getSourceHeatNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("目标人数 8：直接晋级不足时继续从剩余人员递补至达标")
    void shouldFillUpToTargetCount() {
        List<ResultRecord> results = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            results.add(record((long) i, i % 2 == 0 ? 2 : 1, i, String.format("11.%02d", i)));
        }

        List<AdvanceResult> advanceList = AdvanceCalculator.calculate(results, AdvanceRule.builder()
                .groupTopN(2)
                .bestLoserCount(2)
                .targetCount(8)
                .build());

        assertThat(advanceList).hasSize(8);
        assertThat(advanceList).extracting(AdvanceResult::getAdvanceOrder).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        long groupTop = advanceList.stream().filter(item -> "GROUP_TOP".equals(item.getAdvanceType())).count();
        assertThat(groupTop).isEqualTo(4);
    }

    @Test
    @DisplayName("剔除超风速成绩：不参与递补")
    void shouldExcludeIllegalWind() {
        ResultRecord illegal = record(5L, 1, 5, "11.50");
        illegal.setWindLegal(false);
        List<ResultRecord> results = Arrays.asList(
                record(1L, 1, 1, "11.00"),
                record(2L, 2, 2, "11.10"),
                record(3L, 1, 3, "11.20"),
                record(4L, 2, 4, "11.30"),
                illegal,
                record(6L, 2, 6, "11.60"));

        List<AdvanceResult> advanceList = AdvanceCalculator.calculate(results, AdvanceRule.builder()
                .groupTopN(1)
                .bestLoserCount(1)
                .targetCount(0)
                .excludeIllegalWind(true)
                .build());

        assertThat(advanceList).extracting(AdvanceResult::getRegistrationId).containsExactly(1L, 2L, 3L);
        assertThat(advanceList).noneMatch(item -> item.getRegistrationId().equals(5L));
    }

    @Test
    @DisplayName("剔除无效成绩：DNS/DNF/DSQ/NM 不参与晋级")
    void shouldExcludeInvalidStatus() {
        List<ResultRecord> results = new ArrayList<>();
        results.add(record(1L, 1, 1, "11.00"));
        results.add(recordInvalid(2L, 1, "DNS"));
        results.add(recordInvalid(3L, 1, "DSQ"));
        results.add(recordInvalid(4L, 1, "DNF"));
        results.add(recordInvalid(5L, 1, "NM"));

        List<AdvanceResult> advanceList = AdvanceCalculator.calculate(results, AdvanceRule.builder()
                .groupTopN(3)
                .bestLoserCount(2)
                .targetCount(4)
                .build());

        assertThat(advanceList).hasSize(1);
        assertThat(advanceList.get(0).getRegistrationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("边界并列 ALL：与递补最后一名并列者全部录取（可超出名额）")
    void shouldAdvanceAllTiedAtBoundary() {
        List<ResultRecord> results = Arrays.asList(
                record(1L, 1, 1, "11.00"),
                record(2L, 2, 2, "11.10"),
                record(3L, 1, 3, "11.20"),
                record(4L, 2, 4, "11.30"),
                record(5L, 1, 5, "11.50"),
                record(6L, 2, 5, "11.50"));

        List<AdvanceResult> advanceList = AdvanceCalculator.calculate(results, AdvanceRule.builder()
                .groupTopN(1)
                .bestLoserCount(3)
                .targetCount(0)
                .tiePolicy("ALL")
                .build());

        // 直接晋级 2 人 + 递补 3 人；递补第 3 名（r5）与 r6 成绩并列 → 均录取
        assertThat(advanceList).hasSize(6);
        assertThat(advanceList).extracting(AdvanceResult::getRegistrationId).contains(5L, 6L);
    }

    @Test
    @DisplayName("边界并列 TRUNCATE：严格按名额截断，并列者不额外录取")
    void shouldTruncateWhenConfigured() {
        List<ResultRecord> results = Arrays.asList(
                record(1L, 1, 1, "11.00"),
                record(2L, 2, 2, "11.10"),
                record(3L, 1, 3, "11.20"),
                record(4L, 2, 4, "11.30"),
                record(5L, 1, 5, "11.50"),
                record(6L, 2, 5, "11.50"));

        List<AdvanceResult> advanceList = AdvanceCalculator.calculate(results, AdvanceRule.builder()
                .groupTopN(1)
                .bestLoserCount(3)
                .targetCount(0)
                .tiePolicy("TRUNCATE")
                .build());

        assertThat(advanceList).hasSize(5);
        assertThat(advanceList).extracting(AdvanceResult::getRegistrationId).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("单组（无组次）：按成绩整体取前 N")
    void shouldHandleSingleHeat() {
        List<ResultRecord> results = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            results.add(record((long) i, null, i, String.format("11.%02d", i)));
        }

        List<AdvanceResult> advanceList = AdvanceCalculator.calculate(results, AdvanceRule.builder()
                .groupTopN(3)
                .bestLoserCount(0)
                .targetCount(0)
                .build());

        assertThat(advanceList).hasSize(3);
        assertThat(advanceList).extracting(AdvanceResult::getRegistrationId).containsExactly(1L, 2L, 3L);
        assertThat(advanceList).allMatch(item -> "GROUP_TOP".equals(item.getAdvanceType()));
    }

    @Test
    @DisplayName("边界：空输入、无有效成绩、规则为空")
    void shouldHandleBoundaryCases() {
        assertThat(AdvanceCalculator.calculate(new ArrayList<>(), AdvanceRule.builder().build())).isEmpty();
        assertThat(AdvanceCalculator.calculate(null, null)).isEmpty();
        assertThat(AdvanceCalculator.calculate(Arrays.asList(recordInvalid(1L, 1, "DNS")), null)).isEmpty();
        assertThat(AdvanceCalculator.calculate(Arrays.asList(record(1L, 1, 1, "11.00")), null)).hasSize(1);
    }

    private ResultRecord record(Long registrationId, Integer heatNo, Integer rankNo, String value) {
        return ResultRecord.builder()
                .registrationId(registrationId)
                .athleteId(1000L + registrationId)
                .athleteName("运动员" + registrationId)
                .unitId(10L)
                .heatNo(heatNo)
                .rankNo(rankNo)
                .status("VALID")
                .resultRaw(value)
                .resultValue(new BigDecimal(value))
                .windLegal(true)
                .build();
    }

    private ResultRecord recordInvalid(Long registrationId, Integer heatNo, String status) {
        return ResultRecord.builder()
                .registrationId(registrationId)
                .athleteId(1000L + registrationId)
                .athleteName("运动员" + registrationId)
                .unitId(10L)
                .heatNo(heatNo)
                .status(status)
                .build();
    }
}
