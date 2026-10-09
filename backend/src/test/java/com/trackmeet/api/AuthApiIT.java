package com.trackmeet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackmeet.api.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 认证与鉴权接口集成测试。
 *
 * <h3>前置条件</h3>
 * <p>测试库（{@code trackmeet_test}）已按序执行 {@code sql/01..06}，并存在内置账号
 * {@code admin}（初始口令见 {@code trackmeet.default-password}，默认 {@code Admin@123}，
 * 由 {@code DefaultAccountInitializer} 在上下文启动时写入）。
 * 测试 profile 已关闭验证码（{@code trackmeet.captcha.enabled=false}），登录可不传验证码。</p>
 *
 * <h3>覆盖接口</h3>
 * <ul>
 *     <li>{@code POST /api/auth/login}：成功 / 口令错误 / 参数校验</li>
 *     <li>{@code GET /api/auth/profile}：带令牌成功 / 无令牌 401 / 登出后令牌失效</li>
 *     <li>{@code POST /api/auth/refresh}：刷新令牌换新访问令牌 / 非法刷新令牌</li>
 *     <li>{@code POST /api/auth/logout}：登出后旧令牌加入黑名单</li>
 *     <li>{@code POST /api/auth/change-password}：参数校验（新密码长度规则、原密码校验）</li>
 * </ul>
 *
 * <h3>无数据库环境</h3>
 * <p>本机未准备 MySQL 时，{@link ApiTestBase} 会在每个用例前通过 JUnit
 * {@code Assumptions} 中止执行，本类全部用例标记为 SKIPPED 而非 FAILED。</p>
 *
 * <p><b>注意</b>：本类刻意不执行“真正修改 admin 口令”的用例——一旦修改成功，
 * {@code token_version} 自增会使所有测试缓存令牌失效，破坏其它用例的幂等性。</p>
 *
 * @author TrackMeet
 */
class AuthApiIT extends ApiTestBase {

    @Test
    @DisplayName("登录成功：验证码关闭时返回访问令牌、刷新令牌与用户信息（含角色与菜单）")
    void shouldLoginSuccessfully() throws Exception {
        MvcResult result = perform(rawPost("/api/auth/login", null, null,
                Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD)));

        JsonNode data = assertOk(result);
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.path("expiresIn").asLong()).isGreaterThan(0L);
        assertThat(data.path("userInfo").path("username").asText()).isEqualTo(ADMIN_USERNAME);

        String accessToken = data.path("accessToken").asText();

        // 携带令牌访问当前用户信息
        JsonNode profile = assertOk(perform(rawGet("/api/auth/profile", accessToken, null)));
        assertThat(profile.path("roles")).isNotEmpty();
        assertThat(profile.path("roles").get(0).asText()).isEqualTo("SUPER_ADMIN");
        assertThat(profile.path("menus").isArray()).isTrue();

        // 运动会列表（届次上下文由 X-Meet-Id 提供）
        JsonNode meets = assertPaged(perform(rawGet("/api/meets", accessToken, DEMO_MEET_ID, "page=1", "size=10")));
        assertThat(meets.path("records").isArray()).isTrue();
    }

    @Test
    @DisplayName("未携带令牌：返回 HTTP 401 与统一错误码 401")
    void shouldRejectAnonymousRequest() throws Exception {
        assertUnauthorized(perform(rawGet("/api/system/users", null, null, "page=1", "size=10")));
        assertUnauthorized(perform(rawGet("/api/auth/profile", null, null)));
    }

    @Test
    @DisplayName("口令错误：返回业务码 10001")
    void shouldRejectWrongPassword() throws Exception {
        MvcResult result = perform(rawPost("/api/auth/login", null, null,
                Map.of("username", ADMIN_USERNAME, "password", "wrong-password")));
        assertBizCode(result, 10001);
    }

    @Test
    @DisplayName("登录参数校验：用户名缺失或口令长度不足返回参数错误")
    void shouldRejectInvalidLoginParams() throws Exception {
        Map<String, Object> blankUsername = new LinkedHashMap<>();
        blankUsername.put("username", "");
        blankUsername.put("password", ADMIN_PASSWORD);
        assertParamError(perform(rawPost("/api/auth/login", null, null, blankUsername)));

        Map<String, Object> shortPassword = new LinkedHashMap<>();
        shortPassword.put("username", ADMIN_USERNAME);
        shortPassword.put("password", "123");
        assertParamError(perform(rawPost("/api/auth/login", null, null, shortPassword)));
    }

    @Test
    @DisplayName("刷新令牌：可用刷新令牌换取新的访问令牌，非法刷新令牌返回 401")
    void shouldRefreshToken() throws Exception {
        JsonNode loginBody = loginFresh(ADMIN_USERNAME, ADMIN_PASSWORD);
        assumeTrue(loginBody.path("code").asInt(-1) == CODE_SUCCESS, "跳过：admin 登录失败，无法验证刷新令牌");
        String refreshToken = loginBody.path("data").path("refreshToken").asText();
        assertThat(refreshToken).isNotBlank();

        MvcResult refreshed = perform(post("/api/auth/refresh").param("refreshToken", refreshToken));
        JsonNode data = assertOk(refreshed);
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();

        // 新的访问令牌可正常访问受保护接口
        assertOk(perform(rawGet("/api/auth/profile", data.path("accessToken").asText(), null)));

        // 非法刷新令牌
        MvcResult illegal = perform(post("/api/auth/refresh").param("refreshToken", "not-a-valid-token"));
        assertBizCode(illegal, CODE_UNAUTHORIZED);
    }

    @Test
    @DisplayName("登出：旧访问令牌被加入黑名单，再次访问返回 401")
    void shouldInvalidateTokenAfterLogout() throws Exception {
        JsonNode loginBody = loginFresh(ADMIN_USERNAME, ADMIN_PASSWORD);
        assumeTrue(loginBody.path("code").asInt(-1) == CODE_SUCCESS, "跳过：admin 登录失败，无法验证登出");
        String accessToken = loginBody.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        // 登出前可用
        assertOk(perform(rawGet("/api/auth/profile", accessToken, null)));
        // 登出
        assertOk(perform(rawPost("/api/auth/logout", accessToken, null, null)));
        // 登出后旧令牌失效（被加入进程内黑名单）
        assertUnauthorized(perform(rawGet("/api/auth/profile", accessToken, null)));
    }

    @Test
    @DisplayName("修改口令参数校验：新密码过短返回 400，原密码错误返回业务码 400")
    void shouldValidateChangePasswordParams() throws Exception {
        String token = adminToken();

        // 新密码长度不足 6：@Size 校验拦截，不会真正修改口令
        Map<String, Object> tooShort = new LinkedHashMap<>();
        tooShort.put("oldPassword", ADMIN_PASSWORD);
        tooShort.put("newPassword", "12345");
        assertParamError(perform(rawPost("/api/auth/change-password", token, null, tooShort)));

        // 新密码为空
        Map<String, Object> blankNew = new LinkedHashMap<>();
        blankNew.put("oldPassword", ADMIN_PASSWORD);
        blankNew.put("newPassword", "");
        assertParamError(perform(rawPost("/api/auth/change-password", token, null, blankNew)));

        // 原密码不正确：业务码 400（同样不会修改口令）
        Map<String, Object> wrongOld = new LinkedHashMap<>();
        wrongOld.put("oldPassword", "WrongOld@123456");
        wrongOld.put("newPassword", "BrandNew@123456");
        assertBizCode(perform(rawPost("/api/auth/change-password", token, null, wrongOld)), CODE_PARAM_ERROR);
    }
}
