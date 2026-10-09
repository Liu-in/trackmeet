package com.trackmeet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackmeet.api.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 竞赛单元、日程编排、锁定与冲突检测接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}：演示届次 id=1、组别与单位各 4 条、
 * 届次级规则（{@code conflict_interval_min=30}）。演示数据不含项目与竞赛单元，本类会自行创建
 * （名称/日期/时段随机，保证幂等）。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>竞赛单元：{@code GET /api/sessions}、{@code GET /api/sessions/{id}}、{@code POST /api/sessions}、
 *         {@code PUT /api/sessions/{id}}、{@code DELETE /api/sessions/{id}}</li>
 *     <li>日程：{@code GET /api/schedule}、{@code GET /api/schedule/{id}}、{@code POST /api/schedule}、
 *         {@code POST /api/schedule/auto}、{@code PUT /api/schedule/{id}/lock}、{@code POST /api/schedule/{id}/move}</li>
 *     <li>冲突：{@code POST /api/schedule/conflicts/detect}、{@code GET /api/schedule/conflicts}</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class ScheduleApiIT extends ApiTestBase {

    @Test
    @DisplayName("竞赛单元 CRUD：新增后可分页查询、可改可查、删除后 404")
    void shouldManageSessions() throws Exception {
        String token = adminToken();
        long sessionId = createUniqueSession(token);

        JsonNode page = assertPaged(perform(rawGet("/api/sessions", token, DEMO_MEET_ID, "page=1", "size=20")));
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(1L);

        JsonNode detail = assertOk(perform(rawGet("/api/sessions/" + sessionId, token, DEMO_MEET_ID)));
        assertThat(detail.path("id").asLong()).isEqualTo(sessionId);
        String createdName = detail.path("sessionName").asText();
        assertThat(createdName).isNotBlank();

        // 修改名称（保持同一日期与时段，避免唯一键冲突）
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("meetId", DEMO_MEET_ID);
        update.put("sessionName", createdName + "改");
        update.put("sessionDate", detail.path("sessionDate").asText());
        update.put("period", detail.path("period").asText());
        update.put("startTime", "09:00");
        update.put("endTime", "17:00");
        assertOk(perform(rawPut("/api/sessions/" + sessionId, token, DEMO_MEET_ID, update)));

        JsonNode updated = assertOk(perform(rawGet("/api/sessions/" + sessionId, token, DEMO_MEET_ID)));
        assertThat(updated.path("sessionName").asText()).isEqualTo(createdName + "改");

        assertOk(perform(rawDelete("/api/sessions/" + sessionId, token, DEMO_MEET_ID)));
        assertBizCode(perform(rawGet("/api/sessions/" + sessionId, token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("竞赛单元参数校验：缺少时段返回 400，非法时段返回业务码 400")
    void shouldValidateSessionParams() throws Exception {
        String token = adminToken();

        Map<String, Object> noPeriod = new LinkedHashMap<>();
        noPeriod.put("meetId", DEMO_MEET_ID);
        noPeriod.put("sessionName", uniqueName("测试单元缺时段"));
        noPeriod.put("sessionDate", "2044-05-06");
        assertParamError(perform(rawPost("/api/sessions", token, DEMO_MEET_ID, noPeriod)));

        Map<String, Object> noDate = new LinkedHashMap<>();
        noDate.put("meetId", DEMO_MEET_ID);
        noDate.put("sessionName", uniqueName("测试单元缺日期"));
        noDate.put("period", "MORNING");
        assertParamError(perform(rawPost("/api/sessions", token, DEMO_MEET_ID, noDate)));

        Map<String, Object> badPeriod = new LinkedHashMap<>();
        badPeriod.put("meetId", DEMO_MEET_ID);
        badPeriod.put("sessionName", uniqueName("测试单元非法时段"));
        badPeriod.put("sessionDate", "2044-05-07");
        badPeriod.put("period", "NIGHT");
        assertBizCode(perform(rawPost("/api/sessions", token, DEMO_MEET_ID, badPeriod)), CODE_PARAM_ERROR);
    }

    @Test
    @DisplayName("自动编排预览：apply=false 返回计划明细且不落库")
    void shouldPreviewAutoScheduleWithoutPersisting() throws Exception {
        String token = adminToken();
        long sessionId = createUniqueSession(token);
        long eventId = createSchedulableEvent(token, uniqueName("测试项目编排预览"), 2);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("meetId", DEMO_MEET_ID);
        request.put("scope", "EVENT");
        request.put("eventIds", List.of(eventId));
        request.put("apply", false);

        JsonNode plan = assertOk(perform(rawPost("/api/schedule/auto", token, DEMO_MEET_ID, request)));
        assertThat(plan.path("applied").asBoolean()).isFalse();
        assertThat(plan.path("items").isArray()).isTrue();
        assertThat(plan.path("items"))
                .as("预览应返回计划明细（含项目 %s 的日程草案）", eventId)
                .isNotEmpty();
        assertThat(plan.path("summary").isObject()).isTrue();

        // 未落库：按项目查询日程应为空
        JsonNode schedules = assertPaged(perform(rawGet("/api/schedule", token, DEMO_MEET_ID,
                "page=1", "size=50", "eventId=" + eventId)));
        assertThat(schedules.path("records")).isEmpty();

        // 清理：删除占位单元（无日程条目时可删）
        assertOk(perform(rawDelete("/api/sessions/" + sessionId, token, DEMO_MEET_ID)));
    }

    @Test
    @DisplayName("自动编排落库：apply=true 后可查询到日程")
    void shouldApplyAutoSchedule() throws Exception {
        String token = adminToken();
        createUniqueSession(token);
        long eventId = createSchedulableEvent(token, uniqueName("测试项目编排落库"), 2);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("meetId", DEMO_MEET_ID);
        request.put("scope", "EVENT");
        request.put("eventIds", List.of(eventId));
        request.put("apply", true);

        JsonNode plan = assertOk(perform(rawPost("/api/schedule/auto", token, DEMO_MEET_ID, request)));
        assertThat(plan.path("applied").asBoolean()).isTrue();
        assertThat(plan.path("items")).isNotEmpty();

        JsonNode schedules = retryGet("/api/schedule", token, DEMO_MEET_ID,
                node -> node.path("data").path("records").size() > 0,
                "page=1", "size=50", "eventId=" + eventId);
        assertThat(schedules.path("data").path("records"))
                .as("apply=true 后应能查询到该项目的日程：%s", schedules)
                .isNotEmpty();
    }

    @Test
    @DisplayName("锁定与移动：锁定后的日程拖拽返回冲突业务码 409")
    void shouldRejectMovingLockedSchedule() throws Exception {
        String token = adminToken();
        long sessionId = createUniqueSession(token);
        long eventId = createSchedulableEvent(token, uniqueName("测试项目锁定移动"), 2);
        long roundId = firstRoundId(token, eventId);
        long scheduleId = createScheduleEntry(token, sessionId, eventId, roundId, "2038-06-01 09:00:00");

        JsonNode detail = assertOk(perform(rawGet("/api/schedule/" + scheduleId, token, DEMO_MEET_ID)));
        assertThat(detail.path("id").asLong()).isEqualTo(scheduleId);

        // 锁定
        assertOk(perform(rawPut("/api/schedule/" + scheduleId + "/lock", token, DEMO_MEET_ID,
                Map.of("isLocked", 1))));

        // 锁定后移动 -> CONFLICT
        Map<String, Object> move = new LinkedHashMap<>();
        move.put("sessionId", sessionId);
        move.put("startTime", "2038-06-01 10:00:00");
        move.put("adjustReason", "测试移动");
        assertBizCode(perform(rawPost("/api/schedule/" + scheduleId + "/move", token, DEMO_MEET_ID, move)),
                CODE_CONFLICT);

        // 解锁后可移动
        assertOk(perform(rawPut("/api/schedule/" + scheduleId + "/lock", token, DEMO_MEET_ID,
                Map.of("isLocked", 0))));
        assertOk(perform(rawPost("/api/schedule/" + scheduleId + "/move", token, DEMO_MEET_ID, move)));
    }

    @Test
    @DisplayName("冲突检测：返回冲突数组，冲突清单可分页查询")
    void shouldDetectConflicts() throws Exception {
        String token = adminToken();
        long sessionId = createUniqueSession(token);
        long eventId = createSchedulableEvent(token, uniqueName("测试项目冲突"), 2);
        long roundId = firstRoundId(token, eventId);
        createScheduleEntry(token, sessionId, eventId, roundId, "2039-06-01 09:00:00");

        Map<String, Object> detect = new LinkedHashMap<>();
        detect.put("meetId", DEMO_MEET_ID);
        JsonNode conflicts = assertOk(perform(rawPost("/api/schedule/conflicts/detect", token, DEMO_MEET_ID,
                detect)));
        assertThat(conflicts.isArray()).isTrue();

        MvcResult pageResult = perform(rawGet("/api/schedule/conflicts", token, DEMO_MEET_ID,
                "page=1", "size=20"));
        JsonNode page = assertPaged(pageResult);
        assertThat(page.path("records").isArray()).isTrue();
    }

    @Test
    @DisplayName("日程详情与参数校验：不存在返回 404，缺少必填字段返回 400")
    void shouldValidateScheduleDetail() throws Exception {
        String token = adminToken();

        assertBizCode(perform(rawGet("/api/schedule/999999", token, DEMO_MEET_ID)), CODE_NOT_FOUND);

        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("meetId", DEMO_MEET_ID);
        assertParamError(perform(rawPost("/api/schedule", token, DEMO_MEET_ID, missing)));
    }

    @Test
    @DisplayName("权限与多租户：裁判无自动编排权限返回 403，其它届次单元列表为空")
    void shouldEnforcePermissionAndTenant() throws Exception {
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        MvcResult forbidden = perform(rawPost("/api/schedule/auto", refereeToken, DEMO_MEET_ID,
                Map.of("meetId", DEMO_MEET_ID, "scope", "ALL")));
        assertForbidden(forbidden);

        String token = adminToken();
        JsonNode page = assertPaged(perform(rawGet("/api/sessions", token, ABSENT_MEET_ID, "page=1", "size=10")));
        assertThat(page.path("records")).isEmpty();

        assertUnauthorized(perform(rawGet("/api/sessions", null, DEMO_MEET_ID, "page=1", "size=10")));
    }
}
