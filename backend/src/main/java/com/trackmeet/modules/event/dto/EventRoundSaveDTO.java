package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 轮次新增 / 修改入参。
 * <p>
 * {@code roundCode} 在项目内唯一（{@code uk_round_event_code}）；
 * 新增时服务层自动维护 {@code roundNo} 顺序与 {@code isFinal} 标记。
 *
 * @author TrackMeet
 */
@Data
public class EventRoundSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属项目 ID（新增走 {@code POST /api/events/{id}/rounds} 时可空，由路径补全） */
    private Long eventId;

    /** 轮次码：QUALIFY 及格赛 / PRE 预赛 / R1 次赛 / R2 复赛 / SEMI 半决赛 / FINAL 决赛 */
    @NotBlank(message = "轮次码不能为空")
    @Pattern(regexp = "^(QUALIFY|PRE|R1|R2|SEMI|FINAL)$",
            message = "轮次码只能是 QUALIFY/PRE/R1/R2/SEMI/FINAL")
    private String roundCode;

    /** 轮次名称（如「预赛」「决赛」） */
    @Size(max = 100, message = "轮次名称长度不能超过 100")
    private String roundName;

    /** 轮次顺序号，从 1 起；未传由服务层按项目内顺序自动维护 */
    private Integer roundNo;

    /** 是否决赛轮：0 否 / 1 是；未传由服务层按轮次码自动判定 */
    private Integer isFinal;

    /** 计划分组数 */
    private Integer planHeatCount;

    /** 每组计划人数 */
    private Integer planHeatSize;

    /** 计划晋级总人数 */
    private Integer planAdvanceCount;

    /** 计划每组晋级人数 */
    private Integer planAdvanceGroupTop;

    /** 计划递补人数（剩余最好成绩） */
    private Integer planBestLoserCount;

    /** 本轮实际参与人数（快照） */
    private Integer entryCount;

    /** 计划比赛日期 */
    private LocalDate roundDate;

    /** 状态：PLANNED 已计划 / READY 就绪 / RUNNING 进行中 / FINISHED 已完成 */
    @Pattern(regexp = "^(PLANNED|READY|RUNNING|FINISHED)?$",
            message = "轮次状态只能是 PLANNED/READY/RUNNING/FINISHED")
    private String status;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
