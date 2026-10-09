package com.trackmeet.modules.standing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 证书批量生成入参。
 * <p>
 * {@code certType} 决定取数范围：
 * <ul>
 *     <li>{@code RANK} 获奖证书：取已审核成绩中有效名次且名次 ≤ 录取名次（默认 8）的记录；</li>
 *     <li>{@code MEDAL} 奖牌证书：取已审核成绩中有效名次且名次 ∈ {1,2,3} 的记录；</li>
 *     <li>{@code RESULT} 成绩证书：取所有已审核且成绩有效的记录；</li>
 *     <li>{@code PARTICIPATION} 参赛证书：取已通过报名的报名记录（无需成绩）。</li>
 * </ul>
 *
 * @author TrackMeet
 */
@Data
public class CertificateGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 届次 ID（必填，须与 MeetContext 一致） */
    @NotNull(message = "届次不能为空")
    private Long meetId;

    /** 证书类型：RANK / RESULT / MEDAL / PARTICIPATION */
    private String certType;

    /** 项目 ID（可空，空为全部项目） */
    private Long eventId;

    /** 组别 ID（可空，空为全部组别） */
    private Long groupId;

    /** 参赛单位 ID（可空，空为全部单位） */
    private Long unitId;

    /** 打印模板 ID（可空，空表示使用报表类型默认模板） */
    private Long templateId;

    /**
     * 规范化证书类型：空值默认 {@code RANK}，非法值同样回落 {@code RANK}。
     *
     * @return {@code RANK} / {@code RESULT} / {@code MEDAL} / {@code PARTICIPATION}
     */
    public String normalizedCertType() {
        if (certType == null || certType.isBlank()) {
            return "RANK";
        }
        String upper = certType.trim().toUpperCase();
        return switch (upper) {
            case "RESULT", "MEDAL", "PARTICIPATION" -> upper;
            default -> "RANK";
        };
    }
}
