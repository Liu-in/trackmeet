package com.trackmeet.modules.schedule.dto;

import com.trackmeet.common.base.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 冲突检测/冲突清单查询：
 * 检测时使用 meetId、sessionId、eventId；清单查询额外使用 conflictType、level、resolved、scheduleId。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConflictQuery extends BaseQuery {

    private static final long serialVersionUID = 1L;

    /** 竞赛单元 ID（检测范围） */
    private Long sessionId;

    /** 项目 ID（检测范围） */
    private Long eventId;

    /** 日程条目 ID */
    private Long scheduleId;

    /** 冲突类型：ATHLETE / VENUE / REFEREE / INTERVAL */
    private String conflictType;

    /** 冲突级别：ERROR / WARN */
    private String level;

    /** 是否已处理：0 未处理 / 1 已处理 */
    private Integer resolved;
}
