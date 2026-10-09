package com.trackmeet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackmeet.api.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 公告与大屏接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}：演示届次 id=1。
 * 大屏配置由 {@code GET /api/screen/config} 在缺失时自动创建并生成 32 位访问令牌。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>公告：{@code GET /api/announcements}、{@code GET /api/announcements/{id}}、
 *         {@code POST /api/announcements}、{@code PUT /api/announcements/{id}}、
 *         {@code DELETE /api/announcements/{id}}、{@code PUT /api/announcements/{id}/publish}（发布/下线）</li>
 *     <li>大屏：{@code GET /api/screen/config}（返回访问令牌）、{@code GET /api/screen/board}（登录态，X-Meet-Id）、
 *         {@code GET /api/screen/public/board}（匿名，令牌校验）</li>
 *     <li>正文安全：注入 {@code <script>} 的富文本经服务端 {@code HtmlSanitizer} 净化后返回不含脚本标签</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * @author TrackMeet
 */
class AnnouncementScreenApiIT extends ApiTestBase {

    @Test
    @DisplayName("公告 CRUD：新增、分页、详情、修改")
    void shouldManageAnnouncements() throws Exception {
        String token = adminToken();
        String title = uniqueName("测试公告");
        long id = createAnnouncement(token, title, "<p>公告正文</p>");
        assertThat(id).isPositive();

        JsonNode page = assertPaged(perform(rawGet("/api/announcements", token, DEMO_MEET_ID,
                "page=1", "size=10")));
        assertThat(page.path("records").isArray()).isTrue();

        JsonNode detail = assertOk(perform(rawGet("/api/announcements/" + id, token, DEMO_MEET_ID)));
        assertThat(detail.path("title").asText()).isEqualTo(title);
        assertThat(detail.path("status").asText()).isEqualTo("DRAFT");

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("meetId", DEMO_MEET_ID);
        update.put("title", title + "改");
        update.put("contentHtml", "<p>公告正文已修改</p>");
        update.put("noticeType", "NOTICE");
        update.put("scope", "PUBLIC");
        assertOk(perform(rawPut("/api/announcements/" + id, token, DEMO_MEET_ID, update)));

        JsonNode updated = assertOk(perform(rawGet("/api/announcements/" + id, token, DEMO_MEET_ID)));
        assertThat(updated.path("title").asText()).isEqualTo(title + "改");
    }

    @Test
    @DisplayName("公告发布与下线：状态按 PUBLISHED → OFFLINE 流转")
    void shouldPublishAndOfflineAnnouncement() throws Exception {
        String token = adminToken();
        long id = createAnnouncement(token, uniqueName("测试公告发布"), "<p>待发布正文</p>");

        Map<String, Object> publish = new LinkedHashMap<>();
        publish.put("status", "PUBLISHED");
        publish.put("screenShow", 1);
        JsonNode published = assertOk(perform(rawPut("/api/announcements/" + id + "/publish", token, DEMO_MEET_ID,
                publish)));
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.path("publishTime").asText()).isNotBlank();

        Map<String, Object> offline = new LinkedHashMap<>();
        offline.put("status", "OFFLINE");
        JsonNode offlined = assertOk(perform(rawPut("/api/announcements/" + id + "/publish", token, DEMO_MEET_ID,
                offline)));
        assertThat(offlined.path("status").asText()).isEqualTo("OFFLINE");

