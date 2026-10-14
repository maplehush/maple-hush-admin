package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.AuthRedisService;
import com.maplehush.admin.system.auth.AuthSessionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 HTTP/MySQL/Redis 验证角色授权；单个缓存清理故障使用受控注入。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T09_IT_ENABLED", matches = "true")
class RoleManagementIT {
    private static final String SCHEMA = "maple_hush_t09_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "123456"); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/" + SCHEMA
                + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true");
        properties.add("maple-hush.redis.key-prefix", () -> SCHEMA);
    }
    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "123456"); var sql = connection.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthSessionService sessions;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JsonMapper json;
    @Autowired private RedisKeyBuilder keys;
    @Autowired @Qualifier("stringValueRedisTemplate") private RedisTemplate<String, String> redis;
    @MockitoSpyBean private AuthRedisService snapshots;
    private final HttpClient client = HttpClient.newHttpClient();
    private long admin;
    private long builtin;
    private String access;

    @BeforeEach
    void prepare() {
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class);
        builtin = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='super_admin'", Long.class);
        jdbc.update("UPDATE sys_user SET must_change_password=0 WHERE id=?", admin);
        access = sessions.open(admin, 0).accessToken();
    }
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM auth_refresh_session"); jdbc.update("DELETE FROM sys_login_log"); jdbc.update("DELETE FROM sys_operation_log");
        jdbc.update("DELETE FROM sys_user_role WHERE role_id<>?", builtin);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id<>?", builtin);
        jdbc.update("DELETE FROM sys_role WHERE id<>?", builtin);
        for (Long id : jdbc.queryForList("SELECT id FROM sys_user", Long.class)) redis.delete(keys.key("user", id.toString()));
        jdbc.update("DELETE FROM sys_user WHERE id<>?", admin);
    }

    @Test
    void pagingDatesDetailsAndStableCodeMatchContract() throws Exception {
        long one = create("t09_one", List.of()); long two = create("t09_two", List.of()); long three = create("t09_three", List.of());
        jdbc.update("UPDATE sys_role SET create_time='2026-10-12 00:00:00' WHERE id=?", one);
        jdbc.update("UPDATE sys_role SET create_time='2026-10-12 23:59:59' WHERE id=?", two);
        jdbc.update("UPDATE sys_role SET create_time='2026-10-13 00:00:00' WHERE id=?", three);
        var response = request("GET", "/api/system/role/list?page=2&pageSize=1&name=T09&status=1&startTime=2026-10-12&endTime=2026-10-12", null, access);
        assertEquals(200, response.statusCode(), response.body());
        var page = data(response); assertEquals(2, page.get("total").asInt()); assertEquals(1, page.get("items").size());
        assertEquals(Long.toString(two), page.get("items").get(0).get("id").asString());
        assertEquals("2026-10-12 23:59:59", page.get("items").get(0).get("createTime").asString());
        var detail = data(request("GET", "/api/system/role/" + one, null, access));
        assertEquals("t09_one", detail.get("roleCode").asString()); assertFalse(detail.get("builtIn").asBoolean());
        assertEquals(409, request("POST", "/api/system/role", body("t09_one", "T09重复", 1, List.of()), access).statusCode());
        assertEquals(409, request("PUT", "/api/system/role/" + one, body("different", "T09改码", 1, null), access).statusCode());
        for (String query : List.of("page=0", "pageSize=101", "page=abc", "status=2", "startTime=2026-02-30", "startTime=2026-10-13&endTime=2026-10-12")) {
            assertEquals(400, request("GET", "/api/system/role/list?" + query, null, access).statusCode());
        }
        assertEquals(404, request("GET", "/api/system/role/999999", null, access).statusCode());
        assertEquals(400, request("POST", "/api/system/role", body(null, "T09缺编码", 1, null), access).statusCode());
    }

    @Test
    void grantsNormalizeAndRemovingPageCleansButtonsAndInvalidatesSessions() throws Exception {
        long role = create("t09_target", List.of("10", "11"));
        assertEquals(List.of("1", "10", "11"), ids(role));
        assertEquals("[\"1\",\"10\",\"11\"]", data(request("GET", "/api/system/role/" + role, null, access)).get("permissions").toString());
        assertEquals(400, request("POST", "/api/system/role", body("t09_orphan", "T09孤立", 1, List.of("11")), access).statusCode());
        assertEquals(400, request("POST", "/api/system/role", body("t09_bad", "T09错误", 1, List.of("999999")), access).statusCode());
        long user = user("t09_member", role);
        var login = sessions.open(user, 0); String key = keys.key("user", Long.toString(user));
        assertEquals(200, request("PUT", "/api/system/role/" + role, body(null, "T09变更", 1, List.of("11", "20")), access).statusCode());
        assertEquals(List.of("1", "20"), ids(role)); assertNull(redis.opsForValue().get(key));
        assertEquals(401, request("GET", "/api/auth/codes", null, login.accessToken()).statusCode());
        assertThrows(BusinessException.class, () -> sessions.refresh(login.refreshToken(), "127.0.0.1", "IT"));
        assertEquals(1L, version(user));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:role:update' AND target_id=? AND success=1", Integer.class, Long.toString(role)));
    }

    @Test
    void protectedBuiltinCannotBeMutatedOrRecreated() throws Exception {
        assertEquals(403, request("PUT", "/api/system/role/" + builtin, body("super_admin", "内置", 0, List.of()), access).statusCode());
        assertEquals(403, request("DELETE", "/api/system/role/" + builtin, null, access).statusCode());
        assertEquals(403, request("POST", "/api/system/role", body("super_admin", "第二内置", 1, List.of()), access).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT status FROM sys_role WHERE id=?", Integer.class, builtin));
        assertEquals(26, ids(builtin).size());
    }

    @Test
    void ordinaryActorsNeedGrantAndCannotGrantOutsideTheirEffectiveNodes() throws Exception {
        long limitedRole = create("t09_limited", List.of("20", "21", "22", "10"));
        long limitedUser = user("t09_limited_user", limitedRole);
        String limited = sessions.open(limitedUser, 0).accessToken();
        assertEquals(403, request("POST", "/api/system/role", body("t09_forbidden", "T09越权", 1, List.of("10")), limited).statusCode());
        assertEquals(200, request("POST", "/api/system/role", body("t09_empty", "T09空角色", 1, null), limited).statusCode());
        assertEquals(403, request("DELETE", "/api/system/role/999999", null, limited).statusCode());
        long grantRole = create("t09_granter", List.of("20", "21", "22", "24", "10"));
        long grantUser = user("t09_granter_user", grantRole);
        String granter = sessions.open(grantUser, 0).accessToken();
        assertEquals(200, request("POST", "/api/system/role", body("t09_allowed", "T09可授", 1, List.of("10")), granter).statusCode());
        assertEquals(403, request("POST", "/api/system/role", body("t09_escalate", "T09越权按钮", 1, List.of("10", "11")), granter).statusCode());
        long powerful = create("t09_disabled", List.of("10", "11"));
        assertEquals(200, request("PUT", "/api/system/role/" + powerful, body(null, "T09停用", 0, null), access).statusCode());
        assertEquals(403, request("PUT", "/api/system/role/" + powerful, body(null, "T09越权启用", 1, null), granter).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT status FROM sys_role WHERE id=?", Integer.class, powerful));
        assertEquals(401, request("GET", "/api/system/role/list", null, null).statusCode());
    }

    @Test
    void omittedStatusPreservesDisabledRoleDuringMetadataOnlyUpdate() throws Exception {
        long actorRole = create("t09_editor", List.of("20", "22"));
        long actor = user("t09_editor_user", actorRole);
        String editor = sessions.open(actor, 0).accessToken();
        long target = create("t09_target", List.of("10"));
        assertEquals(200, request("PUT", "/api/system/role/" + target, body(null, "T09停用", 0, null), access).statusCode());
        var response = request("PUT", "/api/system/role/" + target, body(null, "T09仅改名称", null, null), editor);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(0, jdbc.queryForObject("SELECT status FROM sys_role WHERE id=?", Integer.class, target));
    }

    @Test
    void disablingAndDeletingRoleInvalidateAllFormerHolders() throws Exception {
        long role = create("t09_target", List.of("10"));
        long one = user("t09_one", role); long two = user("t09_two", role);
        String old = sessions.open(one, 0).accessToken(); sessions.open(two, 0);
        assertEquals(200, request("PUT", "/api/system/role/" + role, body(null, "T09停用", 0, null), access).statusCode());
        assertEquals(1L, version(one)); assertEquals(1L, version(two));
        assertEquals(401, request("GET", "/api/auth/codes", null, old).statusCode());
        String empty = sessions.open(one, 1).accessToken();
        assertTrue(data(request("GET", "/api/auth/codes", null, empty)).isEmpty());
        assertEquals(200, request("DELETE", "/api/system/role/" + role, null, access).statusCode());
        assertEquals(2L, version(one)); assertEquals(2L, version(two));
        assertEquals(401, request("GET", "/api/user/info", null, empty).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE role_id=?", Integer.class, role));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=?", Integer.class, role));
    }

    @Test
    void cacheInvalidationFailureDoesNotRestoreOldAuthorityOrRollbackGrant() throws Exception {
        long role = create("t09_target", List.of("10")); long user = user("t09_member", role);
        String old = sessions.open(user, 0).accessToken();
        doThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "injected cache outage")).when(snapshots).clearSnapshot(user);
        assertEquals(200, request("PUT", "/api/system/role/" + role, body(null, "T09变更", 1, List.of("10", "11")), access).statusCode());
        assertEquals(List.of("1", "10", "11"), ids(role)); assertEquals(1L, version(user));
        assertNotNull(redis.opsForValue().get(keys.key("user", Long.toString(user))));
        assertEquals(401, request("GET", "/api/user/info", null, old).statusCode());
    }

    @Test
    void concurrentGrantsDoNotLeaveMixedRelationsAndInvalidateMembers() throws Exception {
        long role = create("t09_target", List.of("10")); long user = user("t09_member", role);
        String old = sessions.open(user, 0).accessToken(); CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { go.await(); return request("PUT", "/api/system/role/" + role, body(null, "T09甲", 1, List.of("10", "11")), access).statusCode(); });
            var two = executor.submit(() -> { go.await(); return request("PUT", "/api/system/role/" + role, body(null, "T09乙", 1, List.of("20", "21")), access).statusCode(); });
            go.countDown(); assertEquals(200, one.get()); assertEquals(200, two.get());
        }
        assertTrue(List.of(List.of("1", "10", "11"), List.of("1", "20", "21")).contains(ids(role)));
        assertEquals(2L, version(user)); assertEquals(401, request("GET", "/api/auth/codes", null, old).statusCode());
    }

    private long create(String code, List<String> menus) throws Exception {
        var response = request("POST", "/api/system/role", body(code, "T09角色", 1, menus), access);
        assertEquals(200, response.statusCode(), response.body()); return Long.parseLong(data(response).get("id").asString());
    }
    private long user(String name, long role) {
        jdbc.update("INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES (?, '角色测试',?,1,0)", name, passwords.encode("t09 strong phrase"));
        long user = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", user, role); return user;
    }
    private long version(long user) { return jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, user); }
    private List<String> ids(long role) { return jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=? ORDER BY menu_id", Long.class, role).stream().map(String::valueOf).toList(); }
    private Map<String, Object> body(String code, String name, Integer status, List<String> menus) {
        var body = new HashMap<String, Object>(); body.put("roleCode", code); body.put("name", name); body.put("status", status);
        body.put("permissions", menus); body.put("remark", null); return body;
    }
    private JsonNode data(HttpResponse<String> response) { return json.readTree(response.body()).get("data"); }
    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
