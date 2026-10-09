package com.trackmeet.engine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分组结果：组号（从 1 开始）+ 组内成员（顺序即道次/出场优先级：靠前者占最优道次）。
 *
 * @author TrackMeet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 组号，从 1 开始 */
    private int heatNo;

    /** 组内成员（顺序敏感） */
    private List<AthleteEntry> members = new ArrayList<>();

    public int size() {
        return members == null ? 0 : members.size();
    }
}
