package com.trackmeet.modules.athlete.dto;

import com.trackmeet.modules.athlete.entity.RelayMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 报名保存请求（新增单条 / 修改 / 批量校验的明细元素）。
 * <p>
 * 字段名与前端契约严格一致：{@code seedUnit} 落库到 {@code registration.seed_result_unit}；
 * {@code members} 为接力队员数组，元素只需 {@code athleteId} 与 {@code legNo}。
 *
 * @author TrackMeet
 */
@Data
public class RegistrationSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（可空，空时取请求头 X-Meet-Id 或 meetId 参数） */
    private Long meetId;

    /** 报名项目 ID */
    @NotNull(message = "报名项目不能为空")
    private Long eventId;

    /** 报名单位 ID（可空，空时取运动员/首个队员所属单位） */
    private Long unitId;

    /** 运动员 ID（个人项目必填，接力项目可空） */
    private Long athleteId;

    /** 报名组别 ID（可空，空时取项目或运动员所属组别） */
    private Long groupId;

    /** 是否接力项目：0 否 / 1 是（可空，空时按项目类型判定） */
    private Integer isRelay;

    /** 接力队名 */
    @Size(max = 100, message = "接力队名长度不能超过 100")
    private String relayName;

    /** 同单位同项目接力队序号（可空，空时默认 1） */
    @Positive(message = "接力队序号必须为正整数")
    private Integer relayTeamSeq;

    /** 种子成绩原文，如 11"23、1:02.45 */
    @Size(max = 16, message = "种子成绩原文长度不能超过 16")
    private String seedResult;

    /** 种子成绩可比较数值（秒/米） */
    private BigDecimal seedValue;

    /** 种子成绩单位：s/m/cm/pt */
    @Size(max = 32, message = "种子成绩单位长度不能超过 32")
    private String seedUnit;

    /** 种子来源：PB/SB/LAST_MEET/CUSTOM（空时默认 MANUAL） */
    @Size(max = 32, message = "种子来源长度不能超过 32")
    private String seedSource;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;

    /** 接力队员（元素：athleteId + legNo；棒次按数组顺序从 1 归一化重排） */
    @Valid
    private List<RelayMember> members;
}
