package com.trackmeet.modules.print.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印渲染支撑工具：HTML 纯文本化、列宽估算与模板列宽覆盖。
 * <p>
 * <b>列宽口径</b>：与 {@code PdfTableRenderer} 一致——数组元素是<b>相对权重</b>（由
 * {@code PdfPTable#setWidths} 内部归一化），因此既可以直接使用
 * {@code extra_json} 中配置的 {@code [2,1,1,1]}，也可以使用百分数（{@code [40,20,20,20]}）。
 * 未配置时按「各列最长文本显示宽度」占比分配：中日韩字符与全角标点按 2 计，其余按 1 计，
 * 单列权重限制在 {@value #MIN_COLUMN_WEIGHT}~{@value #MAX_COLUMN_WEIGHT} 之间，
 * 避免超长备注列把其他列挤成一条竖线。
 * <p>
 * <b>HTML 口径</b>：模板的 {@code header_html / footer_html / signature_html} <b>不做 HTML 渲染</b>，
 * 只把块级结束标签（{@code </p> </div> </li> </tr> <br>}）视作换行、去标签、解常见实体、折叠空白，
 * 得到纯文本行后写入页眉 / 页脚 / 签名区。
 *
 * @author TrackMeet
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrintRenderSupport {

    /** 单列最小权重 */
    public static final float MIN_COLUMN_WEIGHT = 2F;

    /** 单列最大权重（防止超长文本独占整行） */
    public static final float MAX_COLUMN_WEIGHT = 30F;

    /** 页眉纯文本最多保留行数 */
    public static final int MAX_HEADER_LINES = 4;

    /** 全局列宽通配键（{@code extra_json.columnWidths} 顶层数组写法） */
    private static final String WILDCARD_KEY = "*";

    /** 块级标签（转换为换行，避免多行页眉被拼成一行） */
    private static final String BLOCK_TAG_PATTERN =
            "(?i)</(p|div|li|tr|h1|h2|h3|h4|h5|h6|section|article|header|footer)>|<br\\s*/?>";

    /** 其余标签 */
    private static final String TAG_PATTERN = "<[^>]*>";

    /** 连续空白（含全角空格） */
    private static final String BLANK_PATTERN = "[\\s\\u3000]+";

    private final ObjectMapper objectMapper;

    // ==================================================================================
    // 一、HTML → 纯文本
    // ==================================================================================

    /**
     * HTML 去标签取纯文本：块级结束标签与 {@code <br>} 视作换行，解常见实体，折叠行内空白。
     *
     * @param html HTML 片段（可为 null）
     * @return 纯文本（多行以 {@code \n} 分隔），无有效内容时返回空串
     */
    public String stripHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = html.replaceAll(BLOCK_TAG_PATTERN, "\n").replaceAll(TAG_PATTERN, " ");
        text = decodeEntities(text);
        StringBuilder builder = new StringBuilder(text.length());
        for (String line : text.split("\n")) {
            String normalized = line.replaceAll(BLANK_PATTERN, " ").trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(normalized);
        }
        return builder.toString();
    }

    /**
     * HTML → 纯文本行列表（用于页眉多行渲染）。
     *
     * @param html     HTML 片段
     * @param maxLines 最多保留行数（&lt;=0 表示不限制）
     * @return 非空文本行
     */
    public List<String> htmlLines(String html, int maxLines) {
        String text = stripHtml(html);
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            lines.add(line);
            if (maxLines > 0 && lines.size() >= maxLines) {
                break;
            }
        }
        return lines;
    }

    /**
     * 把签名区纯文本拆成若干槽位：以 {@code |} 或 {@code ｜} 分隔（如「记录员：___ | 裁判长：___」）；
     * 段数不足时从左往右填充，超出时多出的段落合并进最后一个槽位。
     *
     * @param text  纯文本（可含 {@code |} 分隔符）
     * @param slots 槽位数量（通常为 3）
     * @return 长度等于 {@code slots} 的数组，缺失槽位为 {@code null}
     */
    public String[] splitSignature(String text, int slots) {
        int size = Math.max(0, slots);
        String[] result = new String[size];
        if (size == 0 || !StringUtils.hasText(text)) {
            return result;
        }
        List<String> values = new ArrayList<>(size);
        for (String part : stripHtml(text).split("[|｜]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        if (values.isEmpty()) {
            return result;
        }
        if (values.size() <= size) {
            for (int index = 0; index < values.size(); index++) {
                result[index] = values.get(index);
            }
            return result;
        }
        // 段数超过槽位：前面的依次填入，剩余段落合并到最后一个槽位
        for (int index = 0; index < size - 1; index++) {
            result[index] = values.get(index);
        }
        StringBuilder tail = new StringBuilder();
        for (int index = size - 1; index < values.size(); index++) {
            if (tail.length() > 0) {
                tail.append(' ');
            }
            tail.append(values.get(index));
        }
        result[size - 1] = tail.toString();
        return result;
    }

    // ==================================================================================
    // 二、列宽
    // ==================================================================================

    /**
     * 解析模板列宽覆盖：{@code extra_json} 形如
     * {@code {"sections":{"分组表":[2,1,1,1],"heat:12:3":[3,1,1]}}}。
     * <p>
     * 匹配顺序：先按 {@code keys} 逐项精确匹配（sheetKey → sheetName → 报表名），
     * 再退化为「键包含于 sheetName」的模糊匹配（如「分组表」命中「男子甲组 100米 预赛 分组表」）。
     * 解析失败、长度与列数不符时一律返回 {@code null}（回落自适应列宽）。
     *
     * @param extraJson   模板扩展 JSON（可为 null）
     * @param keys        候选键（顺序即优先级）
     * @param columnCount 实际列数
     * @return 列宽权重数组或 {@code null}
     */
    public float[] overrideWidths(String extraJson, List<String> keys, int columnCount) {
        if (!StringUtils.hasText(extraJson) || columnCount <= 0 || keys == null || keys.isEmpty()) {
            return null;
        }
        Map<String, float[]> sections = parseSections(extraJson);
        if (sections.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            float[] widths = key == null ? null : sections.get(key);
            if (widths != null && widths.length == columnCount) {
                return widths;
            }
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            for (Map.Entry<String, float[]> entry : sections.entrySet()) {
                if (!entry.getKey().isEmpty() && key.contains(entry.getKey())
                        && entry.getValue().length == columnCount) {
                    return entry.getValue();
                }
            }
        }
        // 全局默认列宽（{"columnWidths":[...]}）
        float[] wildcard = sections.get(WILDCARD_KEY);
        if (wildcard != null && wildcard.length == columnCount) {
            return wildcard;
        }
        return null;
    }

    /**
     * 列宽自适应：按各列最长文本显示宽度分配相对权重。
     *
     * @param headers 表头
     * @param rows    数据行
     * @return 列宽权重数组（列数为 0 时返回空数组）
     */
    public float[] adaptiveWidths(List<String> headers, List<List<String>> rows) {
        int columns = headers == null ? 0 : headers.size();
        if (columns <= 0 && rows != null && !rows.isEmpty() && rows.get(0) != null) {
            columns = rows.get(0).size();
        }
        if (columns <= 0) {
            return new float[0];
        }
        float[] widths = new float[columns];
        for (int column = 0; column < columns; column++) {
            float max = headers != null && column < headers.size() ? textWidth(headers.get(column)) : 1F;
            if (rows != null) {
                for (List<String> row : rows) {
                    if (row != null && column < row.size()) {
                        max = Math.max(max, textWidth(row.get(column)));
                    }
                }
            }
            widths[column] = Math.min(MAX_COLUMN_WEIGHT, Math.max(MIN_COLUMN_WEIGHT, max));
        }
        return widths;
    }

    /**
     * 生效列宽：模板覆盖优先，否则自适应。
     *
     * @param extraJson 模板扩展 JSON
     * @param keys      候选键（sheetKey / sheetName / 报表名）
     * @param headers   表头
     * @param rows      数据行
     * @return 列宽权重数组
     */
    public float[] resolveWidths(String extraJson, List<String> keys, List<String> headers, List<List<String>> rows) {
        int columns = headers == null ? 0 : headers.size();
        float[] override = overrideWidths(extraJson, keys, columns);
        if (override != null) {
            return override;
        }
        return adaptiveWidths(headers, rows);
    }

    /**
     * 文本显示宽度：中日韩字符与全角标点按 2 计，其余按 1 计。
     *
     * @param text 文本
     * @return 显示宽度
     */
    public float textWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0F;
        }
        float width = 0F;
        for (int index = 0; index < text.length(); index++) {
            width += isWide(text.charAt(index)) ? 2F : 1F;
        }
        return width;
    }

    // ==================================================================================
    // 三、内部工具
    // ==================================================================================

    /**
     * 解析模板 {@code extra_json} 中的列宽配置为「键 → 列宽权重」映射（任何异常都降级为空映射）。
     * <p>
     * 支持三种写法（与任务书 / 模块文档口径一致）：
     * <ol>
     *     <li>{@code {"sections":{"分组表":[2,1,1,1]}}}（推荐，按节精确指定）；</li>
     *     <li>{@code {"columnWidths":{"分组表":[2,1,1,1]}}}（等价别名）；</li>
     *     <li>{@code {"columnWidths":[2,1,1,1]}}（全局默认列宽，对所有列数匹配的节生效）。</li>
     * </ol>
     */
    private Map<String, float[]> parseSections(String extraJson) {
        Map<String, float[]> sections = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(extraJson);
            if (root == null) {
                return sections;
            }
            JsonNode node = root.get("sections");
            if (node == null || !node.isObject()) {
                node = root.get("columnWidths");
            }
            if (node != null && node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    float[] widths = toWidths(entry.getValue());
                    if (widths != null) {
                        sections.put(entry.getKey(), widths);
                    }
                }
            }
            // 顶层数组写法：作为全局默认列宽（通配键）
            JsonNode global = root.get("columnWidths");
            float[] globalWidths = toWidths(global);
            if (globalWidths != null) {
                sections.put(WILDCARD_KEY, globalWidths);
            }
        } catch (Exception ex) {
            log.debug("解析打印模板 extra_json 列宽失败，回落自适应列宽：{}", ex.getMessage());
            return Collections.emptyMap();
        }
        return sections;
    }

    /**
     * JSON 数组 → 列宽权重（非数组、空数组或存在非正数时返回 {@code null}）。
     */
    private float[] toWidths(JsonNode value) {
        if (value == null || !value.isArray() || value.isEmpty()) {
            return null;
        }
        float[] widths = new float[value.size()];
        for (int index = 0; index < value.size(); index++) {
            float width = (float) value.get(index).asDouble(0D);
            if (width <= 0F) {
                return null;
            }
            widths[index] = width;
        }
        return widths;
    }

    /**
     * 解常见 HTML 实体（含数字实体）。
     */
    private String decodeEntities(String text) {
        String decoded = text.replace("&nbsp;", " ")
                .replace("&ensp;", " ")
                .replace("&emsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&#160;", " ");
        return decoded;
    }

    /**
     * 是否全角 / 宽字符（与 PDF 渲染器口径一致）。
     */
    private boolean isWide(char character) {
        return character >= 0x1100 && (character <= 0x115F
                || (character >= 0x2E80 && character <= 0xA4CF)
                || (character >= 0xAC00 && character <= 0xD7A3)
                || (character >= 0xF900 && character <= 0xFAFF)
                || (character >= 0xFE30 && character <= 0xFE6F)
                || (character >= 0xFF00 && character <= 0xFF60)
                || (character >= 0xFFE0 && character <= 0xFFE6));
    }
}
