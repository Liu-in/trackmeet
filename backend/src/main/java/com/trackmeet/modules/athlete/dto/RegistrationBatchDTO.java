package com.trackmeet.modules.athlete.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量报名请求：一个单位一次提交一个项目的多名运动员。
 * <p>
 * 个人项目：{@code athleteIds} 中每名运动员生成一条报名；
 * 接力项目（{@code isRelay = 1}）：{@code athleteIds} 作为同一支接力队的队员，
 * 队序号自动取「该单位该项目已有最大序号 + 1」。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationBatchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（可空，空时取请求头 X-Meet-Id 或 meetId 参数） */
    private Long meetId;

    /** 报名单位 ID（可空，空时取首个运动员所属单位） */
    private Long unitId;

    /** 报名项目 ID */
    @NotNull(message = "报名项目不能为空")
    private Long eventId;

    /** 运动员 ID 数组（个人项目为多名运动员；接力项目为一支接力队的队员） */
    @NotEmpty(message = "运动员列表不能为空")
    private List<Long> athleteIds;

    /** 是否接力项目：0 否 / 1 是（可空，空时按项目类型判定） */
    private Integer isRelay;

    /** 接力队名（接力报名使用） */
    @Size(max = 100, message = "接力队名长度不能超过 100")
    private String relayName;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;
}
