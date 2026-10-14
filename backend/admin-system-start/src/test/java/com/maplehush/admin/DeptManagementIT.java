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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

/** 真实 HTTP 与 MySQL 验证部门管理、并发成环及用户关联删除保护。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T08_IT_ENABLED", matches = "true")
class DeptManagementIT {
    private static final String SCHEMA = "maple_hush_t08_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/" + SCHEMA
            + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true";
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "123456"); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> URL);
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
        access = sessions.open(adminId, 0).accessToken();
    }
    @AfterEach
    void clean() {
        jdbc.update("UPDATE sys_user SET dept_id=NULL");
        while (jdbc.update("DELETE d FROM sys_dept d LEFT JOIN sys_dept c ON c.parent_id=d.id WHERE c.id IS NULL") > 0) { }
        jdbc.update("DELETE FROM auth_refresh_session");
        jdbc.update("DELETE FROM sys_operation_log");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id<>?", adminId);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='t08_readonly')");
        jdbc.update("DELETE FROM sys_role WHERE role_code='t08_readonly'");
        for (Long id : jdbc.queryForList("SELECT id FROM sys_user", Long.class)) redis.delete(keys.key("user", id.toString()));
        jdbc.update("DELETE FROM sys_user WHERE id<>?", adminId);
    }

    @Test
    void treeCrudSortingNullableUpdatesAndAuditUseStableContracts() throws Exception {
        long root = create(body("总部", null, 20, 1, "原备注"));
        long other = create(body("总部", null, 0, 0, null));
        long one = create(body("研发部", root, 5, 1, "旧备注"));
        long two = create(body("产品部", root, 5, 1, null));
        var response = request("GET", "/api/system/dept/list?page=1&pageSize=1", null, access);
        assertEquals(200, response.statusCode());
        var tree = data(response); assertTrue(tree.isArray()); assertEquals(2, tree.size());
        assertEquals(Long.toString(other), tree.get(0).get("id").asString());
        assertEquals("0", tree.get(0).get("pid").asString());
        assertEquals(Long.toString(one), tree.get(1).get("children").get(0).get("id").asString());
        assertTrue(tree.get(0).get("children").isEmpty());
        assertTrue(tree.get(0).get("createTime").asString().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals(200, request("PUT", "/api/system/dept/" + one, body("研发中心", null, -1, 0, ""), access).statusCode());
        assertNull(jdbc.queryForObject("SELECT parent_id FROM sys_dept WHERE id=?", Long.class, one));
        assertNull(jdbc.queryForObject("SELECT remark FROM sys_dept WHERE id=?", String.class, one));
        assertEquals(0L, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        assertEquals(200, request("GET", "/api/user/info", null, access).statusCode());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:dept:create' AND success=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:dept:update' AND target_id=? AND success=1", Integer.class, Long.toString(one)));
        for (long id : new long[]{two, root, one, other}) assertEquals(200, request("DELETE", "/api/system/dept/" + id, null, access).statusCode());
        assertTrue(data(request("GET", "/api/system/dept/list", null, access)).isEmpty());
    }

    @Test
    void rejectsCyclesMissingParentsAndInvalidInputWithoutChangingRows() throws Exception {
        long root = create(body("总部", null, 0, 1, null));
        long child = create(body("子部门", root, 0, 1, null));
        assertEquals(400, request("PUT", "/api/system/dept/" + root, body("总部", child, 0, 1, null), access).statusCode());
        assertEquals(400, request("PUT", "/api/system/dept/" + root, body("总部", root, 0, 1, null), access).statusCode());
        assertEquals(400, request("POST", "/api/system/dept", body("不存在父级", 999999L, 0, 1, null), access).statusCode());
        assertEquals(400, request("POST", "/api/system/dept", body(" ", null, 0, 1, null), access).statusCode());
        assertEquals(400, request("POST", "/api/system/dept", body("部门", null, 0, 2, null), access).statusCode());
        assertEquals(400, request("POST", "/api/system/dept", body("部门", null, 0, 1, "x".repeat(501)), access).statusCode());
        assertEquals(400, request("DELETE", "/api/system/dept/0", null, access).statusCode());
        assertEquals(404, request("PUT", "/api/system/dept/999999", body("部门", null, 0, 1, null), access).statusCode());
        assertNull(jdbc.queryForObject("SELECT parent_id FROM sys_dept WHERE id=?", Long.class, root));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE success=0", Integer.class) >= 3);
    }

    @Test
    void childrenAndDisabledAssociatedUsersPreventDeletion() throws Exception {
        long root = create(body("总部", null, 0, 1, null));
        long child = create(body("子部门", root, 0, 1, null));
        assertEquals(409, request("DELETE", "/api/system/dept/" + root, null, access).statusCode());
        long user = user(0);
        jdbc.update("UPDATE sys_user SET dept_id=? WHERE id=?", child, user);
        assertEquals(409, request("DELETE", "/api/system/dept/" + child, null, access).statusCode());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept", Integer.class));
        jdbc.update("UPDATE sys_user SET dept_id=NULL WHERE id=?", user);
        assertEquals(200, request("DELETE", "/api/system/dept/" + child, null, access).statusCode());
        assertEquals(200, request("DELETE", "/api/system/dept/" + root, null, access).statusCode());
    }

    @Test
    void anonymousAndReadOnlyRoleCannotWrite() throws Exception {
        assertEquals(401, request("GET", "/api/system/dept/list", null, null).statusCode());
        long user = user(1);
        jdbc.update("INSERT INTO sys_role(role_code,role_name,status) VALUES ('t08_readonly','部门只读',1)");
        long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='t08_readonly'", Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", user, role);
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,40)", role);
        String readOnly = sessions.open(user, 0).accessToken();
        assertEquals(200, request("GET", "/api/system/dept/list", null, readOnly).statusCode());
        assertEquals(403, request("POST", "/api/system/dept", body("越权部门", null, 0, 1, null), readOnly).statusCode());
        assertEquals(403, request("PUT", "/api/system/dept/999999", body("越权部门", null, 0, 1, null), readOnly).statusCode());
        assertEquals(403, request("DELETE", "/api/system/dept/999999", null, readOnly).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept", Integer.class));
    }

    @Test
    void concurrentOppositeMovesCannotCreateCycle() throws Exception {
        long a = create(body("甲部门", null, 0, 1, null));
        long b = create(body("乙部门", null, 0, 1, null));
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { go.await(); return request("PUT", "/api/system/dept/" + a, body("甲部门", b, 0, 1, null), access).statusCode(); });
            var two = executor.submit(() -> { go.await(); return request("PUT", "/api/system/dept/" + b, body("乙部门", a, 0, 1, null), access).statusCode(); });
            go.countDown();
            var statuses = List.of(one.get(), two.get());
            assertTrue(statuses.contains(200) && statuses.contains(400), statuses.toString());
        }
        assertEquals(1, data(request("GET", "/api/system/dept/list", null, access)).size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:dept:update' AND success=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:dept:update' AND success=0", Integer.class));
    }

    @Test
    void concurrentUserAssignmentCannotLeaveOrphanOrDeleteReferencedDepartment() throws Exception {
        long dept = create(body("关联部门", null, 0, 1, null));
        long user = user(0);
        try (var connection = DriverManager.getConnection(URL, "root", "123456")) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("UPDATE sys_user SET dept_id=? WHERE id=?")) {
                statement.setLong(1, dept); statement.setLong(2, user); statement.executeUpdate();
            }
            try (var executor = Executors.newSingleThreadExecutor()) {
                var deletion = executor.submit(() -> request("DELETE", "/api/system/dept/" + dept, null, access));
                try {
                    assertThrows(TimeoutException.class, () -> deletion.get(300, TimeUnit.MILLISECONDS));
                } finally { connection.commit(); }
                assertEquals(409, deletion.get(10, TimeUnit.SECONDS).statusCode());
            }
        }
        assertEquals(dept, jdbc.queryForObject("SELECT dept_id FROM sys_user WHERE id=?", Long.class, user));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE id=?", Integer.class, dept));
    }

    private long user(int status) {
        jdbc.update("INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES ('t08_user','部门测试用户',?,?,0)", passwords.encode("t08 strong phrase"), status);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username='t08_user'", Long.class);
    }
    private long create(Map<String, Object> payload) throws Exception {
        var response = request("POST", "/api/system/dept", payload, access);
        assertEquals(200, response.statusCode(), response.body());
        return Long.parseLong(data(response).get("id").asString());
    }
    private Map<String, Object> body(String name, Long parent, int sort, int status, String remark) {
        var body = new HashMap<String, Object>(); body.put("pid", parent == null ? "0" : parent.toString());
        body.put("name", name); body.put("sort", sort); body.put("status", status); body.put("remark", remark); return body;
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
