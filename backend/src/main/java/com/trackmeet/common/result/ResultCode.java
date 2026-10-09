package com.trackmeet.common.result;

import lombok.Getter;

/**
 * 统一业务错误码。
 * 与《API 清单》第 1.2 节保持一致：code = 0 表示成功，其余为业务错误码。
 *
 * @author TrackMeet
 */
@Getter
public enum ResultCode {

    /** 成功 */
    SUCCESS(0, "success"),

    /** 参数校验失败 */
    PARAM_ERROR(400, "参数校验失败"),
    /** 未登录或令牌失效 */
    UNAUTHORIZED(401, "未登录或令牌失效"),
    /** 无权限（含数据范围越权） */
    FORBIDDEN(403, "无权限访问"),
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 数据冲突（唯一约束、状态不允许、并发修改） */
    CONFLICT(409, "数据冲突"),

    /** 用户名或密码错误 */
    LOGIN_FAILED(10001, "用户名或密码错误"),
    /** 账号被锁定/停用 */
    ACCOUNT_DISABLED(10002, "账号被锁定或停用"),
    /** 验证码错误 */
    CAPTCHA_ERROR(10003, "验证码错误"),
    /** 账号被锁定（连续输错） */
    ACCOUNT_LOCKED(10004, "账号已被锁定，请稍后重试"),

    /** 运动会状态不允许该操作 */
    MEET_STATUS_ERROR(20001, "运动会状态不允许该操作"),
    /** 报名已截止 */
    REG_CLOSED(20002, "报名已截止"),
    /** 超出报名限制（超项/超人数/组别不符） */
    REG_LIMIT_EXCEEDED(20003, "超出报名限制"),
    /** 重复报名 */
    REG_DUPLICATE(20004, "重复报名"),

    /** 规则配置缺失或不完整 */
    RULE_NOT_CONFIGURED(30001, "规则配置缺失或不完整"),
    /** 编排冲突未解决 */
    SCHEDULE_CONFLICT(30002, "编排冲突未解决"),

    /** 成绩已审核锁定，禁止直接修改 */
    RESULT_LOCKED(40001, "成绩已审核锁定，禁止直接修改"),
    /** 风速超限，纪录不予认定 */
    WIND_ILLEGAL(40002, "风速超限，纪录不予认定"),
    /** 晋级规则与报名人数不匹配 */
    ADVANCE_RULE_MISMATCH(40003, "晋级规则与报名人数不匹配"),

    /** 导入文件格式错误 */
    IMPORT_FORMAT_ERROR(50001, "导入文件格式错误"),
    /** 报表模板缺失 */
    TEMPLATE_MISSING(50002, "报表模板缺失"),
    /** 未找到可用的中文字体，无法生成 PDF */
    PDF_FONT_MISSING(50003, "未找到可嵌入的中文字体，无法生成 PDF"),

    /** 系统内部错误 */
    SYSTEM_ERROR(90000, "系统内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
