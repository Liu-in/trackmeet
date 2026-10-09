package com.trackmeet.engine.result;

import com.trackmeet.engine.allround.AllroundScoreLookup;
import com.trackmeet.engine.model.AllroundScoreRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风速合法性与全能计分查表单元测试。
 *
 * @author TrackMeet
 */
class WindAndAllroundRuleTest {

    // ------------------------------------------------------------------
    // 风速
    // ------------------------------------------------------------------

    @Test
    @DisplayName("风速合法性：上限 2.0 m/s，顺风逆风取绝对值")
    void shouldCheckWindLegality() {
        assertThat(WindValidator.isLegal(new BigDecimal("1.5"), new BigDecimal("2.0"))).isTrue();
        assertThat(WindValidator.isLegal(new BigDecimal("2.0"), new BigDecimal("2.0"))).isTrue();
        assertThat(WindValidator.isLegal(new BigDecimal("2.5"), new BigDecimal("2.0"))).isFalse();
        assertThat(WindValidator.isLegal(new BigDecimal("-2.5"), new BigDecimal("2.0"))).isFalse();
        assertThat(WindValidator.isLegal(new BigDecimal("-1.8"), new BigDecimal("2.0"))).isTrue();
    }

    @Test
    @DisplayName("风速：未测风速视为合法；未配置上限时取默认 2.0")
    void shouldUseDefaultLimit() {
        assertThat(WindValidator.isLegal(null, new BigDecimal("2.0"))).isTrue();
        assertThat(WindValidator.isLegal(new BigDecimal("2.0"), null)).isTrue();
        assertThat(WindValidator.isLegal(new BigDecimal("3.0"), null)).isFalse();
        assertThat(WindValidator.isLegal(new BigDecimal("3.0"), new BigDecimal("4.0"))).isTrue();
        assertThat(WindValidator.DEFAULT_MAX_LEGAL).isEqualByComparingTo("2.0");
    }

    @Test
    @DisplayName("风速适用性：短跑/跨栏/跳跃计风速，中长跑/投掷/接力不计")
    void shouldCheckWindApplicability() {
        assertThat(WindValidator.isApplicable("TRACK", "SPRINT")).isTrue();
        assertThat(WindValidator.isApplicable("TRACK", "HURDLE")).isTrue();
        assertThat(WindValidator.isApplicable("FIELD", "JUMP")).isTrue();
        assertThat(WindValidator.isApplicable("TRACK", "LONG")).isFalse();
        assertThat(WindValidator.isApplicable("TRACK", "MIDDLE")).isFalse();
        assertThat(WindValidator.isApplicable("TRACK", "WALK")).isFalse();
        assertThat(WindValidator.isApplicable("FIELD", "THROW")).isFalse();
        assertThat(WindValidator.isApplicable("RELAY", "RELAY")).isFalse();
        assertThat(WindValidator.isApplicable(null, null)).isFalse();
    }

    @Test
    @DisplayName("超风速提示文案包含风速与上限")
    void shouldBuildIllegalMessage() {
        String message = WindValidator.illegalMessage(new BigDecimal("3.2"), new BigDecimal("2.0"));
        assertThat(message).contains("3.2").contains("2.0");
    }

    // ------------------------------------------------------------------
    // 全能查表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("全能查表：命中成绩区间取分")
    void shouldLookupAllroundScore() {
        List<AllroundScoreRow> table = allroundTable();

        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("11.05"), "DEC")).isEqualByComparingTo("900");
        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("11.15"), "DEC")).isEqualByComparingTo("880");
        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("11.25"), "DEC")).isEqualByComparingTo("860");
    }

    @Test
    @DisplayName("全能查表：区间倒序书写同样命中")
    void shouldNormalizeReversedRange() {
        List<AllroundScoreRow> table = new ArrayList<>();
        table.add(AllroundScoreRow.builder().tableCode("DEC").performanceMin(new BigDecimal("11.29"))
                .performanceMax(new BigDecimal("11.20")).pointValue(new BigDecimal("860")).build());

        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("11.25"), "DEC")).isEqualByComparingTo("860");
        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("11.19"), "DEC")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("全能查表：未命中、空表、成绩为空、表编码不匹配均返回 0")
    void shouldReturnZeroWhenNotFound() {
        List<AllroundScoreRow> table = allroundTable();

        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("12.00"), "DEC")).isEqualByComparingTo("0");
        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("11.05"), "OTHER")).isEqualByComparingTo("0");
        assertThat(AllroundScoreLookup.lookup(table, null, "DEC")).isEqualByComparingTo("0");
        assertThat(AllroundScoreLookup.lookup(new ArrayList<>(), new BigDecimal("11.05"), "DEC")).isEqualByComparingTo("0");
        assertThat(AllroundScoreLookup.lookup(null, new BigDecimal("11.05"), null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("全能查表：按性别与子类型过滤")
    void shouldFilterByGenderAndSubType() {
        List<AllroundScoreRow> table = Arrays.asList(
                AllroundScoreRow.builder().tableCode("HEC").gender("MALE").subEventType("JUMP")
                        .performanceMin(new BigDecimal("6.00")).performanceMax(new BigDecimal("6.09"))
                        .pointValue(new BigDecimal("900")).build(),
                AllroundScoreRow.builder().tableCode("HEC").gender("FEMALE").subEventType("JUMP")
                        .performanceMin(new BigDecimal("6.00")).performanceMax(new BigDecimal("6.09"))
                        .pointValue(new BigDecimal("700")).build());

        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("6.05"), "HEC", "MALE", "JUMP"))
                .isEqualByComparingTo("900");
        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("6.05"), "HEC", "FEMALE", "JUMP"))
                .isEqualByComparingTo("700");
        assertThat(AllroundScoreLookup.lookup(table, new BigDecimal("6.05"), "HEC", "MALE", "THROW"))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("全能总分：各项得分求和（缺项按 0 计）")
    void shouldSumAllroundTotal() {
        assertThat(AllroundScoreLookup.total(Arrays.asList(
                new BigDecimal("900"), new BigDecimal("880"), null, new BigDecimal("750"))))
                .isEqualByComparingTo("2530");
        assertThat(AllroundScoreLookup.total(new ArrayList<>())).isEqualByComparingTo("0");
        assertThat(AllroundScoreLookup.total(null)).isEqualByComparingTo("0");
    }

    private List<AllroundScoreRow> allroundTable() {
        return Arrays.asList(
                AllroundScoreRow.builder().tableCode("DEC").gender("MALE").subEventType("SPRINT").unit("s")
                        .performanceMin(new BigDecimal("11.00")).performanceMax(new BigDecimal("11.09"))
                        .pointValue(new BigDecimal("900")).scoreDirection("DESC").build(),
                AllroundScoreRow.builder().tableCode("DEC").gender("MALE").subEventType("SPRINT").unit("s")
                        .performanceMin(new BigDecimal("11.10")).performanceMax(new BigDecimal("11.19"))
                        .pointValue(new BigDecimal("880")).scoreDirection("DESC").build(),
                AllroundScoreRow.builder().tableCode("DEC").gender("MALE").subEventType("SPRINT").unit("s")
                        .performanceMin(new BigDecimal("11.20")).performanceMax(new BigDecimal("11.29"))
                        .pointValue(new BigDecimal("860")).scoreDirection("DESC").build());
    }
}
