package com.trackmeet.engine.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目时长估算单元测试：径赛分组时长、田赛人数×次数、全能整段与上下限。
 *
 * @author TrackMeet
 */
class DurationEstimatorTest {

    @Test
    @DisplayName("径赛：短跑 3 组 = 6×3 + 5 = 23 分钟")
    void shouldEstimateSprintDuration() {
        assertThat(DurationEstimator.estimate("TRACK", "SPRINT", 24, 3, 3)).isEqualTo(23);
    }

    @Test
    @DisplayName("径赛：中跑 1 组、长跑 1 组、跨栏 2 组")
    void shouldEstimateTrackDurations() {
        assertThat(DurationEstimator.estimate("TRACK", "MIDDLE", 12, 1, 0)).isEqualTo(15);
        assertThat(DurationEstimator.estimate("TRACK", "LONG", 20, 1, 0)).isEqualTo(23);
        assertThat(DurationEstimator.estimate("TRACK", "HURDLE", 16, 2, 0)).isEqualTo(21);
        assertThat(DurationEstimator.estimate("TRACK", "STEEPLE", 10, 1, 0)).isEqualTo(23);
        assertThat(DurationEstimator.estimate("TRACK", "WALK", 10, 1, 0)).isEqualTo(25);
    }

    @Test
    @DisplayName("接力：4 组 × 8 分钟 + 5 = 37 分钟")
    void shouldEstimateRelayDuration() {
        assertThat(DurationEstimator.estimate("RELAY", "RELAY", 32, 4, 0)).isEqualTo(37);
    }

    @Test
    @DisplayName("田赛：10 人 × 3 次 × 1.2 = 36 分钟")
    void shouldEstimateFieldDuration() {
        assertThat(DurationEstimator.estimate("FIELD", "JUMP", 10, 1, 3)).isEqualTo(36);
    }

    @Test
    @DisplayName("田赛上下限：人少时不低于 15 分钟，人多时不超过 150 分钟")
    void shouldClampFieldDuration() {
        assertThat(DurationEstimator.estimate("FIELD", "THROW", 1, 1, 3)).isEqualTo(15);
        assertThat(DurationEstimator.estimate("FIELD", "THROW", 100, 1, 5)).isEqualTo(150);
    }

    @Test
    @DisplayName("田赛：未配置次数时按默认 3 次计算")
    void shouldUseDefaultAttemptCount() {
        assertThat(DurationEstimator.estimate("FIELD", "JUMP", 9, 1, 0))
                .isEqualTo(DurationEstimator.estimate("FIELD", "JUMP", 9, 1, 3));
    }

    @Test
    @DisplayName("全能：整段 180 分钟")
    void shouldEstimateAllroundDuration() {
        assertThat(DurationEstimator.estimate("ALLROUND", null, 12, 1, 0)).isEqualTo(180);
    }

    @Test
    @DisplayName("兜底：项目类型为空按径赛短跑，组数为 0 按 1 组，且不低于 10 分钟")
    void shouldFallbackToDefaults() {
        assertThat(DurationEstimator.estimate(null, null, 8, 1, 3)).isEqualTo(11);
        assertThat(DurationEstimator.estimate("TRACK", null, 8, 0, 3)).isEqualTo(11);
        assertThat(DurationEstimator.estimate("TRACK", "SPRINT", 0, 1, 0)).isGreaterThanOrEqualTo(10);
    }
}
