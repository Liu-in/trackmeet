package com.trackmeet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackmeet.api.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报名接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}：演示届次 id=1 状态为
 * {@code REG_OPEN}（报名期开放）、组别与单位各有 4 条、届次级规则 {@code athlete_max_events=2}、
 * {@code max_per_unit=3}。演示数据未内置项目，因此本类会按需创建项目（名称带时间戳，幂等）。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>{@code GET /api/registrations}、{@code GET /api/registrations/{id}}</li>
 *     <li>{@code POST /api/registrations}、{@code POST /api/registrations/batch}</li>
 *     <li>{@code POST /api/registrations/approve}、{@code POST /api/registrations/{id}/reject}</li>
 *     <li>{@code GET /api/registrations/statistics}</li>
 *     <li>限制校验：重复报名 {@code 20004}、超出每人限报项数 {@code 20003}</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class RegistrationApiIT extends ApiTestBase {

    @Test
    @DisplayName("报名新增与查询：新增成功、分页按项目命中、详情可查")
    void shouldCreateAndQueryRegistration() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目报名"), "TRACK", "SPRINT", "MALE", 1L, 1);
        long athleteId = createAthlete(token, uniqueName("测试报名运动员"), 1L, 1L, "TR_" + uniqueSuffix());

        long registrationId = register(token, eventId, athleteId);
        assertThat(registrationId).isPositive();

        MvcResult pageResult = perform(rawGet("/api/registrations", token, DEMO_MEET_ID,
                "page=1", "size=10", "eventId=" + eventId));
        JsonNode page = assertPaged(pageResult);
        assertThat(page.path("records")).hasSize(1);
        assertThat(page.path("records").get(0).path("athleteId").asLong()).isEqualTo(athleteId);
        assertThat(page.path("records").get(0).path("status").asText()).isEqualTo("PENDING");

        JsonNode detail = assertOk(perform(rawGet("/api/registrations/" + registrationId, token, DEMO_MEET_ID)));
        assertThat(detail.path("eventId").asLong()).isEqualTo(eventId);
        assertThat(detail.path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("批量报名：一个项目一次提交多名运动员")
    void shouldBatchRegister() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目批量报名"), "TRACK", "SPRINT", "MALE", 1L, 1);
        List<Long> athleteIds = new ArrayList<>();
        athleteIds.add(createAthlete(token, uniqueName("测试批量运动员A"), 1L, 1L, "TB_A_" + uniqueSuffix()));
        athleteIds.add(createAthlete(token, uniqueName("测试批量运动员B"), 1L, 1L, "TB_B_" + uniqueSuffix()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("eventId", eventId);
        body.put("athleteIds", athleteIds);
        JsonNode data = assertOk(perform(rawPost("/api/registrations/batch", token, DEMO_MEET_ID, body)));
        assertThat(data.isArray()).isTrue();
        assertThat(data).hasSize(2);
    }

    @Test
    @DisplayName("审核通过：批量审核后状态变为 APPROVED，ID 列表为空则参数错误")
    void shouldApproveRegistration() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目审核通过"), "TRACK", "SPRINT", "MALE", 1L, 1);
        long athleteId = createAthlete(token, uniqueName("测试审核运动员"), 1L, 1L, "TA_" + uniqueSuffix());
        long registrationId = register(token, eventId, athleteId);

        Map<String, Object> approve = new LinkedHashMap<>();
        approve.put("ids", List.of(registrationId));
        JsonNode approved = assertOk(perform(rawPost("/api/registrations/approve", token, DEMO_MEET_ID, approve)));
        assertThat(approved.asInt()).isGreaterThanOrEqualTo(1);

        JsonNode detail = assertOk(perform(rawGet("/api/registrations/" + registrationId, token, DEMO_MEET_ID)));
        assertThat(detail.path("status").asText()).isEqualTo("APPROVED");

        // ID 列表为空：@NotEmpty 触发参数校验
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("ids", new ArrayList<>());
        assertParamError(perform(rawPost("/api/registrations/approve", token, DEMO_MEET_ID, empty)));
    }

    @Test
    @DisplayName("审核驳回：附退回原因后状态变为 REJECTED，原因缺失则参数错误")
    void shouldRejectRegistration() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目驳回"), "TRACK", "SPRINT", "MALE", 1L, 1);
        long athleteId = createAthlete(token, uniqueName("测试驳回运动员"), 1L, 1L, "TR_" + uniqueSuffix());
        long registrationId = register(token, eventId, athleteId);

        assertParamError(perform(rawPost("/api/registrations/" + registrationId + "/reject", token, DEMO_MEET_ID,
                Map.of("rejectReason", ""))));

        assertOk(perform(rawPost("/api/registrations/" + registrationId + "/reject", token, DEMO_MEET_ID,
                Map.of("rejectReason", "测试退回原因"))));
        JsonNode detail = assertOk(perform(rawGet("/api/registrations/" + registrationId, token, DEMO_MEET_ID)));
        assertThat(detail.path("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("限制校验-重复报名：同一运动员重复报同一项目返回业务码 20004")
    void shouldRejectDuplicateRegistration() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目重复报名"), "TRACK", "SPRINT", "MALE", 1L, 1);
        long athleteId = createAthlete(token, uniqueName("测试重复报名运动员"), 1L, 1L, "TD_" + uniqueSuffix());

        register(token, eventId, athleteId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("eventId", eventId);
        body.put("athleteId", athleteId);
        assertBizCode(perform(rawPost("/api/registrations", token, DEMO_MEET_ID, body)), 20004);
    }

    @Test
    @DisplayName("限制校验-超项：同一运动员报名第 3 个单项返回业务码 20003")
    void shouldRejectOverEventLimit() throws Exception {
        String token = adminToken();
        long athleteId = createAthlete(token, uniqueName("测试超项运动员"), 1L, 1L, "TL_" + uniqueSuffix());
        long firstEventId = createEvent(token, uniqueName("测试项目超项1"), "TRACK", "SPRINT", "MALE", 1L, 1);
        long secondEventId = createEvent(token, uniqueName("测试项目超项2"), "TRACK", "SPRINT", "MALE", 1L, 1);
        long thirdEventId = createEvent(token, uniqueName("测试项目超项3"), "TRACK", "SPRINT", "MALE", 1L, 1);

        register(token, firstEventId, athleteId);
        register(token, secondEventId, athleteId);

        // athlete_max_events = 2（sql/06-demo-data.sql 的届次级规则）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("eventId", thirdEventId);
        body.put("athleteId", athleteId);
        assertBizCode(perform(rawPost("/api/registrations", token, DEMO_MEET_ID, body)), 20003);
    }

    @Test
    @DisplayName("报名统计：按单位维度返回统计数组，非法维度返回参数错误")
    void shouldGetRegistrationStatistics() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目统计"), "TRACK", "SPRINT", "MALE", 1L, 1);
        long athleteId = createAthlete(token, uniqueName("测试统计运动员"), 1L, 1L, "TS_" + uniqueSuffix());
        register(token, eventId, athleteId);

        JsonNode data = assertOk(perform(rawGet("/api/registrations/statistics", token, DEMO_MEET_ID,
                "dimension=UNIT")));
        assertThat(data.isArray()).isTrue();
        assertThat(data).isNotEmpty();

        assertBizCode(perform(rawGet("/api/registrations/statistics", token, DEMO_MEET_ID, "dimension=UNKNOWN")),
                CODE_PARAM_ERROR);
    }

    @Test
    @DisplayName("权限与多租户：裁判无审核权限返回 403，其它届次查询为空，匿名返回 401")
    void shouldEnforcePermissionAndTenant() throws Exception {
        // 裁判（REFEREE）未授予 registration:approve
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        MvcResult forbidden = perform(rawPost("/api/registrations/approve", refereeToken, DEMO_MEET_ID,
                Map.of("ids", List.of(1))));
        assertForbidden(forbidden);

        String token = adminToken();
        JsonNode page = assertPaged(perform(rawGet("/api/registrations", token, ABSENT_MEET_ID,
                "page=1", "size=10")));
        assertThat(page.path("records")).isEmpty();

        assertUnauthorized(perform(rawGet("/api/registrations", null, DEMO_MEET_ID, "page=1", "size=10")));
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    /**
     * 新增一条个人项目报名，返回报名 ID。
     */
    private long register(String token, long eventId, long athleteId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("eventId", eventId);
        body.put("athleteId", athleteId);
        JsonNode data = assertOk(perform(rawPost("/api/registrations", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }
}
