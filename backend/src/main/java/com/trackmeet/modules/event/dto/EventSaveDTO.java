package com.trackmeet.modules.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 项目新增 / 修改入参。
 * <p>
 * 与 {@code event} 表业务列一一对应；{@code meetId} 未传时取当前届次上下文（请求头 X-Meet-Id）。
 * 新增时若未传 {@code status} 与 {@code roundCount}，服务层按 DDL 默认值补全为
 * {@code ENABLED} 与 {@code 1}。
 *
 * @author TrackMeet
 */
@Data
public class EventSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID，未传取当前届次上下文 */
    private Long meetId;

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过 100")
    private String eventName;

    /** 项目编码，届次内唯一 */
    @NotBlank(message = "项目编码不能为空")
    @Size(max = 64, message = "项目编码长度不能超过 64")
    private String eventCode;

    /** 项目类型：TRACK 径赛 / FIELD 田赛 / ALLROUND 全能 / RELAY 接力 */
    @NotBlank(message = "项目类型不能为空")
    @Pattern(regexp = "^(TRACK|FIELD|ALLROUND|RELAY)$", message = "项目类型只能是 TRACK/FIELD/ALLROUND/RELAY")
    private String eventType;

    /** 项目子类型：SPRINT/MIDDLE/LONG/HURDLE/STEEPLE/WALK/JUMP/THROW/RELAY */
    @Pattern(regexp = "^(SPRINT|MIDDLE|LONG|HURDLE|STEEPLE|WALK|JUMP|THROW|RELAY)?$",
            message = "项目子类型取值不正确")
    private String subType;

    /** 所属组别 ID，关联 meet_group.id */
    private Long groupId;

    /** 适用性别：MALE 男 / FEMALE 女 / MIXED 混合 */
    @Pattern(regexp = "^(MALE|FEMALE|MIXED)?$", message = "性别码只能是 MALE/FEMALE/MIXED")
    private String gender;

    /** 轮次数量，新增默认 1 */
    private Integer roundCount;

    /** 是否接力项目：0 否 / 1 是 */
    private Integer isRelay;

    /** 接力最少人数（默认 4） */
    private Integer relayMin;

    /** 接力最多人数（默认 4） */
    private Integer relayMax;

    /** 每单位每项限报人数（默认 3） */
    private Integer maxPerUnit;

    /** 接力得分是否双倍：0 否 / 1 是 */
    private Integer isRelayDouble;

    /** 是否记录并判定风速：0 否 / 1 是 */
    private Integer windApplicable;

    /** 是否计入团体总分：0 否 / 1 是 */
    private Integer isTeamScore;

    /** 项目预计时长（分钟） */
    private Integer durationMin;

    /** 采用名次分方案 ID，关联 score_rule.id */
    private Long scoreRuleId;

    /** 计量单位：s 秒 / m 米 / cm 厘米 / pt 分 */
    @Pattern(regexp = "^(s|m|cm|pt)?$", message = "计量单位只能是 s/m/cm/pt")
    private String unitOfMeasure;

    /** 成绩小数位，覆盖 sys_config 精度配置 */
    private Integer resultPrecision;

    /** 显示排序号 */
    private Integer sortNo;

    /** 状态：ENABLED 启用 / DISABLED 停用 */
    @Pattern(regexp = "^(ENABLED|DISABLED)?$", message = "状态只能是 ENABLED/DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 100, message = "备注长度不能超过 100")
    private String remark;

    /** 新增项目时一并初始化的轮次定义（可为空） */
    private List<EventRoundSaveDTO> rounds;
}
