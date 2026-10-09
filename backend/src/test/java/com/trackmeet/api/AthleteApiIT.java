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
 * 运动员接口集成测试（含 Excel 导入模板下载）。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}：演示届次 id=1、
 * 组别 id=1..4、单位 id=1..4、16 名演示运动员。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>{@code GET /api/athletes}：分页 + keyword（姓名/证件号/号码）过滤</li>
 *     <li>{@code GET /api/athletes/{id}}：详情 / 不存在 404</li>
 *     <li>{@code POST /api/athletes}：新增（含必填校验）</li>
 *     <li>{@code PUT /api/athletes/{id}}：修改</li>
 *     <li>{@code GET /api/athletes/import-template}：Excel 模板下载（校验 xlsx 文件头 PK）</li>
 *     <li>权限：裁判（REFEREE）无 {@code athlete:add}，越权返回 403；多租户隔离用 X-Meet-Id</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class AthleteApiIT extends ApiTestBase {

    @Test
    @DisplayName("运动员分页：返回分页结构，keyword 可命中新增运动员")
    void shouldPageAthletes() throws Exception {
        String token = adminToken();

        JsonNode page = assertPaged(perform(rawGet("/api/athletes", token, DEMO_MEET_ID, "page=1", "size=10")));
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(1L);

        String athleteName = uniqueName("测试运动员");
        long athleteId = createAthlete(token, athleteName, 1L, 1L, "TB_" + uniqueSuffix());
        assertThat(athleteId).isPositive();

        JsonNode filtered = assertPaged(perform(rawGet("/api/athletes", token, DEMO_MEET_ID,
                "page=1", "size=10", "keyword=" + athleteName)));
        assertThat(filtered.path("records")).hasSize(1);
        assertThat(filtered.path("records").get(0).path("athleteName").asText()).isEqualTo(athleteName);
    }

    @Test
    @DisplayName("运动员详情：存在返回详情，不存在返回 404")
    void shouldGetAthleteDetail() throws Exception {
        String token = adminToken();

        JsonNode existing = assertOk(perform(rawGet("/api/athletes/1", token, DEMO_MEET_ID)));
        assertThat(existing.path("athleteName").asText()).isNotBlank();

        assertBizCode(perform(rawGet("/api/athletes/999999", token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("运动员新增与修改：新增后可查，修改后详情同步变更")
    void shouldCreateAndUpdateAthlete() throws Exception {
        String token = adminToken();
        String athleteName = uniqueName("测试运动员新增");
        String bibNo = "TB_" + uniqueSuffix();
        long athleteId = createAthlete(token, athleteName, 1L, 1L, bibNo);

        JsonNode detail = assertOk(perform(rawGet("/api/athletes/" + athleteId, token, DEMO_MEET_ID)));
        assertThat(detail.path("athleteName").asText()).isEqualTo(athleteName);
        assertThat(detail.path("bibNo").asText()).isEqualTo(bibNo);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("meetId", DEMO_MEET_ID);
        update.put("athleteName", athleteName + "改");
        update.put("gender", "MALE");
        update.put("groupId", 1);
        update.put("unitId", 1);
        update.put("bibNo", bibNo);
        update.put("className", "测试班级二");
        assertOk(perform(rawPut("/api/athletes/" + athleteId, token, DEMO_MEET_ID, update)));

        JsonNode updated = assertOk(perform(rawGet("/api/athletes/" + athleteId, token, DEMO_MEET_ID)));
        assertThat(updated.path("athleteName").asText()).isEqualTo(athleteName + "改");
        assertThat(updated.path("className").asText()).isEqualTo("测试班级二");
    }

    @Test
    @DisplayName("运动员参数校验：姓名缺失或组别缺失返回 HTTP 400 与参数错误码")
    void shouldRejectInvalidAthleteParams() throws Exception {
        String token = adminToken();

        Map<String, Object> noName = new LinkedHashMap<>();
        noName.put("meetId", DEMO_MEET_ID);
        noName.put("gender", "MALE");
        noName.put("groupId", 1);
        noName.put("unitId", 1);
        assertParamError(perform(rawPost("/api/athletes", token, DEMO_MEET_ID, noName)));

        Map<String, Object> noGroup = new LinkedHashMap<>();
        noGroup.put("meetId", DEMO_MEET_ID);
        noGroup.put("athleteName", uniqueName("测试运动员无组别"));
        noGroup.put("gender", "MALE");
        noGroup.put("unitId", 1);
        assertParamError(perform(rawPost("/api/athletes", token, DEMO_MEET_ID, noGroup)));
    }

    @Test
    @DisplayName("导入模板下载：返回 xlsx 内容类型且文件头为 PK")
    void shouldDownloadImportTemplate() throws Exception {
        String token = adminToken();

        MvcResult result = perform(rawGet("/api/athletes/import-template", token, DEMO_MEET_ID));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.contains("spreadsheetml") || contentType.contains("octet-stream"))
                .as("应对应 Excel xlsx 内容类型，实际 %s", contentType)
                .isTrue();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(0);
        // xlsx 本质是 zip，文件头固定为 50 4B（"PK"）
        assertThat(new String(bytes, 0, Math.min(2, bytes.length), StandardCharsets.US_ASCII)).isEqualTo("PK");
    }

    @Test
    @DisplayName("权限与多租户：裁判无新增权限返回 403，其它届次查询为空")
    void shouldEnforcePermissionAndTenant() throws Exception {
        // 裁判（REFEREE）有 athlete:list，但没有 athlete:add
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("athleteName", uniqueName("测试运动员越权"));
        body.put("gender", "MALE");
        body.put("groupId", 1);
        body.put("unitId", 1);
        assertForbidden(perform(rawPost("/api/athletes", refereeToken, DEMO_MEET_ID, body)));

        // 多租户隔离：不存在的届次下没有运动员
        String token = adminToken();
        JsonNode page = assertPaged(perform(rawGet("/api/athletes", token, ABSENT_MEET_ID, "page=1", "size=10")));
        assertThat(page.path("records")).isEmpty();

        // 匿名访问被拒绝
        assertUnauthorized(perform(rawGet("/api/athletes", null, DEMO_MEET_ID, "page=1", "size=10")));
    }
}
