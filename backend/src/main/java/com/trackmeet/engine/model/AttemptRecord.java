package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 田赛单次试跳/试掷记录（服务层落库用；算法侧只需要成绩值）。
 *
 * @author TrackMeet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 第几次（从 1 开始） */
    private int attemptNo;

    /** 试跳/试掷成绩（米） */
    private BigDecimal value;

    /** 是否成功 */
    private boolean success;

    /** 是否免跳（PASS） */
    private boolean pass;

    /** 是否犯规（X） */
    private boolean foul;

    /** 高度项横杆高度（米） */
    private BigDecimal height;
}
