package com.trackmeet.engine.model;

/**
 * 田赛出场顺序生成方式。
 *
 * @author TrackMeet
 */
public enum FieldOrderMode {

    /** 随机（抽签） */
    RANDOM,
    /** 按报名成绩（种子成绩） */
    SEED,
    /** 按单位顺序 */
    UNIT,
    /** 按号码顺序 */
    BIB
}
