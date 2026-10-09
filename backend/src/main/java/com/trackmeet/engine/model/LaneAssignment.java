package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 道次分配结果。
 *
 * @author TrackMeet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaneAssignment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 道次号（1 起） */
    private int laneNo;

    /** 报名记录 ID */
    private Long registrationId;

    /** 运动员 ID */
    private Long athleteId;

    /** 分配方式：SEED / DRAW / SERPENT / ORDER */
    private String assignMode;
}
