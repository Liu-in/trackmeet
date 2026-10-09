package com.trackmeet.modules.standing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 榜单快照保存入参（{@code POST /api/standings/snapshot}）。
 * <p>
 * 快照不重算分值，只把当前 {@code team_score} / {@code medal_standing} 行的
 * {@code calc_time} 刷新为当前时间并写入统一的 {@code calc_batch} 批次号，
 * 用于「某时刻对外发布的榜单版本」留痕。
 *
 * @author TrackMeet
 */
@Data
public class StandingSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填，须与 MeetContext 一致） */
    @NotNull(message = "届次不能为空")
    private Long meetId;

    /** 团体口径：MALE / FEMALE / TOTAL，空表示三档全部快照 */
    private String scoreType;
}
