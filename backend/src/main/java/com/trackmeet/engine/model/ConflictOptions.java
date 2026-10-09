package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 冲突检测开关与阈值（来自 meet_rule_config，不硬编码）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 运动员兼项最小间隔（分钟），来自 meet_rule_config.conflict_interval_min */
    @Builder.Default
    private int athleteIntervalMin = 30;

    /** 裁判连场最小间隔（分钟），来自 meet_rule_config.referee_interval_min */
    @Builder.Default
    private int refereeIntervalMin = 15;

    /** 是否检测场地冲突 */
    @Builder.Default
    private boolean checkVenue = true;

    /** 是否检测裁判冲突 */
    @Builder.Default
    private boolean checkReferee = true;

    /** 是否检测运动员兼项与间隔 */
    @Builder.Default
    private boolean checkAthlete = true;
}
