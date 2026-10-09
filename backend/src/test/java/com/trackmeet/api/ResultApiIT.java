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
 * 成绩录入、审核、名次与晋级接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}：演示届次 id=1（状态 {@code REG_OPEN}）、
 * 组别 id=1（高中男子组）、单位 id=1。演示数据不含项目/日程，本类会自行创建
 * 「竞赛单元 + 项目 + 轮次 + 已审核报名 + 日程条目」链路（名称随机，保证幂等）。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>{@code GET /api/results}：成绩分页（按项目/轮次过滤）</li>
 *     <li>{@code POST /api/results}、{@code POST /api/results/batch}：录入与批量录入</li>
 *     <li>{@code PUT /api/results/{id}}：修改（审核锁定后返回 {@code 40001}）</li>
 *     <li>{@code POST /api/results/status}：特殊状态标记（DNS 后名次为空）</li>
 *     <li>{@code POST /api/results/wind}：风速录入与超风速标记</li>
 *     <li>{@code POST /api/results/submit}、{@code POST /api/results/approve}：提交与审核（自动生成名次）</li>
 *     <li>{@code GET /api/results/rank}：名次预览（名次从 1 递增）</li>
 *     <li>{@code GET /api/results/{id}/audits}：修改/审核留痕（非空）</li>
 *     <li>{@code POST /api/results/advance/generate}、{@code GET /api/results/advance}：晋级名单</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class ResultApiIT extends ApiTestBase {

    /** 成绩已审核锁定，禁止直接修改（ResultCode.RESULT_LOCKED） */
    private static final int CODE_RESULT_LOCKED = 40001;

    @Test
    @DisplayName("成绩批量录入：一组一次提交两条，分页可按项目轮次查到")
    void shouldBatchSaveResults() throws Exception {
        String token = adminToken();
        ResultFixture fixture = prepareFixture(token, "测试项目成绩录入", 2);
        List<Long> registrationIds = fixture.registrationIds;
        assertThat(registrationIds).hasSize(2);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(resultPayload(fixture, registrationIds.get(0), "11\"23"));
        items.add(resultPayload(fixture, registrationIds.get(1), "11\"45"));

        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("meetId", DEMO_MEET_ID);
        batch.put("scheduleId", fixture.scheduleId);
        batch.put("results", items);
        JsonNode data = assertOk(perform(rawPost("/api/results/batch", token, DEMO_MEET_ID, batch)));
        assertThat(data.isArray()).isTrue();
        assertThat(data).hasSize(2);

        JsonNode page = assertPaged(perform(rawGet("/api/results", token, DEMO_MEET_ID,
                "page=1", "size=20", "eventId=" + fixture.eventId, "roundId=" + fixture.roundId)));
        assertThat(page.path("records")).hasSize(2);
        assertThat(page.path("records").get(0).path("auditStatus").asText()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("非法成绩文本：abc 无法解析为成绩数值，返回参数错误 400")
    void shouldRejectIllegalResultText() throws Exception {
        String token = adminToken();
        ResultFixture fixture = prepareFixture(token, "测试项目非法成绩", 1);

        Map<String, Object> payload = resultPayload(fixture, fixture.registrationIds.get(0), "abc");
        MvcResult result = perform(rawPost("/api/results", token, DEMO_MEET_ID, payload));
        assertBizCode(result, CODE_PARAM_ERROR);
    }

    @Test
    @DisplayName("特殊状态标记：标记 DNS 后成绩状态为 DNS 且无有效名次")
    void shouldMarkDnsWithoutRank() throws Exception {
        String token = adminToken();
        ResultFixture fixture = prepareFixture(token, "测试项目状态标记", 1);
        long resultId = createResult(token, fixture, fixture.registrationIds.get(0), "12\"10");

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ids", List.of(resultId));
        status.put("status", "DNS");
        status.put("reason", "测试标记 DNS");
        JsonNode affected = assertOk(perform(rawPost("/api/results/status", token, DEMO_MEET_ID, status)));
        assertThat(affected.asInt()).isEqualTo(1);

        JsonNode row = findResult(token, fixture, fixture.registrationIds.get(0));
        assertThat(row.path("status").asText()).isEqualTo("DNS");
        assertThat(row.path("rankNo").isMissingNode() || row.path("rankNo").asInt(0) == 0)
                .as("DNS 成绩不应有名次，实际 %s", row)
                .isTrue();

        // DSQ 必须填写犯规类型
        Map<String, Object> dsqWithoutFoul = new LinkedHashMap<>();
        dsqWithoutFoul.put("ids", List.of(resultId));
        dsqWithoutFoul.put("status", "DSQ");
        assertBizCode(perform(rawPost("/api/results/status", token, DEMO_MEET_ID, dsqWithoutFoul)), CODE_PARAM_ERROR);
    }

    @Test
    @DisplayName("风速录入：超风速（>2.0 m/s）标记 isWindLegal=0")
    void shouldRecordWindAndMarkIllegal() throws Exception {
        String token = adminToken();
        ResultFixture fixture = prepareFixture(token, "测试项目风速", 1);
        long resultId = createResult(token, fixture, fixture.registrationIds.get(0), "11\"30");

        Map<String, Object> wind = new LinkedHashMap<>();
        wind.put("resultIds", List.of(resultId));
        wind.put("windSpeed", 3.5);
        wind.put("windDirection", "TAIL");
        JsonNode affected = assertOk(perform(rawPost("/api/results/wind", token, DEMO_MEET_ID, wind)));
        assertThat(affected.asInt()).isEqualTo(1);

        JsonNode row = findResult(token, fixture, fixture.registrationIds.get(0));
        assertThat(row.path("windSpeed").asDouble()).isEqualTo(3.5d);
        assertThat(row.path("isWindLegal").asInt(-1))
                .as("超风速应标记为不合法（isWindLegal=0），实际 %s", row)
                .isEqualTo(0);

        // 风速读数缺失 -> 参数错误
        Map<String, Object> noSpeed = new LinkedHashMap<>();
        noSpeed.put("resultIds", List.of(resultId));
        assertBizCode(perform(rawPost("/api/results/wind", token, DEMO_MEET_ID, noSpeed)), CODE_PARAM_ERROR);
    }

    @Test
    @DisplayName("提交→审核通过→自动生成名次：名次从 1 递增，且审核留痕非空")
    void shouldApproveAndGenerateRankWithAudits() throws Exception {
        String token = adminToken();
        ResultFixture fixture = prepareFixture(token, "测试项目名次", 2);
        long firstResultId = createResult(token, fixture, fixture.registrationIds.get(0), "11\"10");
        long secondResultId = createResult(token, fixture, fixture.registrationIds.get(1), "11\"60");

        Map<String, Object> submit = new LinkedHashMap<>();
        submit.put("ids", List.of(firstResultId, secondResultId));
        JsonNode submitted = assertOk(perform(rawPost("/api/results/submit", token, DEMO_MEET_ID, submit)));
        assertThat(submitted.asInt()).isEqualTo(2);

        Map<String, Object> approve = new LinkedHashMap<>();
        approve.put("ids", List.of(firstResultId, secondResultId));
        approve.put("reason", "测试审核通过");
        JsonNode approved = assertOk(perform(rawPost("/api/results/approve", token, DEMO_MEET_ID, approve)));
        assertThat(approved.asInt()).isEqualTo(2);

        // 名次预览：名次从 1 递增
        JsonNode ranks = assertOk(perform(rawGet("/api/results/rank", token, DEMO_MEET_ID,
                "eventId=" + fixture.eventId, "roundId=" + fixture.roundId)));
        assertThat(ranks.isArray()).isTrue();
        assertThat(ranks).hasSize(2);
        assertThat(ranks.get(0).path("rankNo").asInt()).isEqualTo(1);
        assertThat(ranks.get(1).path("rankNo").asInt()).isEqualTo(2);

        // 审核留痕非空
        JsonNode audits = assertOk(perform(rawGet("/api/results/" + firstResultId + "/audits", token, DEMO_MEET_ID)));
        assertThat(audits.isArray()).isTrue();
        assertThat(audits).as("成绩提交/审核后应存在留痕记录").isNotEmpty();
    }

    @Test
    @DisplayName("审核后锁定：直接修改已审核成绩返回业务码 40001")
    void shouldRejectEditingApprovedResult() throws Exception {
        String token = adminToken();
        ResultFixture fixture = prepareFixture(token, "测试项目锁定", 1);
        long resultId = createResult(token, fixture, fixture.registrationIds.get(0), "11\"20");

        Map<String, Object> approve = new LinkedHashMap<>();
        approve.put("ids", List.of(resultId));
        assertOk(perform(rawPost("/api/results/approve", token, DEMO_MEET_ID, approve)));

        Map<String, Object> update = resultPayload(fixture, fixture.registrationIds.get(0), "10\"90");
        update.put("scheduleId", fixture.scheduleId);
        assertBizCode(perform(rawPut("/api/results/" + resultId, token, DEMO_MEET_ID, update)),
                CODE_RESULT_LOCKED);
    }

    @Test
    @DisplayName("晋级名单：自动生成后可查询到名单")
    void shouldGenerateAdvanceList() throws Exception {
        String token = adminToken();
        long sessionId = createUniqueSession(token);
        long eventId = createEvent(token, uniqueName("测试项目晋级"), "TRACK", "SPRINT", "MALE", 1L, 1);
        // 先建来源轮次（预赛），再建目标轮次（决赛），轮次 ID 直接取自创建结果，避免依赖排序
        long sourceRoundId = createRound(token, eventId, "PRE", "预赛");
        long finalRoundId = createFinalRound(token, eventId);
        long firstRegistrationId = createAthleteWithApprovedRegistration(token, eventId);
        long secondRegistrationId = createAthleteWithApprovedRegistration(token, eventId);
        long scheduleId = createScheduleEntry(token, sessionId, eventId, sourceRoundId, "2040-06-01 09:00:00");

        List<Long> registrationIds = List.of(firstRegistrationId, secondRegistrationId);
        ResultFixture fixture = new ResultFixture(eventId, sourceRoundId, scheduleId, registrationIds);
        String[] marks = {"11\"10", "11\"60"};
        List<Long> resultIds = new ArrayList<>();
        for (int index = 0; index < registrationIds.size(); index++) {
            resultIds.add(createResult(token, fixture, registrationIds.get(index), marks[index]));
        }
        Map<String, Object> approve = new LinkedHashMap<>();
        approve.put("ids", resultIds);
        assertOk(perform(rawPost("/api/results/approve", token, DEMO_MEET_ID, approve)));

        Map<String, Object> generate = new LinkedHashMap<>();
        generate.put("eventId", eventId);
        generate.put("fromRoundId", sourceRoundId);
        generate.put("toRoundId", finalRoundId);
        generate.put("groupTopN", 1);
        generate.put("bestLoserCount", 1);
        generate.put("targetCount", 2);
        JsonNode generated = assertOk(perform(rawPost("/api/results/advance/generate", token, DEMO_MEET_ID,
                generate)));
        assertThat(generated.isArray()).isTrue();
        assertThat(generated).isNotEmpty();

        JsonNode advanceList = assertOk(perform(rawGet("/api/results/advance", token, DEMO_MEET_ID,
                "eventId=" + eventId, "toRoundId=" + finalRoundId)));
        assertThat(advanceList.isArray()).isTrue();
        assertThat(advanceList).isNotEmpty();
    }

    @Test
    @DisplayName("权限与多租户：裁判无成绩审核权限返回 403，其它届次成绩查询为空")
    void shouldEnforcePermissionAndTenant() throws Exception {
        // 裁判（REFEREE）未被授予 result:approve 与 result:audit:list
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        MvcResult forbiddenApprove = perform(rawPost("/api/results/approve", refereeToken, DEMO_MEET_ID,
                Map.of("ids", List.of(1))));
        assertForbidden(forbiddenApprove);

        MvcResult forbiddenAudit = perform(rawGet("/api/results/1/audits", refereeToken, DEMO_MEET_ID));
        assertForbidden(forbiddenAudit);

        String token = adminToken();
        JsonNode page = assertPaged(perform(rawGet("/api/results", token, ABSENT_MEET_ID, "page=1", "size=10")));
        assertThat(page.path("records")).isEmpty();

        assertUnauthorized(perform(rawGet("/api/results", null, DEMO_MEET_ID, "page=1", "size=10")));
    }

    // ------------------------------------------------------------------
    // 私有辅助与夹具载体
    // ------------------------------------------------------------------

    /** 成绩用例的链路夹具：项目 + 轮次 + 日程 + 已审核报名 ID */
    private static final class ResultFixture {

        private final long eventId;
        private final long roundId;
        private final long scheduleId;
        private final List<Long> registrationIds;

        private ResultFixture(long eventId, long roundId, long scheduleId, List<Long> registrationIds) {
            this.eventId = eventId;
            this.roundId = roundId;
            this.scheduleId = scheduleId;
            this.registrationIds = registrationIds;
        }
    }

    /**
     * 构建「竞赛单元 + 项目（含决赛轮次与 N 条已审核报名）+ 日程条目」完整链路。
     */
    private ResultFixture prepareFixture(String token, String eventName, int athleteCount) throws Exception {
        long sessionId = createUniqueSession(token);
        long eventId = createSchedulableEvent(token, uniqueName(eventName), athleteCount);
        long roundId = firstRoundId(token, eventId);
        long scheduleId = createScheduleEntry(token, sessionId, eventId, roundId, "2041-06-01 09:00:00");
        List<Long> registrationIds = registrationIdsOf(token, eventId);
        return new ResultFixture(eventId, roundId, scheduleId, registrationIds);
    }

    private List<Long> registrationIdsOf(String token, long eventId) throws Exception {
        JsonNode data = registrationsOfEvent(token, eventId);
        List<Long> ids = new ArrayList<>();
        for (JsonNode record : data.path("records")) {
            ids.add(record.path("id").asLong());
        }
        return ids;
    }

    private Map<String, Object> resultPayload(ResultFixture fixture, long registrationId, String resultRaw) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("meetId", DEMO_MEET_ID);
        payload.put("scheduleId", fixture.scheduleId);
        payload.put("eventId", fixture.eventId);
        payload.put("roundId", fixture.roundId);
        payload.put("registrationId", registrationId);
        payload.put("resultRaw", resultRaw);
        payload.put("resultUnit", "s");
        return payload;
    }

    private long createResult(String token, ResultFixture fixture, long registrationId, String resultRaw)
            throws Exception {
        JsonNode data = assertOk(perform(rawPost("/api/results", token, DEMO_MEET_ID,
                resultPayload(fixture, registrationId, resultRaw))));
        return data.asLong();
    }

    /**
     * 按报名 ID 在成绩分页中定位成绩行。
     */
    private JsonNode findResult(String token, ResultFixture fixture, long registrationId) throws Exception {
        JsonNode page = assertPaged(perform(rawGet("/api/results", token, DEMO_MEET_ID,
                "page=1", "size=50", "eventId=" + fixture.eventId, "roundId=" + fixture.roundId)));
        for (JsonNode record : page.path("records")) {
            if (record.path("registrationId").asLong() == registrationId) {
                return record;
            }
        }
        throw new AssertionError("未在成绩分页中找到报名 " + registrationId + " 的成绩：" + page);
    }
}
