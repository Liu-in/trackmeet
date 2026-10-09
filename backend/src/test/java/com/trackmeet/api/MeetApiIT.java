package com.trackmeet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackmeet.api.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 届次（运动会）/ 组别 / 单位 / 场地接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}，其中
 * {@code sql/06-demo-data.sql} 提供届次 id=1、4 个组别、4 个单位与 4 名以上运动员。
 * 推荐 {@code cd deploy && docker compose up -d mysql} 后在容器内执行同一批脚本。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>届次：{@code GET /api/meets}、{@code GET /api/meets/{id}}、{@code POST /api/meets}、
 *         {@code PUT /api/meets/{id}}、{@code DELETE /api/meets/{id}}、{@code PUT /api/meets/{id}/status}</li>
 *     <li>组别：{@code GET /api/meets/{id}/groups}、{@code POST /api/meets/{id}/groups}、
 *         {@code GET /api/groups/{id}}、{@code PUT /api/groups/{id}}、{@code DELETE /api/groups/{id}}</li>
 *     <li>单位：{@code GET /api/units}、{@code GET /api/units/{id}}、{@code POST /api/units}、
 *         {@code PUT /api/units/{id}}、{@code DELETE /api/units/{id}}</li>
 *     <li>场地：{@code GET /api/venues}、{@code GET /api/venues/{id}}、{@code POST /api/venues}、
 *         {@code PUT /api/venues/{id}}、{@code DELETE /api/venues/{id}}</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class MeetApiIT extends ApiTestBase {

    @Test
    @DisplayName("届次分页：返回分页结构且可按关键字命中新建届次")
    void shouldPageMeets() throws Exception {
        String token = adminToken();

        MvcResult pageResult = perform(rawGet("/api/meets", token, null, "page=1", "size=10"));
        JsonNode page = assertPaged(pageResult);
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(1L);

        String meetName = uniqueName("测试运动会");
        Long meetId = createMeet(token, meetName);
        assertThat(meetId).isNotNull();

        MvcResult filteredResult = perform(rawGet("/api/meets", token, null,
                "page=1", "size=10", "keyword=" + meetName));
        JsonNode filtered = assertPaged(filteredResult);
        assertThat(filtered.path("records")).hasSize(1);
        assertThat(filtered.path("records").get(0).path("meetName").asText()).isEqualTo(meetName);
    }

    @Test
    @DisplayName("届次详情：存在返回详情，不存在返回 404 业务码")
    void shouldGetMeetDetail() throws Exception {
        String token = adminToken();

        JsonNode data = assertOk(perform(rawGet("/api/meets/" + DEMO_MEET_ID, token, null)));
        assertThat(data.path("meetName").asText()).isNotBlank();
        assertThat(data.path("id").asLong()).isEqualTo(DEMO_MEET_ID);

        MvcResult missing = perform(rawGet("/api/meets/999999", token, null));
        assertBizCode(missing, CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("届次新增/修改/删除：草稿可删且软删后详情不可查")
    void shouldCreateUpdateAndSoftDeleteMeet() throws Exception {
        String token = adminToken();
        String meetName = uniqueName("测试运动会");

        Long id = createMeet(token, meetName);
        assertThat(id).isNotNull();

        JsonNode detail = assertOk(perform(rawGet("/api/meets/" + id, token, null)));
        assertThat(detail.path("meetName").asText()).isEqualTo(meetName);
        assertThat(detail.path("status").asText()).isEqualTo("DRAFT");

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("meetName", meetName);
        update.put("host", "测试主办单位");
        assertOk(perform(rawPut("/api/meets/" + id, token, null, update)));
        JsonNode updated = assertOk(perform(rawGet("/api/meets/" + id, token, null)));
        assertThat(updated.path("host").asText()).isEqualTo("测试主办单位");

        assertOk(perform(rawDelete("/api/meets/" + id, token, null)));
        assertBizCode(perform(rawGet("/api/meets/" + id, token, null)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("届次状态流转：只允许按顺序前进一步，非法目标状态返回 20001")
    void shouldChangeMeetStatusStepByStep() throws Exception {
        String token = adminToken();
        Long id = createMeet(token, uniqueName("测试运动会状态"));

        // 跳步流转被状态机拒绝
        assertBizCode(perform(rawPut("/api/meets/" + id + "/status", token, null, Map.of("status", "SCHEDULING"))), 20001);
        // 目标状态缺失触发参数校验
        assertParamError(perform(rawPut("/api/meets/" + id + "/status", token, null, Map.of("status", ""))));
        // 前进一步成功
        assertOk(perform(rawPut("/api/meets/" + id + "/status", token, null, Map.of("status", "REG_OPEN"))));
        JsonNode detail = assertOk(perform(rawGet("/api/meets/" + id, token, null)));
        assertThat(detail.path("status").asText()).isEqualTo("REG_OPEN");
        // 非草稿/归档状态不允许删除
        assertBizCode(perform(rawDelete("/api/meets/" + id, token, null)), 20001);
    }

    @Test
    @DisplayName("组别：在届次下新增、修改、查询、删除，编码重复返回 409")
    void shouldManageMeetGroups() throws Exception {
        String token = adminToken();
        String groupName = uniqueName("测试组别");
        String groupCode = "TG_" + uniqueSuffix();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupName", groupName);
        body.put("groupCode", groupCode);
        body.put("gender", "MALE");
        JsonNode created = assertOk(perform(rawPost("/api/meets/" + DEMO_MEET_ID + "/groups", token, DEMO_MEET_ID, body)));
        long groupId = created.asLong();
        assertThat(groupId).isPositive();

        // 编码重复：唯一键冲突
        Map<String, Object> duplicate = new LinkedHashMap<>();
        duplicate.put("groupName", uniqueName("测试组别重复"));
        duplicate.put("groupCode", groupCode);
        assertBizCode(perform(rawPost("/api/meets/" + DEMO_MEET_ID + "/groups", token, DEMO_MEET_ID, duplicate)),
                CODE_CONFLICT);

        // 届次下组别列表可见
        JsonNode groups = assertOk(perform(rawGet("/api/meets/" + DEMO_MEET_ID + "/groups", token, DEMO_MEET_ID)));
        assertThat(groups.isArray()).isTrue();
        assertThat(containsName(groups, "groupName", groupName)).isTrue();

        // 修改
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("groupName", groupName + "改");
        update.put("groupCode", groupCode);
        assertOk(perform(rawPut("/api/groups/" + groupId, token, DEMO_MEET_ID, update)));
        JsonNode detail = assertOk(perform(rawGet("/api/groups/" + groupId, token, DEMO_MEET_ID)));
        assertThat(detail.path("groupName").asText()).isEqualTo(groupName + "改");

        // 删除后不可查
        assertOk(perform(rawDelete("/api/groups/" + groupId, token, DEMO_MEET_ID)));
        assertBizCode(perform(rawGet("/api/groups/" + groupId, token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("单位：新增、按关键字查询、修改、删除，编码重复返回 409")
    void shouldManageUnits() throws Exception {
        String token = adminToken();
        String unitName = uniqueName("测试单位");
        String unitCode = "TU_" + uniqueSuffix();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unitName", unitName);
        body.put("unitCode", unitCode);
        body.put("shortName", "测试");
        body.put("unitType", "SCHOOL");
        JsonNode created = assertOk(perform(rawPost("/api/units", token, DEMO_MEET_ID, body)));
        long unitId = created.asLong();
        assertThat(unitId).isPositive();

        MvcResult pageResult = perform(rawGet("/api/units", token, DEMO_MEET_ID,
                "page=1", "size=10", "keyword=" + unitName));
        JsonNode page = assertPaged(pageResult);
        assertThat(page.path("records")).hasSize(1);
        assertThat(page.path("records").get(0).path("unitName").asText()).isEqualTo(unitName);

        // 编码重复
        Map<String, Object> duplicate = new LinkedHashMap<>();
        duplicate.put("unitName", uniqueName("测试单位重复"));
        duplicate.put("unitCode", unitCode);
        assertBizCode(perform(rawPost("/api/units", token, DEMO_MEET_ID, duplicate)), CODE_CONFLICT);

        // 缺少必填字段：单位编码 -> 参数校验失败
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("unitName", uniqueName("测试单位缺编码"));
        assertParamError(perform(rawPost("/api/units", token, DEMO_MEET_ID, invalid)));

        // 修改
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("unitName", unitName);
        update.put("unitCode", unitCode);
        update.put("leader", "测试领队");
        assertOk(perform(rawPut("/api/units/" + unitId, token, DEMO_MEET_ID, update)));
        JsonNode detail = assertOk(perform(rawGet("/api/units/" + unitId, token, DEMO_MEET_ID)));
        assertThat(detail.path("leader").asText()).isEqualTo("测试领队");

        // 删除后不可查
        assertOk(perform(rawDelete("/api/units/" + unitId, token, DEMO_MEET_ID)));
        assertBizCode(perform(rawGet("/api/units/" + unitId, token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("场地：新增、列表、修改、删除，道数非法返回参数错误")
    void shouldManageVenues() throws Exception {
        String token = adminToken();
        String venueName = uniqueName("测试场地");
        String venueCode = "TV_" + uniqueSuffix();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("venueName", venueName);
        body.put("venueCode", venueCode);
        body.put("venueType", "TRACK");
        body.put("laneCount", 8);
        JsonNode created = assertOk(perform(rawPost("/api/venues", token, DEMO_MEET_ID, body)));
        long venueId = created.asLong();
        assertThat(venueId).isPositive();

        JsonNode list = assertOk(perform(rawGet("/api/venues", token, DEMO_MEET_ID, "meetId=" + DEMO_MEET_ID)));
        assertThat(list.isArray()).isTrue();
        assertThat(containsName(list, "venueName", venueName)).isTrue();

        // 非法道数（业务参数校验，HTTP 200 + code 400）
        Map<String, Object> illegalLane = new LinkedHashMap<>();
        illegalLane.put("venueName", uniqueName("测试场地非法道数"));
        illegalLane.put("venueCode", "TV_" + uniqueSuffix());
        illegalLane.put("venueType", "TRACK");
        illegalLane.put("laneCount", 7);
        assertBizCode(perform(rawPost("/api/venues", token, DEMO_MEET_ID, illegalLane)), CODE_PARAM_ERROR);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("venueName", venueName + "改");
        update.put("venueCode", venueCode);
        update.put("venueType", "TRACK");
        assertOk(perform(rawPut("/api/venues/" + venueId, token, DEMO_MEET_ID, update)));
        JsonNode detail = assertOk(perform(rawGet("/api/venues/" + venueId, token, DEMO_MEET_ID)));
        assertThat(detail.path("venueName").asText()).isEqualTo(venueName + "改");

        assertOk(perform(rawDelete("/api/venues/" + venueId, token, DEMO_MEET_ID)));
        assertBizCode(perform(rawGet("/api/venues/" + venueId, token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("权限与多租户：编排员无单位新增权限返回 403，其它届次查不到数据")
    void shouldEnforcePermissionAndTenantIsolation() throws Exception {
        String adminToken = adminToken();

        // 编排员（SCHEDULER）未被授予 meet:unit:add
        String schedulerToken = assumeLogin(SCHEDULER_USERNAME);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unitName", uniqueName("测试单位越权"));
        body.put("unitCode", "TP_" + uniqueSuffix());
        MvcResult forbidden = perform(rawPost("/api/units", schedulerToken, DEMO_MEET_ID, body));
        assertForbidden(forbidden);

        // 多租户隔离：不存在的届次下无单位/组别数据
        JsonNode units = assertPaged(perform(rawGet("/api/units", adminToken, ABSENT_MEET_ID, "page=1", "size=10")));
        assertThat(units.path("records")).isEmpty();

        JsonNode groups = assertPaged(perform(rawGet("/api/groups", adminToken, ABSENT_MEET_ID, "page=1", "size=10")));
        assertThat(groups.path("records")).isEmpty();

        // 匿名访问被拒绝
        assertUnauthorized(perform(rawGet("/api/units", null, DEMO_MEET_ID, "page=1", "size=10")));
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    private Long createMeet(String token, String meetName) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetName", meetName);
        body.put("edition", uniqueName("届次"));
        body.put("host", "测试主办单位");
        body.put("startDate", "2030-10-18");
        body.put("endDate", "2030-10-20");
        JsonNode data = assertOk(perform(rawPost("/api/meets", token, null, body)));
        return data.isNumber() ? data.asLong() : null;
    }

    private boolean containsName(JsonNode array, String field, String expected) {
        for (JsonNode node : array) {
            if (expected.equals(node.path(field).asText())) {
                return true;
            }
        }
        return false;
    }
}
