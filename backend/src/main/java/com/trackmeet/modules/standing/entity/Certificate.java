package com.trackmeet.modules.standing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trackmeet.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * certificate 证书表：生成的获奖/成绩/奖牌/参赛证书，含编号、收件人、成绩描述、文件与二维码。
 * <p>
 * 字段与 {@code sql/04-schema-result-standing.sql} 的 {@code certificate} 表逐列一致（通用字段继承自
 * {@link BaseEntity}）：
 * <pre>
 * id, meet_id, cert_no, cert_type, template_id, athlete_id, unit_id, event_id, round_id,
 * result_id, rank_no, rank_display, result_text, honor_text, issue_date, issuer, file_id,
 * qr_content, status, print_count, last_print_time, remark,
 * create_by, create_time, update_by, update_time, deleted, version
 * </pre>
 * <p>
 * 唯一键 {@code uk_cert_no (cert_no)} 为全局唯一（<b>不含</b> {@code deleted}），因此证书编号生成采用
 * 「前缀 + 库内既有同前缀条数递推」策略保证唯一（见 {@code StandingMapper#countCertNoByPrefix}）。
 *
 * @author TrackMeet
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("certificate")
public class Certificate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 证书类型：获奖证书 */
    public static final String TYPE_RANK = "RANK";
    /** 证书类型：成绩证书 */
    public static final String TYPE_RESULT = "RESULT";
    /** 证书类型：奖牌证书 */
    public static final String TYPE_MEDAL = "MEDAL";
    /** 证书类型：参赛证书 */
    public static final String TYPE_PARTICIPATION = "PARTICIPATION";

    /** 证书状态：已生成 */
    public static final String STATUS_GENERATED = "GENERATED";
    /** 证书状态：已签发 */
    public static final String STATUS_ISSUED = "ISSUED";
    /** 证书状态：已作废 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 届次 ID（业务表届次隔离列，必填） */
    @TableField("meet_id")
    private Long meetId;

    /** 证书编号，全局唯一 */
    @TableField("cert_no")
    private String certNo;

    /** 证书类型：RANK 获奖 / RESULT 成绩 / MEDAL 奖牌 / PARTICIPATION 参赛 */
    @TableField("cert_type")
    private String certType;

    /** 打印模板 ID，关联 print_template.id */
    @TableField("template_id")
    private Long templateId;

    /** 收件运动员 ID，关联 athlete.id */
    @TableField("athlete_id")
    private Long athleteId;

    /** 所属单位 ID，关联 unit.id */
    @TableField("unit_id")
    private Long unitId;

    /** 项目 ID，关联 event.id */
    @TableField("event_id")
    private Long eventId;

    /** 轮次 ID，关联 event_round.id */
    @TableField("round_id")
    private Long roundId;

    /** 来源成绩 ID，关联 result.id */
    @TableField("result_id")
    private Long resultId;

    /** 名次 */
    @TableField("rank_no")
    private Integer rankNo;

    /** 名次显示文本 */
    @TableField("rank_display")
    private String rankDisplay;

    /** 成绩展示文本，如 11"23 */
    @TableField("result_text")
    private String resultText;

    /** 荣誉描述，如「男子甲组 100 米第一名」 */
    @TableField("honor_text")
    private String honorText;

    /** 签发日期 */
    @TableField("issue_date")
    private LocalDate issueDate;

    /** 签发单位名称 */
    @TableField("issuer")
    private String issuer;

    /** 证书文件 ID，关联 sys_file.id */
    @TableField("file_id")
    private Long fileId;

    /** 二维码内容（验真链接或编号） */
    @TableField("qr_content")
    private String qrContent;

    /** 证书状态：GENERATED 已生成 / ISSUED 已签发 / REVOKED 已作废 */
    @TableField("status")
    private String status;

    /** 打印次数 */
    @TableField("print_count")
    private Integer printCount;

    /** 最近打印时间 */
    @TableField("last_print_time")
    private LocalDateTime lastPrintTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