        // 目标状态缺失 / 非法：参数校验与业务校验
        assertParamError(perform(rawPut("/api/announcements/" + id + "/publish", token, DEMO_MEET_ID,
                Map.of("status", ""))));
        assertBizCode(perform(rawPut("/api/announcements/" + id + "/publish", token, DEMO_MEET_ID,
                Map.of("status", "NOT_A_STATUS"))), CODE_PARAM_ERROR);
    }

    @Test
    @DisplayName("公告删除与参数校验：草稿可删、删除后 404，标题缺失返回 400")
    void shouldDeleteAnnouncementAndValidateParams() throws Exception {
        String token = adminToken();
        long id = createAnnouncement(token, uniqueName("测试公告删除"), "<p>待删除正文</p>");

        assertOk(perform(rawDelete("/api/announcements/" + id, token, DEMO_MEET_ID)));
        assertBizCode(perform(rawGet("/api/announcements/" + id, token, DEMO_MEET_ID)), CODE_NOT_FOUND);

        Map<String, Object> blankTitle = new LinkedHashMap<>();
        blankTitle.put("meetId", DEMO_MEET_ID);
        blankTitle.put("title", "");
        assertParamError(perform(rawPost("/api/announcements", token, DEMO_MEET_ID, blankTitle)));
    }

    @Test
    @DisplayName("公告正文安全：注入 <script> 的富文本经净化后返回内容不含 script 标签")
    void shouldSanitizeAnnouncementHtml() throws Exception {
        String token = adminToken();
        String dirty = "<p>正常正文</p><script>alert('xss')</script><img src=x onerror=alert(1)>";
        long id = createAnnouncement(token, uniqueName("测试公告净化"), dirty);

        JsonNode detail = assertOk(perform(rawGet("/api/announcements/" + id, token, DEMO_MEET_ID)));
        String contentHtml = detail.path("contentHtml").asText();
        assertThat(contentHtml.toLowerCase()).doesNotContain("<script");
        assertThat(contentHtml.toLowerCase()).doesNotContain("onerror");
        assertThat(contentHtml).contains("正常正文");
    }

    @Test
    @DisplayName("大屏配置：返回访问令牌与配置项")
    void shouldGetScreenConfigWithToken() throws Exception {
        String token = adminToken();

        JsonNode config = assertOk(perform(rawGet("/api/screen/config", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID)));
        assertThat(config.path("meetId").asLong()).isEqualTo(DEMO_MEET_ID);
        assertThat(config.path("accessToken").asText()).isNotBlank();
        assertThat(config.path("refreshInterval").asInt(-1)).isPositive();
    }

    @Test
    @DisplayName("登录态大屏：带 X-Meet-Id 返回聚合数据")
    void shouldGetScreenBoard() throws Exception {
        String token = adminToken();

        JsonNode board = assertOk(perform(rawGet("/api/screen/board", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID)));
        assertThat(board.isObject()).isTrue();
        assertThat(board.path("meetId").asLong()).isEqualTo(DEMO_MEET_ID);
        assertThat(board.path("config").isObject()).isTrue();

        // meetId 缺失：@NotNull 参数校验
        assertParamError(perform(rawGet("/api/screen/board", token, DEMO_MEET_ID)));
        // 届次上下文与参数不一致：越权
        assertBizCode(perform(rawGet("/api/screen/board", token, ABSENT_MEET_ID, "meetId=" + DEMO_MEET_ID)),
                CODE_FORBIDDEN);
    }

    @Test
    @DisplayName("匿名大屏：正确令牌可匿名取数，错误令牌返回 403")
    void shouldAllowAnonymousBoardWithValidToken() throws Exception {
        String token = adminToken();
        JsonNode config = assertOk(perform(rawGet("/api/screen/config", token, DEMO_MEET_ID,
                "meetId=" + DEMO_MEET_ID)));
        String accessToken = config.path("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        // 不带任何鉴权头，仅带 meetId + token
        JsonNode board = assertOk(perform(rawGet("/api/screen/public/board", null, null,
                "meetId=" + DEMO_MEET_ID, "token=" + accessToken)));
        assertThat(board.isObject()).isTrue();
        assertThat(board.path("meetId").asLong()).isEqualTo(DEMO_MEET_ID);

        // 错误令牌：业务码 403（匿名路径已放行，由服务层校验令牌）
        assertBizCode(perform(rawGet("/api/screen/public/board", null, null,
                "meetId=" + DEMO_MEET_ID, "token=invalid-token-0000")), CODE_FORBIDDEN);

        // 缺少令牌参数：ScreenQuery.token 无 @NotNull，服务层按令牌不匹配处理（400 或 403 均属预期）
        assertBizCodeIn(perform(rawGet("/api/screen/public/board", null, null, "meetId=" + DEMO_MEET_ID)),
                CODE_PARAM_ERROR, CODE_FORBIDDEN);
    }

    @Test
    @DisplayName("权限与多租户：裁判无公告新增权限返回 403，其它届次公告列表为空")
    void shouldEnforcePermissionAndTenant() throws Exception {
        // 裁判（REFEREE）只有 notice:list，没有 notice:add
        String refereeToken = assumeLogin(REFEREE_USERNAME);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("title", uniqueName("测试公告越权"));
        body.put("contentHtml", "<p>越权</p>");
        assertForbidden(perform(rawPost("/api/announcements", refereeToken, DEMO_MEET_ID, body)));

        String token = adminToken();
        JsonNode page = assertPaged(perform(rawGet("/api/announcements", token, ABSENT_MEET_ID,
                "page=1", "size=10")));
        assertThat(page.path("records")).isEmpty();

        // 公告列表需要登录态
        assertUnauthorized(perform(rawGet("/api/announcements", null, DEMO_MEET_ID, "page=1", "size=10")));
        // 大屏配置同样需要登录态（仅 /api/screen/public/** 匿名放行）
        assertUnauthorized(perform(rawGet("/api/screen/config", null, DEMO_MEET_ID, "meetId=" + DEMO_MEET_ID)));
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    private long createAnnouncement(String token, String title, String contentHtml) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("title", title);
        body.put("contentHtml", contentHtml);
        body.put("noticeType", "NOTICE");
        body.put("scope", "PUBLIC");
        body.put("isTop", 0);
        JsonNode data = assertOk(perform(rawPost("/api/announcements", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }
}
