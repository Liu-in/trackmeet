package com.trackmeet.modules.standing.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证书 VO：{@code certificate} 一行的可读展开（含运动员、单位、项目、轮次与组别名）。
 *
 * @author TrackMeet
 */
@Data
public class CertificateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 证书 ID */
    private Long id;

    /** 届次 ID */
    private Long meetId;

    /** 证书编号 */
    private String certNo;

    /** 证书类型：RANK 获奖 / RESULT 成绩 / MEDAL 奖牌 / PARTICIPATION 参赛 */
    private String certType;

    /** 证书类型中文名 */
    private String certTypeName;

    /** 打印模板 ID */
    private Long templateId;

    /** 收件运动员 ID */
    private Long athleteId;

    /** 运动员姓名 */
    private String athleteName;

    /** 号码布编号 */
    private String bibNo;

    /** 所属单位 ID */
    private Long unitId;

    /** 单位名称 */
    private String unitName;

    /** 单位简称 */
    private String unitShortName;

    /** 项目 ID */
    private Long eventId;

    /** 项目名称 */
    private String eventName;

    /** 项目编码 */
    private String eventCode;

    /** 轮次 ID */
    private Long roundId;

    /** 轮次名称 */
    private String roundName;

    /** 组别名称 */
    private String groupName;

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
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    /** 签发单位名称 */
    private String issuer;

    /** 证书文件 ID */
    private Long fileId;

    /** 二维码内容（验真串） */
    private String qrContent;

    /** 证书状态：GENERATED / ISSUED / REVOKED */
    private String status;

    /** 打印次数 */
    private Integer printCount;

    /** 最近打印时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastPrintTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 备注 */
    private String remark;
}
