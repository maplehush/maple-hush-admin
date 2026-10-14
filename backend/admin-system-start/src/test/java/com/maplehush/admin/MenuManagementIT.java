package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthSessionService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实数据库与 HTTP 验证管理写事务、动态权限和审计，不改开发库种子。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T07_IT_ENABLED", matches = "true")
class MenuManagementIT {
    private static final String SCHEMA = "maple_hush_t07_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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
    @Autowired private IAuthSessionService sessions;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JsonMapper json;
    @Autowired private RedisKeyBuilder keys;
    @Autowired @Qualifier("stringValueRedisTemplate") private RedisTemplate<String, String> redis;
    private final HttpClient client = HttpClient.newHttpClient();
    private long adminId;
    private String access;

    @BeforeEach
    void prepare() {
        adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class);
        jdbc.update("UPDATE sys_user SET must_change_password=0 WHERE id=?", adminId);
        renew();
    }
    @AfterEach
    void clean() {
        jdbc.update("DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON m.id=rm.menu_id WHERE m.name LIKE 'T07%'");
        while (jdbc.update("DELETE m FROM sys_menu m LEFT JOIN sys_menu c ON c.parent_id=m.id WHERE m.name LIKE 'T07%' AND c.id IS NULL") > 0) { }
        jdbc.update("DELETE FROM auth_refresh_session");
        jdbc.update("DELETE FROM sys_operation_log");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id<>?", adminId);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='t07_regular')");
        jdbc.update("DELETE FROM sys_role WHERE role_code='t07_regular'");
        for (Long id : jdbc.queryForList("SELECT id FROM sys_user", Long.class)) redis.delete(keys.key("user", id.toString()));
        jdbc.update("DELETE FROM sys_user WHERE id<>?", adminId);
    }

    @Test
    void crudRoundTripBuiltinGrantsAndRealAudit() throws Exception {
        long root = create(body("T07Catalog", "catalog", null, "/t07", null, null));
        var pageBody = body("T07Page", "menu", root, "/t07/user", "/system/user/list", "system:t07:list");
        pageBody.put("meta", Map.of("title", "测试页面", "icon", "lucide:user", "order", 5, "keepAlive", true));
        long page = create(pageBody);
        long button = create(body("T07Button", "button", page, null, null, "system:t07:create"));
        assertEquals("user", jdbc.queryForObject("SELECT path FROM sys_menu WHERE id=?", String.class, page));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (?,?,?)", Integer.class, root, page, button));
        var managed = find(data(request("GET", "/api/system/menu/list", null, access)), "T07Page");
        assertEquals(Long.toString(page), managed.get("id").asString());
        assertEquals("/t07/user", managed.get("path").asString());
        assertEquals("button", managed.get("children").get(0).get("type").asString());
        var route = find(data(request("GET", "/api/menu/all", null, access)), "T07Page");
        assertEquals("user", route.get("path").asString()); assertFalse(route.has("children")); assertFalse(route.has("id"));
        pageBody.put("meta", Map.of("title", "更新页面", "order", 9, "icon", "", "activeIcon", ""));
        assertEquals(200, request("PUT", "/api/system/menu/" + page, pageBody, access).statusCode()); renew();
        assertNull(jdbc.queryForObject("SELECT icon FROM sys_menu WHERE id=?", String.class, page));
        assertNull(jdbc.queryForObject("SELECT meta_json FROM sys_menu WHERE id=?", String.class, page));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE target_id=? AND operation_code='system:menu:update' AND success=1", Integer.class, Long.toString(page)));
        assertEquals(409, request("DELETE", "/api/system/menu/" + page, null, access).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:menu:delete' AND success=0", Integer.class));
        for (long id : new long[]{button, page, root}) { assertEquals(200, request("DELETE", "/api/system/menu/" + id, null, access).statusCode()); renew(); }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (?,?,?)", Integer.class, root, page, button));
    }

    @Test
    void duplicatesCrossLevelPathsAndDirectoryMovesAreValidated() throws Exception {
        long root = create(body("T07Catalog", "catalog", null, "/t07", null, null));
        create(body("T07Page", "menu", root, "/t07/user", "/system/user/list", "system:t07:list"));
        assertEquals(409, request("POST", "/api/system/menu", body("T07Other", "menu", null, "/t07/user", "/system/user/list", null), access).statusCode());
        assertEquals(409, request("POST", "/api/system/menu", body("T07Page", "catalog", null, "/different", null, null), access).statusCode());
        assertTrue(data(request("GET", "/api/system/menu/path-exists?path=/t07/user", null, access)).asBoolean());
        assertFalse(data(request("GET", "/api/system/menu/path-exists?path=/t07&id=" + root, null, access)).asBoolean());
        assertTrue(data(request("GET", "/api/system/menu/name-exists?name=T07Catalog", null, access)).asBoolean());
        assertEquals(200, request("PUT", "/api/system/menu/" + root, body("T07Catalog", "catalog", null, "/moved", null, null), access).statusCode()); renew();
        assertNotNull(find(data(request("GET", "/api/system/menu/list", null, access)), "T07Page"));
        assertTrue(data(request("GET", "/api/system/menu/path-exists?path=/moved/user", null, access)).asBoolean());
        assertFalse(data(request("GET", "/api/system/menu/path-exists?path=/t07/user", null, access)).asBoolean());
    }

    @Test
    void descendantCollisionsCyclesAndRedirectDependenciesAreRejected() throws Exception {
        long root = create(body("T07Catalog", "catalog", null, "/t07", null, null));
        long sub = create(body("T07Sub", "catalog", root, "/t07/sub", null, null));
        long page = create(body("T07Page", "menu", sub, "/t07/sub/user", "/system/user/list", null));
        create(body("T07Other", "menu", null, "/moved/sub/user", "/system/user/list", null));
        assertEquals(409, request("PUT", "/api/system/menu/" + root, body("T07Catalog", "catalog", null, "/moved", null, null), access).statusCode());
        assertEquals("/t07", jdbc.queryForObject("SELECT path FROM sys_menu WHERE id=?", String.class, root));
        assertEquals(400, request("PUT", "/api/system/menu/" + root, body("T07Catalog", "catalog", sub, "/t07/sub/back", null, null), access).statusCode());
        var redirect = body("T07Redirect", "catalog", null, "/t07redirect", null, null);
        redirect.put("redirect", "/t07/sub/user");
        create(redirect);
        assertEquals(409, request("DELETE", "/api/system/menu/" + page, null, access).statusCode());
        redirect.put("name", "T07SelfRedirect"); redirect.put("path", "/self"); redirect.put("redirect", "/self");
        assertEquals(400, request("POST", "/api/system/menu", redirect, access).statusCode());
    }

    @Test
    void rejectsInvalidHierarchyComponentsAndMetadata() throws Exception {
        long root = create(body("T07Catalog", "catalog", null, "/t07", null, null));
        long page = create(body("T07Page", "menu", root, "/t07/user", "/system/user/list", null));
        for (var invalid : List.of(
                body("T07Bad", "button", root, null, null, "system:t07:create"),
                body("T07Bad", "catalog", page, "/t07/user/child", null, null),
                body("T07Bad", "menu", root, "/outside", "/system/user/list", null),
                body("T07Bad", "menu", root, "/t07/bad", "/missing/list", null),
                body("T07Bad", "menu", root, "/t07/bad", "/system/user/list.vue", null),
                body("T07Bad", "button", page, null, null, "System:T07:Create"))) {
            assertEquals(400, request("POST", "/api/system/menu", invalid, access).statusCode());
        }
        var unsafe = body("T07Bad", "menu", root, "/t07/bad", "/system/user/list", null);
        unsafe.put("meta", Map.of("title", "测试", "ignoreAccess", true));
        assertEquals(400, request("POST", "/api/system/menu", unsafe, access).statusCode());
        assertEquals(400, request("PUT", "/api/system/menu/" + root, body("T07Catalog", "catalog", root, "/t07/child", null, null), access).statusCode());
        assertEquals(400, request("GET", "/api/system/menu/path-exists", null, access).statusCode());
        assertEquals(404, request("DELETE", "/api/system/menu/999999", null, access).statusCode());
    }

    @Test
    void embeddedAndLinkRequireSafeUrlsAndHaveDistinctComponents() throws Exception {
        var embedded = body("T07Frame", "embedded", null, "/t07frame", null, null);
        embedded.put("meta", Map.of("title", "内嵌", "iframeSrc", "https://example.com/frame"));
        long frame = create(embedded);
        assertEquals("IFrameView", jdbc.queryForObject("SELECT component FROM sys_menu WHERE id=?", String.class, frame));
        var link = body("T07Link", "link", null, "/t07link", null, null);
        link.put("meta", Map.of("title", "外链", "link", "javascript:alert(1)"));
        assertEquals(400, request("POST", "/api/system/menu", link, access).statusCode());
        link.put("meta", Map.of("title", "外链", "link", "https://example.com/docs", "openInNewWindow", true));
        create(link);
        var route = find(data(request("GET", "/api/menu/all", null, access)), "T07Link");
        assertEquals("https://example.com/docs", route.get("meta").get("link").asString());
        assertFalse(route.has("component"));
    }

    @Test
    void ordinaryAdminCanRenameAuthorizedCatalogAndCodeLessPage() throws Exception {
        long root = create(body("T07Catalog", "catalog", null, "/t07scope", null, null));
        long page = create(body("T07Public", "menu", root, "/t07scope/page", "/system/user/list", null));
        String regular = regularUser();
        long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='t07_regular'", Long.class);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,?),(?,?)", role, root, role, page);
        var response = request("PUT", "/api/system/menu/" + root,
                body("T07RenamedCatalog", "catalog", null, "/t07scope", null, null), regular);
        assertEquals(200, response.statusCode(), response.body());
        long user = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='t07_regular'", Long.class);
        regular = sessions.open(user, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, user)).accessToken();
        assertEquals(200, request("PUT", "/api/system/menu/" + page,
                body("T07RenamedPage", "menu", root, "/t07scope/page", "/system/user/list", null), regular).statusCode());
    }

    @Test
    void unauthorizedAndOutOfScopeWritesCannotEscalate() throws Exception {
        assertEquals(401, request("GET", "/api/system/menu/list", null, null).statusCode());
        String regular = regularUser();
        assertEquals(200, request("GET", "/api/system/menu/list", null, regular).statusCode());
        assertEquals(403, request("DELETE", "/api/system/menu/10", null, regular).statusCode());
        var escalation = body("SystemMenu", "menu", 1L, "/system/menu", "/system/menu/list", "system:user:delete");
        assertEquals(403, request("PUT", "/api/system/menu/30", escalation, regular).statusCode());
        assertEquals("system:menu:list", jdbc.queryForObject("SELECT auth_code FROM sys_menu WHERE id=30", String.class));
    }

    @Test
    void committedWritesInvalidateOtherUsersAndConcurrentOldRequestsCannotBothCommit() throws Exception {
        String regular = regularUser();
        String stale = access;
        var payload = body("T07Concurrent", "catalog", null, "/t07race", null, null);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> call = () -> { start.await(); return request("POST", "/api/system/menu", payload, stale).statusCode(); };
            var one = executor.submit(call); var two = executor.submit(call); start.countDown();
            var statuses = List.of(one.get(), two.get());
            assertEquals(1, statuses.stream().filter(status -> status == 200).count());
            assertTrue(statuses.contains(401) || statuses.contains(409), statuses.toString());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE name='T07Concurrent'", Integer.class));
        assertEquals(401, request("GET", "/api/menu/all", null, regular).statusCode());
        assertEquals(401, request("GET", "/api/menu/all", null, stale).statusCode());
        assertNull(redis.opsForValue().get(keys.key("user", Long.toString(adminId))));
    }

    private String regularUser() {
        jdbc.update("INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES ('t07_regular','普通管理员',?,1,0)", passwords.encode("t07 strong passphrase"));
        long user = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='t07_regular'", Long.class);
        jdbc.update("INSERT INTO sys_role(role_code,role_name,status) VALUES ('t07_regular','菜单查询和修改',1)");
        long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='t07_regular'", Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", user, role);
        for (long menu : new long[]{30, 32}) jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,?)", role, menu);
        return sessions.open(user, 0).accessToken();
    }
    private void renew() { access = sessions.open(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId)).accessToken(); }
    private long create(Map<String, Object> body) throws Exception {
        var response = request("POST", "/api/system/menu", body, access);
        assertEquals(200, response.statusCode(), response.body());
        long id = Long.parseLong(data(response).get("id").asString()); renew(); return id;
    }
    private Map<String, Object> body(String name, String type, Long pid, String path, String component, String code) {
        var body = new HashMap<String, Object>();
        body.put("name", name); body.put("type", type); body.put("pid", pid == null ? null : pid.toString());
        body.put("path", path); body.put("component", component); body.put("authCode", code); body.put("status", 1);
        body.put("meta", Map.of("title", "测试菜单", "order", 10)); return body;
    }
    private JsonNode data(HttpResponse<String> response) { return json.readTree(response.body()).get("data"); }
    private JsonNode find(JsonNode routes, String name) {
        for (JsonNode route : routes) {
            if (name.equals(route.get("name").asString())) return route;
            if (route.has("children")) { JsonNode found = find(route.get("children"), name); if (found != null) return found; }
        }
        return null;
    }
    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
}
