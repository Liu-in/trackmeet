package com.trackmeet.modules.result.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 名次生成结果视图：回写名次后的完整名次表 + 并列说明。
 * <p>
 * {@link #ranks} 为已写入 {@code result.rank_no/rank_display} 的名次表（有效成绩在前、无效成绩在后）；
 * {@link #tieGroups} 按「并列名次」分组说明，便于前端高亮与裁判核对：
 * 每项为一条并列说明（如「第 1 名并列：张三、李四」）。
 *
 * @author TrackMeet
 */
@Data
public class RankGenerateResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private Long eventId;

    /** 项目名称 */
    private String eventName;

    /** 轮次 ID */
    private Long roundId;

    /** 轮次名称 */
    private String roundName;

    /** 排序方向：true 升序（径赛）、false 降序（田赛） */
    private Boolean trackAscending;

    /** 参与排名的成绩总数 */
    private Integer totalCount;

    /** 有效成绩数（写入名次的条数） */
    private Integer rankedCount;

    /** 无效成绩数（DNS/DNF/DSQ/NM/PASS/SCRATCH，不参与名次） */
    private Integer invalidCount;

    /** 是否存在并列名次：true 有 / false 无 */
    private Boolean hasTie;

    /** 名次表（含并列说明所需的展示字段） */
    private List<RankRowVO> ranks = new ArrayList<>();

    /** 并列说明列表，如「第 1 名并列：张三、李四」 */
    private List<String> tieGroups = new ArrayList<>();

    /** 处理说明（如「3 条无效成绩未参与名次」） */
    private List<String> remarks = new ArrayList<>();
}
