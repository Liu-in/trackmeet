package com.trackmeet.modules.schedule.service.support;

import com.trackmeet.engine.model.FieldOrderMode;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分组分道草案（引擎结果的内存载体）。
 * <p>
 * 与 {@link com.trackmeet.modules.schedule.vo.HeatGenerateResultVO} 的区别：
 * VO 面向前端（含展示字段与最终主键），草案面向落库（含成员明细与待分配道次），
 * 自动编排与手工「生成分组分道」共用同一草案构建过程，保证两处组数口径一致。
 *
 * @author TrackMeet
 */
@Data
public class GroupDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日程条目 ID */
    private Long scheduleId;

    /** 项目 ID */
    private Long eventId;

    /** 是否径赛（径赛才分配道次） */
    private boolean track;

    /** 分组方式：SERPENT / SEED / RANDOM / UNIT */
    private String drawMode;

    /** 道次分配方式：SEED / DRAW / ORDER / SERPENT（为空表示不分配道次） */
    private String laneMode;

    /** 有效道数 */
    private int laneCount;

    /** 本次生成使用的道次优先顺序 */
    private List<Integer> laneOrder = new ArrayList<>();

    /** 参赛人数（未锁定成员，即本次参与分组的报名条目数） */
    private int participantCount;

    /** 组数（含保留的锁定分组） */
    private int heatCount;

    /** 被锁定而保留的既有组数 */
    private int lockedHeatCount;

    /** 分组草案明细 */
    private List<HeatDraft> heats = new ArrayList<>();

    /**
     * 单个分组草案。
     */
    @Data
    public static class HeatDraft implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 既有分组 ID（锁定保留的组才有值） */
        private Long existingHeatId;

        /** 组号（从 1 开始） */
        private int heatNo;

        /** 是否锁定保留（不重建成员与道次） */
        private boolean locked;

        /** 本组可用道数/出场位数 */
        private Integer laneCount;

        /** 本组种子选手数 */
        private int seedCount;

        /** 组内成员（顺序即出发顺序） */
        private List<MemberDraft> members = new ArrayList<>();
    }

    /**
     * 组内成员草案。
     */
    @Data
    public static class MemberDraft implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 报名记录 ID */
        private Long registrationId;

        /** 运动员 ID */
        private Long athleteId;

        /** 参赛单位 ID */
        private Long unitId;

        /** 号码布号 */
        private String bibNo;

        /** 组别 ID */
        private Long groupId;

        /** 报名最好成绩数值 */
        private java.math.BigDecimal seedResult;

        /** 报名成绩原文 */
        private String seedResultRaw;

        /** 成绩单位 */
        private String seedResultUnit;

        /** 是否种子选手：0 否 / 1 是 */
        private int isSeed;
    }

    /**
     * 田赛出场顺序模式：由分组方式映射（RANDOM→RANDOM / SEED→SEED / UNIT→UNIT / 其余→BIB）。
     *
     * @return 田赛出场顺序模式
     */
    public FieldOrderMode fieldOrderMode() {
        if (drawMode == null) {
            return FieldOrderMode.RANDOM;
        }
        switch (drawMode.trim().toUpperCase()) {
            case "SEED":
                return FieldOrderMode.SEED;
            case "UNIT":
                return FieldOrderMode.UNIT;
            case "RANDOM":
                return FieldOrderMode.RANDOM;
            default:
                return FieldOrderMode.BIB;
        }
    }
}
