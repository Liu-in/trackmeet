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
 * 积分榜、奖牌榜、积分明细与快照接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}：演示届次 id=1、
 * 单位 id=1..4、组别 id=1..4、名次分方案（单项前八名 9/7/6/5/4/3/2/1）与届次级规则。
 * 榜单来源于「已审核成绩」，因此本类会自行创建
 * 「竞赛单元 + 项目 + 轮次 + 已通过报名 + 日程 + 成绩 + 审核」链路（数据命名随机，保证幂等）。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>{@code POST /api/standings/recalculate}：重算积分与榜单（幂等）</li>
 *     <li>{@code GET /api/standings/team}：团体总分（{@code scoreType=TOTAL}）</li>
 *     <li>{@code GET /api/standings/medals}：奖牌榜</li>
 *     <li>{@code GET /api/standings/points}：积分明细分页</li>
 *     <li>{@code POST /api/standings/snapshot}：榜单快照</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class StandingApiIT extends ApiTestBase {

    @Test
    @DisplayName("重算积分：重算成功且团体总分（TOTAL）非空")
    void shouldRecalculateAndListTeamScores() throws Exception {
        String token = adminToken();
        prepareScoredResults(token);

        JsonNode overview = assertOk(perform(rawPost("/api/standings/recalculate", token, DEMO_MEET_ID,
                Map.of("meetId", DEMO_MEET_ID))));
        assertThat(overview.isObject()).isTrue();
        assertThat(overview.path("meetId").asLong()).isEqualTo(DEMO_MEET_ID);

        JsonNode team = assertOk(perform(rawGet("/api/standings/team", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID, "scoreType=TOTAL")));
        assertThat(team.isArray()).isTrue();
        assertThat(team).as("重算后团体总分榜不应为空").isNotEmpty();
        assertThat(team.get(0).path("unitId").asLong()).isPositive();
    }

    @Test
    @DisplayName("奖牌榜：重算后返回奖牌统计数组")
    void shouldListMedals() throws Exception {
        String token = adminToken();
        prepareScoredResults(token);

        assertOk(perform(rawPost("/api/standings/recalculate", token, DEMO_MEET_ID,
                Map.of("meetId", DEMO_MEET_ID))));

        JsonNode medals = assertOk(perform(rawGet("/api/standings/medals", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID)));
        assertThat(medals.isArray()).isTrue();
        assertThat(medals).as("重算后奖牌榜不应为空").isNotEmpty();
        assertThat(medals.get(0).path("unitId").asLong()).isPositive();
    }

    @Test
    @DisplayName("积分明细：分页结构正确")
    void shouldPagePoints() throws Exception {
        String token = adminToken();
        prepareScoredResults(token);
        assertOk(perform(rawPost("/api/standings/recalculate", token, DEMO_MEET_ID,
                Map.of("meetId", DEMO_MEET_ID))));

        JsonNode page = assertPaged(perform(rawGet("/api/standings/points", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID, "page=1", "size=10")));
        assertThat(page.path("records").isArray()).isTrue();
    }

    @Test
    @DisplayName("榜单快照：重算后保存快照成功并返回快照统计")
    void shouldSaveSnapshot() throws Exception {
        String token = adminToken();
        // 快照不重算分值，只刷新 calc_time / calc_batch；无榜单数据时服务层返回 CONFLICT（409），
        // 因此这里先构造已审核成绩并重算，保证快照有数据可存。
        prepareScoredResults(token);
        assertOk(perform(rawPost("/api/standings/recalculate", token, DEMO_MEET_ID,
                Map.of("meetId", DEMO_MEET_ID))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("scoreType", "TOTAL");
        JsonNode overview = assertOk(perform(rawPost("/api/standings/snapshot", token, DEMO_MEET_ID, body)));
        assertThat(overview.isObject()).isTrue();
        assertThat(overview.path("meetId").asLong()).isEqualTo(DEMO_MEET_ID);
    }

    @Test
    @DisplayName("参数校验：缺少届次参数返回 HTTP 400 与参数错误码")
    void shouldValidateStandingParams() throws Exception {
        String token = adminToken();

        // 重算缺少 meetId（@NotNull）
        assertParamError(perform(rawPost("/api/standings/recalculate", token, DEMO_MEET_ID, Map.of())));
        // 团体总分查询缺少 meetId（@NotNull）
        assertParamError(perform(rawGet("/api/standings/team", token, DEMO_MEET_ID)));
        // 快照缺少 meetId（@NotNull）
        assertParamError(perform(rawPost("/api/standings/snapshot", token, DEMO_MEET_ID, Map.of())));
    }

    @Test
    @DisplayName("权限与多租户：裁判无重算权限返回 403，届次上下文与参数不一致返回 403")
    void shouldEnforcePermissionAndTenant() throws Exception {
        // 裁判（REFEREE）只有 standing:medal，没有 standing:recalc / standing:points
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        assertForbidden(perform(rawPost("/api/standings/recalculate", refereeToken, DEMO_MEET_ID,
                Map.of("meetId", DEMO_MEET_ID))));
        assertForbidden(perform(rawGet("/api/standings/team", refereeToken, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID, "scoreType=TOTAL")));

        // 多租户：X-Meet-Id 与请求参数 meetId 不一致时按越权处理
        String token = adminToken();
        MvcResult mismatch = perform(rawGet("/api/standings/team", token, DEMO_MEET_ID,
                "meetId=" + ABSENT_MEET_ID, "scoreType=TOTAL"));
        assertBizCode(mismatch, CODE_FORBIDDEN);

        // 一致的其它届次：查询成功但榜单为空（或该届次不存在）
        MvcResult absent = perform(rawGet("/api/standings/team", token, ABSENT_MEET_ID,
                "meetId=" + ABSENT_MEET_ID, "scoreType=TOTAL"));
        assertBizCodeIn(absent, CODE_SUCCESS, CODE_NOT_FOUND);

        assertUnauthorized(perform(rawGet("/api/standings/team", null, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID, "scoreType=TOTAL")));
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    /**
     * 构造一组「已审核并有名次」的成绩，使重算后榜单必然非空。
     */
    private void prepareScoredResults(String token) throws Exception {
        long sessionId = createUniqueSession(token);
        long eventId = createSchedulableEvent(token, uniqueName("测试项目积分"), 2);
        long roundId = firstRoundId(token, eventId);
        long scheduleId = createScheduleEntry(token, sessionId, eventId, roundId, "2042-06-01 09:00:00");

        List<Long> registrationIds = new ArrayList<>();
        for (JsonNode record : registrationsOfEvent(token, eventId).path("records")) {
            registrationIds.add(record.path("id").asLong());
        }
        assertThat(registrationIds).hasSize(2);

        String[] marks = {"11\"12", "11\"58"};
        List<Long> resultIds = new ArrayList<>();
        for (int index = 0; index < registrationIds.size(); index++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("meetId", DEMO_MEET_ID);
            payload.put("scheduleId", scheduleId);
            payload.put("eventId", eventId);
            payload.put("roundId", roundId);
            payload.put("registrationId", registrationIds.get(index));
            payload.put("resultRaw", marks[index]);
            payload.put("resultUnit", "s");
            resultIds.add(assertOk(perform(rawPost("/api/results", token, DEMO_MEET_ID, payload))).asLong());
        }

        // 审核通过后自动生成名次，重算积分才有数据来源
        Map<String, Object> approve = new LinkedHashMap<>();
        approve.put("ids", resultIds);
        assertOk(perform(rawPost("/api/results/approve", token, DEMO_MEET_ID, approve)));
    }
}
