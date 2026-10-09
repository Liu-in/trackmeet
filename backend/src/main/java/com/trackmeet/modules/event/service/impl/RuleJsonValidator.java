package com.trackmeet.modules.event.service.impl;

import com.trackmeet.common.exception.BizException;
import com.trackmeet.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 规则模块内部 JSON 文本校验工具。
 * <p>
 * 规则表中的 JSON 列以原始文本接收 / 返回（见实体注释），写库前统一用
 * {@link ObjectMapper} 解析校验，非法 JSON 直接抛 {@code PARAM_ERROR}，避免脏数据写入。
 * <p>
 * 包内可见的静态工具，不注册为 Bean；{@link ObjectMapper} 线程安全，静态共享即可。
 *
 * @author TrackMeet
 */
final class RuleJsonValidator {

    /** JSON 解析器（线程安全） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RuleJsonValidator() {
    }

    /**
     * 校验 JSON 文本合法性；空值时直接通过（空值语义为「继承上一层」）。
     *
     * @param json   JSON 文本，允许为 {@code null} 或空白
     * @param field  字段中文名，用于错误提示
     */
    static void validate(String json, String field) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, field + "不是合法的 JSON 文本");
        }
    }

    /**
     * 判断 JSON 文本是否为合法 JSON。
     *
     * @param json JSON 文本
     * @return 合法返回 true，空值或非法返回 false
     */
    static boolean isValid(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
