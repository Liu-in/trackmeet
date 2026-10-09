package com.trackmeet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackmeet.api.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目（event）与规则配置接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}：演示届次 id=1、
 * 组别 id=1..4、届次级规则 {@code meet_rule_config}（{@code admit_rank=8}、{@code athlete_max_events=2}）、
 * 名次分方案 id=1/2 及其明细、加分规则若干。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>项目：{@code GET /api/events}、{@code GET /api/events/{id}}、{@code POST /api/events}、
 *         {@code PUT /api/events/{id}}、{@code DELETE /api/events/{id}}</li>
 *     <li>轮次：{@code POST /api/events/{id}/rounds}、{@code GET /api/events/{id}/rounds}</li>
 *     <li>项目级规则：{@code GET/PUT /api/events/{id}/rule-config}、
 *         {@code POST /api/events/{id}/rule-config/reset}（恢复继承）</li>
 *     <li>规则查询：{@code GET /api/score-rules}（名次分方案）、{@code GET /api/bonus-rules}（加分规则）</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class EventApiIT extends ApiTestBase {

    @Test
    @DisplayName("项目分页：分页结构正确且 keyword 可命中新建项目")
    void shouldPageEvents() throws Exception {
        String token = adminToken();
        String eventName = uniqueName("测试项目分页");
        long eventId = createEvent(token, eventName, "TRACK", "SPRINT", "MALE", 1L, 1);

        JsonNode page = assertPaged(perform(rawGet("/api/events", token, DEMO_MEET_ID, "page=1", "size=10")));
        assertThat(page.path("records").isArray()).isTrue();

        JsonNode filtered = assertPaged(perform(rawGet("/api/events", token, DEMO_MEET_ID,
                "page=1", "size=10", "keyword=" + eventName)));
        assertThat(filtered.path("records")).hasSize(1);
        assertThat(filtered.path("records").get(0).path("id").asLong()).isEqualTo(eventId);
        assertThat(filtered.path("records").get(0).path("eventType").asText()).isEqualTo("TRACK");
    }

    @Test
    @DisplayName("项目详情：返回项目 + 轮次 + 规则视图，不存在返回 404")
    void shouldGetEventDetail() throws Exception {
        String token = adminToken();
        String eventName = uniqueName("测试项目详情");
        long eventId = createEvent(token, eventName, "TRACK", "SPRINT", "MALE", 1L, 1);

        JsonNode detail = assertOk(perform(rawGet("/api/events/" + eventId, token, DEMO_MEET_ID)));
        assertThat(detail.path("event").path("eventName").asText()).isEqualTo(eventName);
        assertThat(detail.path("rounds").isArray()).isTrue();
        assertThat(detail.path("ruleConfig").path("admitRank").asInt()).isEqualTo(8);
        assertThat(detail.path("ruleConfig").path("admitRankSource").asText()).isEqualTo("MEET");

        assertBizCode(perform(rawGet("/api/events/999999", token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("项目新增/修改/删除：修改生效，删除后详情 404")
    void shouldCreateUpdateDeleteEvent() throws Exception {
        String token = adminToken();
        String eventName = uniqueName("测试项目增删改");
        long eventId = createEvent(token, eventName, "FIELD", "JUMP", "FEMALE", 2L, 0);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("meetId", DEMO_MEET_ID);
        update.put("eventName", eventName + "改");
        update.put("eventCode", "TE_UPDATE_" + uniqueSuffix());
        update.put("eventType", "FIELD");
        update.put("subType", "JUMP");
        update.put("gender", "FEMALE");
        update.put("groupId", 2);
        update.put("durationMin", 45);
        assertOk(perform(rawPut("/api/events/" + eventId, token, DEMO_MEET_ID, update)));

        JsonNode detail = assertOk(perform(rawGet("/api/events/" + eventId, token, DEMO_MEET_ID)));
        assertThat(detail.path("event").path("eventName").asText()).isEqualTo(eventName + "改");
        assertThat(detail.path("event").path("durationMin").asInt()).isEqualTo(45);

        assertOk(perform(rawDelete("/api/events/" + eventId, token, DEMO_MEET_ID)));
        assertBizCode(perform(rawGet("/api/events/" + eventId, token, DEMO_MEET_ID)), CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("轮次：新增决赛轮次后可查询，非法轮次码返回参数错误")
    void shouldGenerateAndQueryRounds() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目轮次"), "TRACK", "SPRINT", "MALE", 1L, 1);

        long roundId = createFinalRound(token, eventId);
        assertThat(roundId).isPositive();

        JsonNode rounds = assertOk(perform(rawGet("/api/events/" + eventId + "/rounds", token, DEMO_MEET_ID)));
        assertThat(rounds.isArray()).isTrue();
        assertThat(rounds).isNotEmpty();
        boolean hasFinal = false;
        for (JsonNode round : rounds) {
            if ("FINAL".equals(round.path("roundCode").asText())) {
                hasFinal = true;
                assertThat(round.path("id").asLong()).isEqualTo(roundId);
            }
        }
        assertThat(hasFinal).as("新建的决赛轮次应出现在轮次列表中").isTrue();

        // 非法轮次码：@Pattern 校验失败
        Map<String, Object> illegal = new LinkedHashMap<>();
        illegal.put("roundCode", "NOT_A_ROUND");
        assertParamError(perform(rawPost("/api/events/" + eventId + "/rounds", token, DEMO_MEET_ID, illegal)));
    }

    @Test
    @DisplayName("项目级规则：读取继承届次值，覆盖后为项目级，恢复继承后回退届次值")
    void shouldReadWriteAndResetEventRuleConfig() throws Exception {
        String token = adminToken();
        long eventId = createEvent(token, uniqueName("测试项目规则"), "TRACK", "SPRINT", "MALE", 1L, 1);

        // 1. 未配置：继承届次级 admit_rank = 8
        JsonNode inherited = assertOk(perform(rawGet("/api/events/" + eventId + "/rule-config", token, DEMO_MEET_ID)));
        assertThat(inherited.path("admitRank").asInt()).isEqualTo(8);
        assertThat(inherited.path("admitRankSource").asText()).isEqualTo("MEET");

        // 2. 写入项目级覆盖值
        Map<String, Object> save = new LinkedHashMap<>();
        save.put("admitRank", 5);
        save.put("drawMode", "SERPENT");
        save.put("conflictIntervalMin", 45);
        assertOk(perform(rawPut("/api/events/" + eventId + "/rule-config", token, DEMO_MEET_ID, save)));

        JsonNode overridden = assertOk(perform(rawGet("/api/events/" + eventId + "/rule-config", token, DEMO_MEET_ID)));
        assertThat(overridden.path("admitRank").asInt()).isEqualTo(5);
        assertThat(overridden.path("admitRankSource").asText()).isEqualTo("PROJECT");
        assertThat(overridden.path("drawMode").asText()).isEqualTo("SERPENT");
        assertThat(overridden.path("conflictIntervalMin").asInt()).isEqualTo(45);
        assertThat(overridden.path("conflictIntervalMinSource").asText()).isEqualTo("PROJECT");

        // 3. 恢复继承：删除项目级规则行，回退届次级
        assertOk(perform(rawPost("/api/events/" + eventId + "/rule-config/reset", token, DEMO_MEET_ID, null)));

        JsonNode reset = assertOk(perform(rawGet("/api/events/" + eventId + "/rule-config", token, DEMO_MEET_ID)));
        assertThat(reset.path("admitRank").asInt()).isEqualTo(8);
        assertThat(reset.path("admitRankSource").asText()).isEqualTo("MEET");
        assertThat(reset.path("conflictIntervalMin").asInt()).isEqualTo(30);
        assertThat(reset.path("conflictIntervalMinSource").asText()).isEqualTo("MEET");

        // 4. 非法数值：录取名次必须大于 0
        Map<String, Object> illegal = new LinkedHashMap<>();
        illegal.put("admitRank", 0);
        assertBizCode(perform(rawPut("/api/events/" + eventId + "/rule-config", token, DEMO_MEET_ID, illegal)),
                CODE_PARAM_ERROR);
    }

    @Test
    @DisplayName("规则查询：名次分方案与加分规则分页返回")
    void shouldPageScoreAndBonusRules() throws Exception {
        String token = adminToken();

        JsonNode scoreRules = assertPaged(perform(rawGet("/api/score-rules", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID, "page=1", "size=10")));
        assertThat(scoreRules.path("records")).isNotEmpty();

        JsonNode bonusRules = assertPaged(perform(rawGet("/api/bonus-rules", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID, "page=1", "size=10")));
        assertThat(bonusRules.path("records").isArray()).isTrue();

        // 届次规则配置（三层继承的最上层）
        JsonNode meetRule = assertOk(perform(rawGet("/api/meets/" + DEMO_MEET_ID + "/rule-config", token, null)));
        assertThat(meetRule.path("admitRank").asInt()).isEqualTo(8);
    }

    @Test
    @DisplayName("项目参数校验：项目类型非法或名称缺失返回 HTTP 400")
    void shouldRejectInvalidEventParams() throws Exception {
        String token = adminToken();

        Map<String, Object> badType = new LinkedHashMap<>();
        badType.put("meetId", DEMO_MEET_ID);
        badType.put("eventName", uniqueName("测试项目非法类型"));
        badType.put("eventCode", "TE_BAD_" + uniqueSuffix());
        badType.put("eventType", "BAD_TYPE");
        assertParamError(perform(rawPost("/api/events", token, DEMO_MEET_ID, badType)));

        Map<String, Object> noName = new LinkedHashMap<>();
        noName.put("meetId", DEMO_MEET_ID);
        noName.put("eventCode", "TE_NONAME_" + uniqueSuffix());
        noName.put("eventType", "TRACK");
        assertParamError(perform(rawPost("/api/events", token, DEMO_MEET_ID, noName)));
    }

    @Test
    @DisplayName("权限与多租户：裁判无项目新增权限返回 403，其它届次查询为空")
    void shouldEnforcePermissionAndTenant() throws Exception {
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("eventName", uniqueName("测试项目越权"));
        body.put("eventCode", "TE_FORBID_" + uniqueSuffix());
        body.put("eventType", "TRACK");
        assertForbidden(perform(rawPost("/api/events", refereeToken, DEMO_MEET_ID, body)));

        String token = adminToken();
        JsonNode page = assertPaged(perform(rawGet("/api/events", token, ABSENT_MEET_ID, "page=1", "size=10")));
        assertThat(page.path("records")).isEmpty();

        assertUnauthorized(perform(rawGet("/api/events", null, DEMO_MEET_ID, "page=1", "size=10")));
    }
}
