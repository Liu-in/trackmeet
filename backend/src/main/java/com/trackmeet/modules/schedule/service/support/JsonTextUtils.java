package com.trackmeet.modules.schedule.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON 文本读写工具：单元可用场地列表（{@code venue_ids_json}）、道次顺序
 * （{@code lane_order_json}）、快照 JSON 等列均以原始文本存取，此处统一容错解析。
 *
 * @author TrackMeet
 */
public final class JsonTextUtils {

    private JsonTextUtils() {
    }

    /**
     * 解析 Long 数组文本，如 {@code [1,2,3]}。
     * <p>
     * 容错：空文本、非数组、数组内非数字元素一律跳过（不抛异常），保证脏数据不阻断编排。
     *
     * @param mapper Jackson 映射器
     * @param text   JSON 文本
     * @return 去重后的 ID 列表（保持原顺序）
     */
    public static List<Long> parseLongArray(ObjectMapper mapper, String text) {
        List<Long> values = new ArrayList<>();
        if (!StringUtils.hasText(text) || mapper == null) {
            return values;
        }
        try {
            JsonNode node = mapper.readTree(text);
            if (node == null || !node.isArray()) {
                return values;
            }
            Set<Long> distinct = new LinkedHashSet<>();
            for (JsonNode element : node) {
                if (element != null && element.canConvertToLong()) {
                    distinct.add(element.asLong());
                }
            }
            values.addAll(distinct);
        } catch (Exception ignored) {
            // 脏数据按「未配置」处理，由调用方回退默认值
        }
        return values;
    }

    /**
     * 解析 Integer 数组文本，如 {@code [4,5,3,6,2,7,1,8]}（道次优先顺序）。
     *
     * @param mapper Jackson 映射器
     * @param text   JSON 文本
     * @return 去重后的道次顺序（保持原顺序）
     */
    public static List<Integer> parseIntArray(ObjectMapper mapper, String text) {
        List<Integer> values = new ArrayList<>();
        if (!StringUtils.hasText(text) || mapper == null) {
            return values;
        }
        try {
            JsonNode node = mapper.readTree(text);
            if (node == null || !node.isArray()) {
                return values;
            }
            Set<Integer> distinct = new LinkedHashSet<>();
            for (JsonNode element : node) {
                if (element != null && element.canConvertToInt()) {
                    distinct.add(element.asInt());
                }
            }
            values.addAll(distinct);
        } catch (Exception ignored) {
            // 脏数据按「未配置」处理，由调用方回退默认道次顺序
        }
        return values;
    }

    /**
     * Long 列表序列化为 JSON 数组文本；空列表返回 {@code null}（列可为空，不写空数组占位）。
     *
     * @param mapper Jackson 映射器
     * @param values 值列表
     * @return JSON 文本或 null
     */
    public static String writeLongArray(ObjectMapper mapper, List<Long> values) {
        if (mapper == null || values == null || values.isEmpty()) {
            return null;
        }
        ArrayNode array = mapper.createArrayNode();
        for (Long value : values) {
            if (value != null) {
                array.add(value);
            }
        }
        return array.isEmpty() ? null : array.toString();
    }

    /**
     * 序列化任意对象为 JSON 文本（失败返回 null，由调用方决定是否降级）。
     *
     * @param mapper Jackson 映射器
     * @param value  对象
     * @return JSON 文本或 null
     */
    public static String writeValue(ObjectMapper mapper, Object value) {
        if (mapper == null || value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
