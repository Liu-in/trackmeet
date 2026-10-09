package com.trackmeet.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.trackmeet.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 后端接口集成测试公共基类。
 *
 * <h3>前置条件</h3>
 * <p>需要一套可用的 MySQL 8 测试库 {@code trackmeet_test}，并按序执行
 * {@code sql/01-schema-system.sql} ~ {@code sql/06-demo-data.sql}
 * （或 {@code cd deploy && docker compose up -d mysql} 后在其中执行同一批脚本）。
 * 数据库连接参数见 {@code src/test/resources/application-test.yml}，
 * 可用环境变量 {@code TEST_DB_URL} / {@code TEST_DB_USERNAME} / {@code TEST_DB_PASSWORD} 覆盖。</p>
 *
 * <h3>无数据库时的行为</h3>
 * <p>{@code spring.datasource.hikari.initialization-fail-timeout=-1} 保证即使连不上库
 * Spring 上下文也能启动；每个测试方法执行前由 {@link #assumeEnvironmentReady()} 检查
 * “能否连接 + {@code sys_user} 是否有数据 + 是否存在演示届次 {@code meet.id=1} + admin 能否登录”，
 * 任一不满足即 {@link org.junit.jupiter.api.Assumptions#assumeTrue} 中止，用例被标记为
 * <b>SKIPPED</b> 而非 FAILED，避免 CI 误报（探测结论在 JVM 内缓存，仅首次真实探测）。</p>
 *
 * <h3>约定</h3>
 * <ul>
 *     <li>统一返回体 {@code R{code,message,data}}：{@code code == 0} 为成功；</li>
 *     <li>鉴权：{@code Authorization: Bearer <accessToken>}；</li>
 *     <li>多租户：请求头 {@code X-Meet-Id}（缺省时基类不注入，接口自行取默认值）；</li>
 *     <li>测试数据全部使用时间戳 + 随机数后缀，用例之间互不依赖执行顺序（幂等）。</li>
 * </ul>
 *
 * <p><b>注意</b>：登录口令一旦被成功修改（{@code POST /api/auth/change-password}）会使
 * 所有已缓存令牌因 {@code token_version} 自增而立即失效，因此集成测试请勿真正修改
 * 内置账号口令，仅覆盖“参数校验”类用例；需要验证登出失效请使用
 * {@link #loginFresh(String, String)} 单独申请令牌。</p>
 *
 * @author TrackMeet
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    /** 内置超级管理员账号 */
    protected static final String ADMIN_USERNAME = "admin";
    /** 内置账号默认口令（见 trackmeet.default-password） */
    protected static final String ADMIN_PASSWORD = "Admin@123";
    /** 演示账号：编排员（06-demo-data.sql，角色 SCHEDULER） */
    protected static final String SCHEDULER_USERNAME = "scheduler";
    /** 演示账号：裁判（06-demo-data.sql，角色 REFEREE） */
    protected static final String REFEREE_USERNAME = "referee";
    /** 演示账号：领队（06-demo-data.sql，角色 TEAM_LEADER） */
    protected static final String LEADER_USERNAME = "leader1";

    /** 鉴权请求头 */
    protected static final String AUTH_HEADER = "Authorization";
    /** 令牌前缀 */
    protected static final String TOKEN_PREFIX = "Bearer ";
    /** 届次上下文请求头 */
    protected static final String MEET_HEADER = "X-Meet-Id";
    /** 演示运动会 ID（06-demo-data.sql） */
    protected static final long DEMO_MEET_ID = 1L;
    /** 演示运动会中不存在的届次 ID，用于多租户隔离断言 */
    protected static final long ABSENT_MEET_ID = 999999L;

    /** 业务成功码 */
    protected static final int CODE_SUCCESS = ResultCode.SUCCESS.getCode();
    /** 参数校验失败码 */
    protected static final int CODE_PARAM_ERROR = ResultCode.PARAM_ERROR.getCode();
    /** 未登录或令牌失效码 */
    protected static final int CODE_UNAUTHORIZED = ResultCode.UNAUTHORIZED.getCode();
    /** 无权限码 */
    protected static final int CODE_FORBIDDEN = ResultCode.FORBIDDEN.getCode();
    /** 资源不存在码 */
    protected static final int CODE_NOT_FOUND = ResultCode.NOT_FOUND.getCode();
    /** 数据冲突码 */
    protected static final int CODE_CONFLICT = ResultCode.CONFLICT.getCode();

    /** 令牌缓存：username + '\u0000' + password -> accessToken */
    private static final Map<String, String> ACCESS_TOKEN_CACHE = new ConcurrentHashMap<>();
    /** 刷新令牌缓存 */
    private static final Map<String, String> REFRESH_TOKEN_CACHE = new ConcurrentHashMap<>();
    /** 测试库就绪探测结果缓存（同一 JVM 只探测一次，避免无库时每个用例都等待连接超时） */
    private static volatile Boolean databaseReady;
    /** admin 可登录探测结果缓存 */
    private static volatile Boolean adminLoginReady;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected DataSource dataSource;

    /**
     * 环境就绪检查：测试库不可用或未初始化时跳过（而非失败）。
     * <p>声明为 {@code protected}（而非包私有）以确保位于其它包中的子类也能被 JUnit 识别并继承。</p>
     */
    @BeforeEach
    protected void assumeEnvironmentReady() {
        assumeTrue(isDatabaseReady(),
                () -> "跳过：测试库未就绪，无法连接 " + describeDataSource()
                        + "，或 sys_user 为空，或缺少演示届次 meet.id=1。"
                        + "请先按序执行 sql/01~06（或 cd deploy && docker compose up -d mysql）。");
        assumeTrue(isAdminLoginReady(),
                () -> "跳过：admin 账号无法登录（口令已修改 / 账号锁定 / 未执行 sql/05-init-data.sql）。");
    }

    // ------------------------------------------------------------------
    // 环境探测
    // ------------------------------------------------------------------

    /**
     * 数据源是否可用且已初始化；结果在 JVM 内缓存，仅首次真实探测。
     * <p>就绪判定 = 能连接 + {@code sys_user} 非空 + 存在演示届次（{@code meet.id = 1}，
     * 由 {@code sql/06-demo-data.sql} 提供）。三者缺一即整类跳过，避免「库已建但未导入数据」时误报失败。</p>
     */
    protected boolean isDatabaseReady() {
        Boolean cached = databaseReady;
        if (cached != null) {
            return cached;
        }
        boolean ready = probeDatabase();
        databaseReady = ready;
        return ready;
    }

    /**
     * admin 是否可用默认口令登录；结果在 JVM 内缓存。
     */
    protected boolean isAdminLoginReady() {
        Boolean cached = adminLoginReady;
        if (cached != null) {
            return cached;
        }
        boolean ready = login(ADMIN_USERNAME, ADMIN_PASSWORD) != null;
        adminLoginReady = ready;
        return ready;
    }

    private boolean probeDatabase() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet users = statement.executeQuery("SELECT COUNT(*) FROM sys_user")) {
                if (!users.next() || users.getLong(1) <= 0L) {
                    return false;
                }
            }
            try (ResultSet meets = statement.executeQuery("SELECT COUNT(*) FROM meet WHERE id = 1")) {
                return meets.next() && meets.getLong(1) > 0L;
            }
        } catch (Exception ex) {
            return false;
        }
    }

    private String describeDataSource() {
        return "配置的数据源（见 src/test/resources/application-test.yml）";
    }

    // ------------------------------------------------------------------
    // 登录与令牌
    // ------------------------------------------------------------------

    /**
     * 登录并缓存访问令牌；失败返回 {@code null}（不抛异常，便于调用方 decide 跳过）。
     *
     * @param username 用户名
     * @param password 口令
     * @return 访问令牌，失败返回 null
     */
    protected String login(String username, String password) {
        String cacheKey = cacheKey(username, password);
        String cached = ACCESS_TOKEN_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        JsonNode body = doLogin(username, password);
        if (body == null || body.path("code").asInt(-1) != CODE_SUCCESS) {
            return null;
        }
        String accessToken = body.path("data").path("accessToken").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        ACCESS_TOKEN_CACHE.put(cacheKey, accessToken);
        String refreshToken = body.path("data").path("refreshToken").asText(null);
        if (refreshToken != null && !refreshToken.isBlank()) {
            REFRESH_TOKEN_CACHE.put(cacheKey, refreshToken);
        }
        return accessToken;
    }

    /**
     * 每次都真实调用登录接口（不使用缓存），用于登出失效等需要独立令牌的用例。
     *
     * @param username 用户名
     * @param password 口令
     * @return 登录返回体（可能为 null 表示请求本身失败）
     */
    protected JsonNode loginFresh(String username, String password) {
        return doLogin(username, password);
    }

    /**
     * 超级管理员访问令牌（不可用时跳过用例）。
     */
    protected String adminToken() {
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        assumeTrue(token != null, () -> "跳过：admin 登录失败，请确认测试库已执行 sql/05-init-data.sql 且口令为默认值。");
        return token;
    }

    /**
     * 指定演示账号的访问令牌；账号不存在（未导入 06-demo-data.sql）时跳过用例。
     *
     * @param username 演示账号用户名，如 referee / scheduler / leader1
     * @return 访问令牌
     */
    protected String assumeLogin(String username) {
        return assumeLogin(username, ADMIN_PASSWORD);
    }

    /**
     * 指定账号的访问令牌；不可用时跳过用例。
     *
     * @param username 用户名
     * @param password 口令
     * @return 访问令牌
     */
    protected String assumeLogin(String username, String password) {
        String token = login(username, password);
        assumeTrue(token != null,
                () -> "跳过：账号 " + username + " 不可用，请确认测试库已执行 sql/06-demo-data.sql。");
        return token;
    }

    /**
     * 缓存的刷新令牌；不存在时返回 null。
     */
    protected String cachedRefreshToken(String username, String password) {
        return REFRESH_TOKEN_CACHE.get(cacheKey(username, password));
    }

    private JsonNode doLogin(String username, String password) {
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("username", username, "password", password));
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andReturn();
            return body(result);
        } catch (Exception ex) {
            return null;
        }
    }

    private String cacheKey(String username, String password) {
        return username + '\u0000' + password;
    }

    // ------------------------------------------------------------------
    // 请求构造：raw（可继续追加断言）/ json（直接解析为 JsonNode）
    // ------------------------------------------------------------------

    /**
     * GET 请求构造器。
     *
     * @param path   路径（含前导 /）
     * @param token  访问令牌，null 表示匿名请求
     * @param meetId 届次 ID（X-Meet-Id），null 表示不注入
     * @param params 查询参数，形如 {@code "page=1", "size=10"}
     */
    protected MockHttpServletRequestBuilder rawGet(String path, String token, Long meetId, String... params) {
        // 注意：不设置 Accept 头，避免 PDF / Excel 等接口因内容协商返回 406
        MockHttpServletRequestBuilder builder = get(path);
        return decorate(applyParams(builder, params), token, meetId);
    }

    /**
     * POST JSON 请求构造器。
     */
    protected MockHttpServletRequestBuilder rawPost(String path, String token, Long meetId, Object payload) {
        MockHttpServletRequestBuilder builder = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(payload));
        return decorate(builder, token, meetId);
    }

    /**
     * PUT JSON 请求构造器。
     */
    protected MockHttpServletRequestBuilder rawPut(String path, String token, Long meetId, Object payload) {
        MockHttpServletRequestBuilder builder = put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(payload));
        return decorate(builder, token, meetId);
    }

    /**
     * DELETE 请求构造器。
     */
    protected MockHttpServletRequestBuilder rawDelete(String path, String token, Long meetId) {
        return decorate(delete(path), token, meetId);
    }

    /**
     * 执行 GET 并解析为 JsonNode。
     */
    protected JsonNode getJson(String path, String token, Long meetId, String... params) throws Exception {
        return body(perform(rawGet(path, token, meetId, params)));
    }

    /**
     * 执行 POST（JSON 体）并解析为 JsonNode。
     */
    protected JsonNode postJson(String path, String token, Long meetId, Object payload) throws Exception {
        return body(perform(rawPost(path, token, meetId, payload)));
    }

    /**
     * 执行 PUT（JSON 体）并解析为 JsonNode。
     */
    protected JsonNode putJson(String path, String token, Long meetId, Object payload) throws Exception {
        return body(perform(rawPut(path, token, meetId, payload)));
    }

    /**
     * 执行 DELETE 并解析为 JsonNode。
     */
    protected JsonNode deleteJson(String path, String token, Long meetId) throws Exception {
        return body(perform(rawDelete(path, token, meetId)));
    }

    /**
     * 执行请求，返回 MvcResult（用于文件流、Content-Type 等断言）。
     */
    protected MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn();
    }

    /**
     * 请求体解析为 JsonNode；空响应返回空对象，避免 NPE。
     */
    protected JsonNode body(MvcResult result) throws Exception {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        if (bytes == null || bytes.length == 0) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 序列化任意对象为 JSON 字符串（null 转为 {@code {}}）。
     */
    protected String toJson(Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("测试数据无法序列化为 JSON: " + payload, ex);
        }
    }

    private MockHttpServletRequestBuilder applyParams(MockHttpServletRequestBuilder builder, String... params) {
        if (params == null) {
            return builder;
        }
        for (String param : params) {
            if (param == null) {
                continue;
            }
            int index = param.indexOf('=');
            if (index > 0) {
                builder.param(param.substring(0, index), param.substring(index + 1));
            }
        }
        return builder;
    }

    private MockHttpServletRequestBuilder decorate(MockHttpServletRequestBuilder builder, String token, Long meetId) {
        if (token != null && !token.isBlank()) {
            builder.header(AUTH_HEADER, TOKEN_PREFIX + token);
        }
        if (meetId != null) {
            builder.header(MEET_HEADER, String.valueOf(meetId));
        }
        return builder;
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    /**
     * 断言 HTTP 200 且 {@code code == 0}，返回 {@code data} 节点（可能为 MissingNode）。
     */
    protected JsonNode assertOk(MvcResult result) throws Exception {
        JsonNode json = body(result);
        assertThat(result.getResponse().getStatus())
                .as("期望 HTTP 200，实际 %s，响应体=%s", result.getResponse().getStatus(), json)
                .isEqualTo(200);
        assertThat(json.path("code").asInt(-1))
                .as("期望业务码 0，响应体=%s", json)
                .isEqualTo(CODE_SUCCESS);
        JsonNode data = json.path("data");
        return data == null ? MissingNode.getInstance() : data;
    }

    /**
     * 断言 Http 200 且业务成功，返回整个响应体。
     */
    protected JsonNode assertOkBody(MvcResult result) throws Exception {
        assertOk(result);
        return body(result);
    }

    /**
     * 断言分页结构：{@code data.records} 为数组且含 total/page/size，返回 data 节点。
     */
    protected JsonNode assertPaged(MvcResult result) throws Exception {
        JsonNode data = assertOk(result);
        assertThat(data.path("records").isArray())
                .as("期望分页结构 data.records 为数组，实际 data=%s", data)
                .isTrue();
        assertThat(data.hasNonNull("total")).as("分页结果应包含 total，实际 data=%s", data).isTrue();
        assertThat(data.hasNonNull("page")).as("分页结果应包含 page，实际 data=%s", data).isTrue();
        assertThat(data.hasNonNull("size")).as("分页结果应包含 size，实际 data=%s", data).isTrue();
        return data;
    }

    /**
     * 断言 HTTP 403 且业务码为 403（无权限）。
     */
    protected void assertForbidden(MvcResult result) throws Exception {
        JsonNode json = body(result);
        assertThat(result.getResponse().getStatus())
                .as("期望 HTTP 403，实际 %s，响应体=%s", result.getResponse().getStatus(), json)
                .isEqualTo(403);
        assertThat(json.path("code").asInt(-1))
                .as("期望业务码 403，响应体=%s", json)
                .isEqualTo(CODE_FORBIDDEN);
    }

    /**
     * 断言 HTTP 401 且业务码为 401（未登录 / 令牌失效）。
     */
    protected void assertUnauthorized(MvcResult result) throws Exception {
        JsonNode json = body(result);
        assertThat(result.getResponse().getStatus())
                .as("期望 HTTP 401，实际 %s，响应体=%s", result.getResponse().getStatus(), json)
                .isEqualTo(401);
        assertThat(json.path("code").asInt(-1))
                .as("期望业务码 401，响应体=%s", json)
                .isEqualTo(CODE_UNAUTHORIZED);
    }

    /**
     * 断言 HTTP 400 且业务码为 400（参数校验失败）。
     */
    protected void assertParamError(MvcResult result) throws Exception {
        JsonNode json = body(result);
        assertThat(result.getResponse().getStatus())
                .as("期望 HTTP 400，实际 %s，响应体=%s", result.getResponse().getStatus(), json)
                .isEqualTo(400);
        assertThat(json.path("code").asInt(-1))
                .as("期望业务码 400，响应体=%s", json)
                .isEqualTo(CODE_PARAM_ERROR);
    }

    /**
     * 断言业务码等于给定值（HTTP 状态不限制，业务异常通常为 200）。
     */
    protected void assertBizCode(MvcResult result, int expectedCode) throws Exception {
        JsonNode json = body(result);
        assertThat(json.path("code").asInt(-1))
                .as("期望业务码 %s，实际响应体=%s", expectedCode, json)
                .isEqualTo(expectedCode);
    }

    /**
     * 断言响应体中的业务码属于给定集合之一（用于“404 或 400”这类兼容断言）。
     */
    protected void assertBizCodeIn(MvcResult result, int... expectedCodes) throws Exception {
        JsonNode json = body(result);
        int actual = json.path("code").asInt(-1);
        assertThat(expectedCodes)
                .as("期望业务码属于给定集合，实际 %s，响应体=%s", actual, json)
                .contains(actual);
    }

    // ------------------------------------------------------------------
    // 断言重试（替代长时间 Thread.sleep）
    // ------------------------------------------------------------------

    /** 断言重试次数上限 */
    protected static final int ASSERT_RETRY_TIMES = 3;
    /** 断言重试间隔（毫秒） */
    protected static final long ASSERT_RETRY_INTERVAL_MILLIS = 200L;

    /**
     * 带重试的 GET：最多请求 {@value #ASSERT_RETRY_TIMES} 次，每次间隔
     * {@value #ASSERT_RETRY_INTERVAL_MILLIS} 毫秒，直到响应体满足条件或重试耗尽
     * （耗尽时返回最后一次响应，由调用方断言，从而给出清晰的失败信息）。
     *
     * <p>用于异步落库（如自动编排、名次生成）后的最终一致性断言，禁止使用长时间 sleep。</p>
     *
     * @param path      请求路径
     * @param token     访问令牌
     * @param meetId    届次 ID
     * @param predicate 期望条件
     * @param params    查询参数
     * @return 最后一次响应体
     */
    protected JsonNode retryGet(String path, String token, Long meetId,
                                java.util.function.Predicate<JsonNode> predicate, String... params) throws Exception {
        JsonNode last = null;
        for (int attempt = 1; attempt <= ASSERT_RETRY_TIMES; attempt++) {
            last = getJson(path, token, meetId, params);
            if (predicate.test(last)) {
                return last;
            }
            if (attempt < ASSERT_RETRY_TIMES) {
                Thread.sleep(ASSERT_RETRY_INTERVAL_MILLIS);
            }
        }
        return last;
    }

    /**
     * 带重试的 POST（见 {@link #retryGet}）。
     */
    protected JsonNode retryPost(String path, String token, Long meetId, Object payload,
                                 java.util.function.Predicate<JsonNode> predicate) throws Exception {
        JsonNode last = null;
        for (int attempt = 1; attempt <= ASSERT_RETRY_TIMES; attempt++) {
            last = postJson(path, token, meetId, payload);
            if (predicate.test(last)) {
                return last;
            }
            if (attempt < ASSERT_RETRY_TIMES) {
                Thread.sleep(ASSERT_RETRY_INTERVAL_MILLIS);
            }
        }
        return last;
    }

    // ------------------------------------------------------------------
    // 测试数据命名（幂等：时间戳 + 随机后缀）
    // ------------------------------------------------------------------

    /**
     * 生成带时间戳与随机后缀的唯一名称，如 {@code 测试单位_1730123456789_4821}。
     *
     * @param prefix 业务前缀，如「测试单位」
     * @return 唯一名称
     */
    protected static String uniqueName(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /**
     * 生成短唯一后缀（4 位随机 + 自增基数），用于编码类字段。
     */
    protected static String uniqueSuffix() {
        return String.valueOf(System.currentTimeMillis() % 100000) + ThreadLocalRandom.current().nextInt(100, 999);
    }

    // ------------------------------------------------------------------
    // 领域夹具（多个集成测试类共用；均使用唯一命名，保证幂等）
    // ------------------------------------------------------------------

    /**
     * 在演示届次（{@link #DEMO_MEET_ID}）下新增一个项目。
     *
     * @param token          访问令牌（需具备 event:add 权限）
     * @param eventName      项目名称（建议用 {@link #uniqueName(String)} 生成）
     * @param eventType      TRACK / FIELD / ALLROUND / RELAY
     * @param subType        SPRINT / MIDDLE / LONG / HURDLE / JUMP / THROW / RELAY 等，可为 null
     * @param gender         MALE / FEMALE / MIXED，可为 null
     * @param groupId        所属组别（演示数据为 1..4）
     * @param windApplicable 1 记录风速 / 0 不记录
     * @return 新增项目的 ID
     */
    protected long createEvent(String token, String eventName, String eventType, String subType,
                               String gender, long groupId, int windApplicable) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("eventName", eventName);
        body.put("eventCode", "TE_" + uniqueSuffix());
        body.put("eventType", eventType);
        body.put("subType", subType);
        body.put("gender", gender);
        body.put("groupId", groupId);
        body.put("windApplicable", windApplicable);
        body.put("isTeamScore", 1);
        body.put("durationMin", 30);
        body.put("unitOfMeasure", "s");
        JsonNode data = assertOk(perform(rawPost("/api/events", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }

    /**
     * 为项目新增一个轮次。
     *
     * @param token     访问令牌（需具备 event:round:add 权限）
     * @param eventId   项目 ID
     * @param roundCode QUALIFY / PRE / R1 / R2 / SEMI / FINAL
     * @param roundName 轮次名称
     * @return 轮次 ID
     */
    protected long createRound(String token, long eventId, String roundCode, String roundName) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("roundCode", roundCode);
        body.put("roundName", roundName);
        JsonNode data = assertOk(perform(rawPost("/api/events/" + eventId + "/rounds", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }

    /**
     * 为项目新增一个决赛轮次。
     *
     * @param token   访问令牌（需具备 event:round:add 权限）
     * @param eventId 项目 ID
     * @return 轮次 ID
     */
    protected long createFinalRound(String token, long eventId) throws Exception {
        return createRound(token, eventId, "FINAL", "决赛");
    }

    /**
     * 在演示届次下新增一名运动员。
     *
     * @param token       访问令牌（需具备 athlete:add 权限）
     * @param athleteName 姓名（建议唯一）
     * @param groupId     所属组别
     * @param unitId      所属单位
     * @param bibNo       号码（建议唯一）
     * @return 运动员 ID
     */
    protected long createAthlete(String token, String athleteName, long groupId, long unitId, String bibNo)
            throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("athleteName", athleteName);
        body.put("gender", "MALE");
        body.put("birthDate", "2009-05-06");
        body.put("idCard", "TEST" + uniqueSuffix());
        body.put("groupId", groupId);
        body.put("unitId", unitId);
        body.put("bibNo", bibNo);
        body.put("className", "测试班级");
        body.put("qualificationStatus", "QUALIFIED");
        JsonNode data = assertOk(perform(rawPost("/api/athletes", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }

    /**
     * 在演示届次下新增一个参赛单位。
     *
     * @param token    访问令牌
     * @param unitName 单位名称（建议唯一）
     * @return 单位 ID
     */
    protected long createUnit(String token, String unitName) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("unitName", unitName);
        body.put("unitCode", "TU_" + uniqueSuffix());
        body.put("unitType", "SCHOOL");
        JsonNode data = assertOk(perform(rawPost("/api/units", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }

    /**
     * 新增一个竞赛单元（日程容器）。
     *
     * @param token       访问令牌（需具备 schedule:session:add 权限）
     * @param sessionName 单元名称（建议唯一）
     * @param sessionDate 日期（yyyy-MM-dd）
     * @param period      时段：MORNING / AFTERNOON / EVENING
     * @param startTime   开始时间（HH:mm）
     * @param endTime     结束时间（HH:mm）
     * @return 竞赛单元 ID
     */
    protected long createSession(String token, String sessionName, String sessionDate, String period,
                                 String startTime, String endTime) throws Exception {
        JsonNode data = assertOk(perform(rawPost("/api/sessions", token, DEMO_MEET_ID,
                sessionPayload(sessionName, sessionDate, period, startTime, endTime))));
        return data.asLong();
    }

    /**
     * 新增一个「日期 + 时段」唯一的竞赛单元（自动重试规避唯一键冲突，保证用例幂等）。
     *
     * <p>{@code uk_session_meet_date_period} 约束同一天同一时段只能有一个单元，
     * 因此这里用随机年份（2036~2043）+ 随机日期 + 随机时段生成隔离数据。</p>
     *
     * @param token 访问令牌
     * @return 竞赛单元 ID
     */
    protected long createUniqueSession(String token) throws Exception {
        String sessionName = uniqueName("测试竞赛单元");
        String[] periods = {"MORNING", "AFTERNOON", "EVENING"};
        for (int attempt = 1; attempt <= ASSERT_RETRY_TIMES; attempt++) {
            int seed = ThreadLocalRandom.current().nextInt(100000, 999999);
            String date = String.format("%04d-%02d-%02d", 2036 + seed % 8, 1 + (seed / 8) % 12, 1 + (seed / 96) % 28);
            String period = periods[seed % periods.length];
            JsonNode response = postJson("/api/sessions", token, DEMO_MEET_ID,
                    sessionPayload(sessionName, date, period, "08:00", "18:00"));
            if (response.path("code").asInt(-1) == CODE_SUCCESS) {
                return response.path("data").asLong();
            }
        }
        throw new IllegalStateException("无法创建测试竞赛单元：3 次随机日期/时段均发生唯一键冲突");
    }

    private Map<String, Object> sessionPayload(String sessionName, String sessionDate, String period,
                                               String startTime, String endTime) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("sessionName", sessionName);
        body.put("sessionDate", sessionDate);
        body.put("period", period);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        return body;
    }

    /**
     * 新增一条个人项目报名，返回报名 ID。
     */
    protected long createRegistration(String token, long eventId, long athleteId) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("eventId", eventId);
        body.put("athleteId", athleteId);
        JsonNode data = assertOk(perform(rawPost("/api/registrations", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }

    /**
     * 批量审核通过报名。
     */
    protected void approveRegistrations(String token, java.util.List<Long> registrationIds) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ids", registrationIds);
        assertOk(perform(rawPost("/api/registrations/approve", token, DEMO_MEET_ID, body)));
    }

    /**
     * 新增一名运动员并为其在该项目下创建「已审核通过」的报名。
     *
     * @param token   访问令牌
     * @param eventId 项目 ID
     * @return 报名记录 ID
     */
    protected long createAthleteWithApprovedRegistration(String token, long eventId) throws Exception {
        long athleteId = createAthlete(token, uniqueName("测试编排运动员"), 1L, 1L, "TS_" + uniqueSuffix());
        long registrationId = createRegistration(token, eventId, athleteId);
        approveRegistrations(token, java.util.List.of(registrationId));
        return registrationId;
    }

    /**
     * 构造「可直接编排/录成绩」的项目：项目 + 决赛轮次 + N 名已审核通过报名的运动员。
     *
     * @param token        访问令牌
     * @param eventName    项目名称（建议唯一）
     * @param athleteCount 参赛人数
     * @return 项目 ID
     */
    protected long createSchedulableEvent(String token, String eventName, int athleteCount) throws Exception {
        long eventId = createEvent(token, eventName, "TRACK", "SPRINT", "MALE", 1L, 1);
        createFinalRound(token, eventId);
        for (int index = 0; index < athleteCount; index++) {
            createAthleteWithApprovedRegistration(token, eventId);
        }
        return eventId;
    }

    /**
     * 取项目的首个轮次 ID（先执行 {@link #createFinalRound}）。
     */
    protected long firstRoundId(String token, long eventId) throws Exception {
        JsonNode rounds = assertOk(perform(rawGet("/api/events/" + eventId + "/rounds", token, DEMO_MEET_ID)));
        assertThat(rounds.isArray()).as("项目 %s 的轮次列表应为数组", eventId).isTrue();
        assertThat(rounds).as("项目 %s 应至少有一个轮次", eventId).isNotEmpty();
        return rounds.get(0).path("id").asLong();
    }

    /**
     * 查询项目下的报名记录分页数据。
     */
    protected JsonNode registrationsOfEvent(String token, long eventId) throws Exception {
        return getJson("/api/registrations", token, DEMO_MEET_ID,
                "page=1", "size=50", "eventId=" + eventId);
    }

    /**
     * 手工新增一条日程条目，返回日程 ID。
     *
     * @param token     访问令牌（需具备 schedule:add 权限）
     * @param sessionId 竞赛单元 ID
     * @param eventId   项目 ID
     * @param roundId   轮次 ID
     * @param startTime 计划开始时间（yyyy-MM-dd HH:mm:ss）
     * @return 日程条目 ID
     */
    protected long createScheduleEntry(String token, long sessionId, long eventId, long roundId, String startTime)
            throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("meetId", DEMO_MEET_ID);
        body.put("sessionId", sessionId);
        body.put("eventId", eventId);
        body.put("roundId", roundId);
        body.put("planStartTime", startTime);
        body.put("durationMin", 30);
        body.put("heatCount", 1);
        body.put("memberCount", 2);
        JsonNode data = assertOk(perform(rawPost("/api/schedule", token, DEMO_MEET_ID, body)));
        return data.asLong();
    }
}
