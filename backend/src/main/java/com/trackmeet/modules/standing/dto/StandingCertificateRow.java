package com.trackmeet.modules.standing.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证书投影行：{@code certificate} 表联 {@code athlete} / {@code unit} / {@code event} /
 * {@code meet_group} 的只读明细，用于证书列表与导出。
 *
 * @author TrackMeet
 */
@Data
public class StandingCertificateRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证书 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 证书编号 */
    private String certNo;

    /** 证书类型：RANK / RESULT / MEDAL / PARTICIPATION */
    private String certType;

    /** 打印模板 ID */
    private Long templateId;

    /** 收件运动员 ID */
    private Long athleteId;

    /** 所属单位 ID */
    private Long unitId;

    /** 项目 ID */
    private Long eventId;

    /** 轮次 ID */
    private Long roundId;

    /** 来源成绩 ID */
    private Long resultId;

    /** 名次 */
    private Integer rankNo;

    /** 名次显示文本 */
    private String rankDisplay;

    /** 成绩展示文本 */
    private String resultText;

    /** 荣誉描述 */
    private String honorText;

    /** 签发日期 */
    private LocalDate issueDate;

    /** 签发单位名称 */
    private String issuer;

    /** 证书文件 ID */
    private Long fileId;

    /** 二维码内容 */
    private String qrContent;

    /** 证书状态：GENERATED / ISSUED / REVOKED */
    private String status;

    /** 打印次数 */
    private Integer printCount;

    /** 最近打印时间 */
    private LocalDateTime lastPrintTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 运动员姓名 */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 轮次名称 */
    private String roundName;

    /** 组别名称 */
    private String groupName;
}
