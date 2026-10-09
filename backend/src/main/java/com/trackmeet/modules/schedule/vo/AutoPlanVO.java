package com.trackmeet.modules.schedule.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动编排结果视图对象：预览与落库共用同一结构。
 * apply=false 时仅返回预览（不含落库主键）；apply=true 时回填 sessionId/scheduleId。
 *
 * @author TrackMeet
 */
@Data
public class AutoPlanVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否为落库执行（false 为纯预览） */
    private Boolean applied;
    private Long meetId;
    /** 编排范围：ALL / SESSION / GROUP / EVENT */
    private String scope;
    /** 本次编排批次号（apply=true 时生成） */
    private String batchNo;
    /** 已排入条数 */
    private Integer scheduledCount;
    /** 未能排入条数 */
    private Integer unscheduledCount;
    /** 日程草案条目 */
    private List<Item> items = new ArrayList<>();
    /** 计划阶段检测出的冲突 */
    private List<ConflictVO> conflicts = new ArrayList<>();
    /** 场地/时长不足提示 */
    private List<String> shortageMessages = new ArrayList<>();
    /** 汇总 */
    private Summary summary = new Summary();

    /**
     * 日程草案条目。
     */
    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long eventId;
        private Long roundId;
        private String eventName;
        private String roundCode;
        private Long sessionId;
        private String sessionName;
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
        private Long venueId;
        private String venueName;
        private Integer heatCount;
        private Boolean track;
        private Integer participantCount;
        /** 落库后的日程条目 ID（预览时若已存在同名日程则为该行 ID，否则为空） */
        private Long scheduleId;
        private LocalDateTime planStartTime;
        private LocalDateTime planEndTime;
    }

    /**
     * 编排汇总信息。
     */
    @Data
    public static class Summary implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 参与编排的单元数 */
        private Integer sessionCount;
        /** 草案条目数 */
        private Integer itemCount;
        /** 已排入条数 */
        private Integer scheduledCount;
        /** 未能排入条数 */
        private Integer unscheduledCount;
        /** 冲突总条数 */
        private Integer conflictCount;
        /** 阻断级冲突条数 */
        private Integer errorCount;
        /** 提醒级冲突条数 */
        private Integer warnCount;
        /** 提示信息条数 */
        private Integer shortageCount;
    }
}
