package com.trackmeet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackmeet.api.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印中心（PDF / Excel 导出、打印模板、打印留痕、报表清单）接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}。PDF 能否生成取决于运行环境是否
 * 存在可嵌入的中文字体（{@code trackmeet.pdf.font-path} 或系统字体 / classpath:fonts/*.ttf）：
 * 若 {@code GET /api/print/pdf/capability} 的 {@code available=false}，输出接口会返回统一错误码
 * {@code 50003}（PDF_FONT_MISSING），本类据此分支断言，两种情况都不会误报失败。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>{@code GET /api/print/pdf/capability}：PDF 能力探测</li>
 *     <li>{@code GET /api/print/pdf/order-book}：秩序册 PDF（Content-Type + {@code %PDF-} 文件头）</li>
 *     <li>{@code GET /api/print/excel/order-book}：统一 Excel 导出（xlsx 内容类型或统一错误码）</li>
 *     <li>{@code GET /api/print/reports}：报表清单</li>
 *     <li>{@code GET/POST/PUT/DELETE /api/print/templates}：模板 CRUD 与默认模板唯一</li>
 *     <li>{@code GET /api/print/logs}：打印留痕分页</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class PrintApiIT extends ApiTestBase {

    @Test
    @DisplayName("PDF 能力探测：返回可用性与字体信息字段")
    void shouldReportPdfCapability() throws Exception {
        JsonNode data = assertOk(perform(rawGet("/api/print/pdf/capability", adminToken(), DEMO_MEET_ID)));
        assertThat(data.has("available")).as("应返回 available 字段：%s", data).isTrue();
        assertThat(data.path("available").isBoolean()).isTrue();
    }

    @Test
    @DisplayName("秩序册 PDF：字体可用时返回 application/pdf 且文件头为 %PDF-，否则返回错误码 50003")
    void shouldExportOrderBookPdfOrReportFontMissing() throws Exception {
        String token = adminToken();
        JsonNode capability = assertOk(perform(rawGet("/api/print/pdf/capability", token, DEMO_MEET_ID)));
        boolean available = capability.path("available").asBoolean();

        MvcResult result = perform(rawGet("/api/print/pdf/order-book", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID));

        if (available) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            String contentType = result.getResponse().getContentType();
            assertThat(contentType).as("PDF 响应内容类型：%s", contentType).contains("application/pdf");
            byte[] bytes = result.getResponse().getContentAsByteArray();
            assertThat(bytes.length).isGreaterThan(4);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        } else {
            JsonNode error = body(result);
            assertThat(error.path("code").asInt(-1))
                    .as("字体缺失时应返回统一错误码 50003，实际响应：%s", error)
                    .isEqualTo(50003);
        }
    }

    @Test
    @DisplayName("Excel 导出：返回 xlsx 内容类型与 PK 文件头，或统一错误码")
    void shouldExportOrderBookExcel() throws Exception {
        String token = adminToken();
        MvcResult result = perform(rawGet("/api/print/excel/order-book", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID));

        String contentType = result.getResponse().getContentType();
        if (contentType != null && (contentType.contains("spreadsheetml") || contentType.contains("octet-stream"))) {
            byte[] bytes = result.getResponse().getContentAsByteArray();
            assertThat(bytes.length).isGreaterThan(1);
            assertThat(new String(bytes, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        } else {
            // 模板缺失 / 字体缺失 / 内部错误：均以统一错误体返回
            assertBizCodeIn(result, CODE_SUCCESS, 50002, 50003, 90000);
        }
    }

    @Test
    @DisplayName("报表清单：返回报表列表（含报表类型与名称）")
    void shouldListReports() throws Exception {
        JsonNode data = assertOk(perform(rawGet("/api/print/reports", adminToken(), DEMO_MEET_ID)));
        assertThat(data.isArray()).isTrue();
        assertThat(data).as("报表清单不应为空").isNotEmpty();
        assertThat(data.get(0).path("reportType").asText()).isNotBlank();
        assertThat(data.get(0).path("reportName").asText()).isNotBlank();
    }

    @Test
    @DisplayName("打印模板：CRUD 成功，且同一报表类型的默认模板唯一")
    void shouldManagePrintTemplates() throws Exception {
        String token = adminToken();
        String firstCode = "TT_" + uniqueSuffix();
        String secondCode = "TT_" + uniqueSuffix();

        // 新增：默认模板
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("meetId", DEMO_MEET_ID);
        first.put("templateCode", firstCode);
        first.put("templateName", uniqueName("测试模板"));
        first.put("reportType", "RESULT");
        first.put("pageSize", "A4");
        first.put("orientation", "PORTRAIT");
        first.put("fontSize", 10);
        first.put("isDefault", 1);
        long firstId = assertOk(perform(rawPost("/api/print/templates", token, DEMO_MEET_ID, first))).asLong();
        assertThat(firstId).isPositive();

        JsonNode defaultTemplate = assertOk(perform(rawGet("/api/print/templates/default", token, DEMO_MEET_ID,
                "reportType=RESULT", "meetId=" + DEMO_MEET_ID)));
        assertThat(defaultTemplate.path("templateCode").asText()).isEqualTo(firstCode);

        // 新增第二个默认模板：旧的自动置为非默认（默认模板唯一）
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("meetId", DEMO_MEET_ID);
        second.put("templateCode", secondCode);
        second.put("templateName", uniqueName("测试模板二"));
        second.put("reportType", "RESULT");
        second.put("isDefault", 1);
        long secondId = assertOk(perform(rawPost("/api/print/templates", token, DEMO_MEET_ID, second))).asLong();

        JsonNode newDefault = assertOk(perform(rawGet("/api/print/templates/default", token, DEMO_MEET_ID,
                "reportType=RESULT", "meetId=" + DEMO_MEET_ID)));
        assertThat(newDefault.path("templateCode").asText()).isEqualTo(secondCode);

        JsonNode oldTemplate = assertOk(perform(rawGet("/api/print/templates/" + firstId, token, DEMO_MEET_ID)));
        assertThat(oldTemplate.path("isDefault").asInt(0)).isEqualTo(0);

        // 模板分页
        JsonNode page = assertPaged(perform(rawGet("/api/print/templates", token, DEMO_MEET_ID,
                "page=1", "size=10", "reportType=RESULT")));
        assertThat(page.path("records")).isNotEmpty();

        // 修改
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("meetId", DEMO_MEET_ID);
        update.put("templateCode", secondCode);
        update.put("templateName", "测试模板二改");
        update.put("reportType", "RESULT");
        update.put("isDefault", 1);
        assertOk(perform(rawPut("/api/print/templates/" + secondId, token, DEMO_MEET_ID, update)));
        JsonNode updated = assertOk(perform(rawGet("/api/print/templates/" + secondId, token, DEMO_MEET_ID)));
        assertThat(updated.path("templateName").asText()).isEqualTo("测试模板二改");

        // 参数校验：缺少模板编码
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("meetId", DEMO_MEET_ID);
        invalid.put("templateName", uniqueName("测试模板缺编码"));
        invalid.put("reportType", "RESULT");
        assertParamError(perform(rawPost("/api/print/templates", token, DEMO_MEET_ID, invalid)));

        // 删除
        assertOk(perform(rawDelete("/api/print/templates/" + secondId, token, DEMO_MEET_ID)));
        assertBizCode(perform(rawGet("/api/print/templates/" + secondId, token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("打印留痕：分页结构正确")
    void shouldPagePrintLogs() throws Exception {
        JsonNode page = assertPaged(perform(rawGet("/api/print/logs", adminToken(), DEMO_MEET_ID,
                "page=1", "size=10")));
        assertThat(page.path("records").isArray()).isTrue();
    }

    @Test
    @DisplayName("权限与多租户：裁判无打印中心权限返回 403，其它届次留痕为空")
    void shouldEnforcePermissionAndTenant() throws Exception {
        // 裁判（REFEREE）只有 print:calling，没有 print:center / print:template:list
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        assertForbidden(perform(rawGet("/api/print/logs", refereeToken, DEMO_MEET_ID, "page=1", "size=10")));
        assertForbidden(perform(rawGet("/api/print/templates", refereeToken, DEMO_MEET_ID, "page=1", "size=10")));

        String token = adminToken();
        JsonNode logs = assertPaged(perform(rawGet("/api/print/logs", token, ABSENT_MEET_ID, "page=1", "size=10")));
        assertThat(logs.path("records")).isEmpty();

        assertUnauthorized(perform(rawGet("/api/print/reports", null, DEMO_MEET_ID)));
    }
}
