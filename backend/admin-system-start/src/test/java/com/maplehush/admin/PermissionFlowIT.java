package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthSessionService;
import com.maplehush.admin.system.log.service.ISysOperationLogService;
import com.maplehush.admin.system.permission.service.IAuthorizationChangeService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** 只在测试上下文装配受保护写接口，不提前实现 T07 管理功能。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@Import(PermissionFlowIT.Fixtures.class)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T06_IT_ENABLED", matches = "true")
class PermissionFlowIT {
    private static final String SCHEMA = "maple_hush_t06_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static final String PREFIX = SCHEMA;
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "123456"); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/" + SCHEMA
                + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true");
        properties.add("maple-hush.redis.key-prefix", () -> PREFIX);
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
    @Autowired private IAuthorizationChangeService changes;
    @Autowired private ISysOperationLogService audit;
    @Autowired private FixtureService fixture;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JsonMapper json;
    @Autowired private RedisKeyBuilder keys;
    @Autowired @Qualifier("stringValueRedisTemplate") private RedisTemplate<String, String> redis;
    private final HttpClient client = HttpClient.newHttpClient();
    private long userId;
    private long roleId;
    private String access;

    @BeforeEach
    void prepare() {
        jdbc.update("INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES ('t06_user','测试本人',?,1,0)", passwords.encode("t06 strong phrase"));
        userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='t06_user'", Long.class);
        jdbc.update("INSERT INTO sys_role(role_code,role_name,status) VALUES ('t06_role','测试角色',1)");
        roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='t06_role'", Long.class);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", userId, roleId);
        grant(10); access = sessions.open(userId, 0).accessToken();
    }
    @AfterEach
    void clean() {
        jdbc.update("UPDATE sys_menu SET status=1 WHERE id=1");
        jdbc.update("DELETE FROM sys_dept WHERE name LIKE 't06_%'");
        jdbc.update("DELETE FROM sys_operation_log");
        jdbc.update("DELETE FROM auth_refresh_session WHERE user_id=?", userId);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=?", roleId);
        jdbc.update("DELETE FROM sys_role WHERE id=?", roleId);
        jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        redis.delete(keys.key("user", Long.toString(userId)));
    }

    @Test
    void httpContractsAndDirectPermissionBypass() throws Exception {
        assertEquals(401, get("/api/menu/all", null).statusCode());
        assertEquals(200, get("/api/t06/list", access).statusCode());
        var denied = post("/api/t06/write/success", "{\"password\":\"secret\"}");
        assertEquals(403, denied.statusCode(), denied.body());
        assertEquals(40300, json.readTree(denied.body()).get("code").asInt());
        var tree = json.readTree(get("/api/menu/all", access).body());
        assertEquals(0, tree.get("code").asInt()); assertTrue(tree.get("error").isNull());
        var root = tree.get("data").get(0);
        assertEquals("/system", root.get("path").asString());
        assertEquals(1, root.get("children").size());
        var page = root.get("children").get(0);
        assertEquals("user", page.get("path").asString());
        assertEquals("/system/user/list", page.get("component").asString());
        assertFalse(page.has("children"));
        for (String forbidden : java.util.List.of("id", "pid", "type", "status", "authCode")) assertFalse(page.has(forbidden));
        assertEquals("[\"system:user:list\"]", json.readTree(get("/api/auth/codes", access).body()).get("data").toString());
        jdbc.update("UPDATE sys_role SET status=0 WHERE id=?", roleId);
        assertEquals(403, get("/api/t06/list", access).statusCode());
        assertTrue(json.readTree(get("/api/menu/all", access).body()).get("data").isEmpty());
    }

    @Test
    void orphanButtonAndDisabledAncestorCannotGrant() throws Exception {
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=?", roleId); grant(11);
        assertEquals(403, post("/api/t06/write/success", "{}").statusCode());
        assertTrue(json.readTree(get("/api/auth/codes", access).body()).get("data").isEmpty());
        grant(10);
        assertEquals(200, post("/api/t06/write/success", "{}").statusCode());
        jdbc.update("UPDATE sys_menu SET status=0 WHERE id=1");
        assertEquals(403, post("/api/t06/write/success", "{}").statusCode());
    }

    @Test
    void cacheMissAndWrongVersionRebuildAndAuthorizationChangeInvalidates() throws Exception {
        String key = keys.key("user", Long.toString(userId));
        redis.delete(key);
        var info = json.readTree(get("/api/user/info", access).body()).get("data");
        assertTrue(info.get("userId").isString()); assertEquals(Long.toString(userId), info.get("userId").asString());
        assertEquals("t06_role", info.get("roles").get(0).asString());
        assertFalse(info.has("authVersion")); assertFalse(info.has("password"));
        assertNotNull(redis.opsForValue().get(key));
        redis.opsForValue().set(key, "{\"userId\":\"" + userId + "\",\"username\":\"spoof\",\"authVersion\":99}");
        assertEquals("t06_user", json.readTree(get("/api/user/info", access).body()).get("data").get("username").asString());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            grant(11); changes.roleChanged(roleId);
        });
        assertNull(redis.opsForValue().get(key));
        assertEquals(401, get("/api/auth/codes", access).statusCode());
        access = sessions.open(userId, 1).accessToken();
        assertTrue(get("/api/auth/codes", access).body().contains("system:user:create"));
        new TransactionTemplate(transactions).executeWithoutResult(status -> { changes.roleChanged(roleId); status.setRollbackOnly(); });
        assertEquals(200, get("/api/user/info", access).statusCode());
        assertEquals(1L, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, userId));
    }

    @Test
    void auditCommitsAfterBusinessAndRollbackIsRecordedWithoutSecrets() throws Exception {
        grant(11);
        assertEquals(200, post("/api/t06/write/success", "{\"password\":\"never-store-me\",\"token\":\"private\"}").statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE name='t06_success'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE success=1 AND actor_user_id=?", Integer.class, userId));
        assertEquals(409, post("/api/t06/write/rollback", "{}").statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE name='t06_rollback'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE success=0", Integer.class));
        String persisted = jdbc.queryForList("SELECT * FROM sys_operation_log").toString();
        assertFalse(persisted.contains("never-store-me")); assertFalse(persisted.contains("private"));
        assertFalse(persisted.contains("sensitive exception"));
        assertTrue(persisted.contains("/api/t06/write/{mode}"));
    }

    @Test
    void superAdminUsesDatabaseGrantsAndHasDifferentMenuScope() throws Exception {
        long adminId = jdbc.queryForObject("SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id "
                + "JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='super_admin'", Long.class);
        long adminRole = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code='super_admin'", Long.class);
        long version = jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId);
        try {
            jdbc.update("UPDATE sys_user SET must_change_password=0 WHERE id=?", adminId);
            String adminAccess = sessions.open(adminId, version).accessToken();
            var adminRoutes = json.readTree(get("/api/menu/all", adminAccess).body()).get("data");
            assertEquals(7, adminRoutes.get(0).get("children").size());
            assertEquals(25, json.readTree(get("/api/auth/codes", adminAccess).body()).get("data").size());
            assertEquals(1, json.readTree(get("/api/menu/all", access).body()).get("data").get(0).get("children").size());
            jdbc.update("DELETE FROM sys_role_menu WHERE role_id=? AND menu_id=11", adminRole);
            access = adminAccess;
            assertEquals(403, post("/api/t06/write/success", "{}").statusCode(), "内置角色也不能绕过授权表");
        } finally {
            jdbc.update("INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES (?,11)", adminRole);
            jdbc.update("UPDATE sys_user SET must_change_password=1 WHERE id=?", adminId);
            jdbc.update("DELETE FROM auth_refresh_session WHERE user_id=?", adminId);
            redis.delete(keys.key("user", Long.toString(adminId)));
        }
    }

    @Test
    void profileInvalidationAndMenuVersionChangesFollowCommit() throws Exception {
        String key = keys.key("user", Long.toString(userId));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("UPDATE sys_user SET real_name='新的姓名' WHERE id=?", userId);
            changes.profileChanged(userId);
            assertNotNull(redis.opsForValue().get(key));
        });
        assertNull(redis.opsForValue().get(key));
        assertEquals("新的姓名", json.readTree(get("/api/user/info", access).body()).get("data").get("realName").asString());
        new TransactionTemplate(transactions).executeWithoutResult(status -> changes.menusChanged());
        assertEquals(401, get("/api/menu/all", access).statusCode());
        assertEquals(1L, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, userId));
    }

    @Test
    void joinedTransactionWaitsForOuterCompletionAndRecordsRollback() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            fixture.write("outer", "password=never-store-me");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log", Integer.class));
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE name='t06_outer'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE success=0", Integer.class));
    }

    @Test
    void auditDatabaseFailureDoesNotUndoSuccessfulBusiness() throws Exception {
        grant(11); long before = audit.failureCount();
        jdbc.execute("CREATE TRIGGER t06_audit_failure BEFORE INSERT ON sys_operation_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='audit unavailable'");
        try {
            assertEquals(200, post("/api/t06/write/success", "{}").statusCode());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE name='t06_success'", Integer.class));
            assertEquals(before + 1, audit.failureCount());
        } finally { jdbc.execute("DROP TRIGGER t06_audit_failure"); }
    }

    private void grant(long menu) { jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,?)", roleId, menu); }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + access).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class Fixtures {
        @Bean FixtureService fixtureService(JdbcTemplate jdbc) { return new FixtureService(jdbc); }
        @Bean FixtureController fixtureController(FixtureService service) { return new FixtureController(service); }
    }
    @RestController
    static class FixtureController {
        private final FixtureService service;
        FixtureController(FixtureService service) { this.service = service; }
        @GetMapping("/api/t06/list") @PreAuthorize("hasAuthority('system:user:list')")
        public Result<Void> list() { return Result.ok(null); }
        @PostMapping("/api/t06/write/{mode}") @PreAuthorize("hasAuthority('system:user:create')")
        public Result<Void> write(@PathVariable String mode, @RequestBody String body) {
            service.write(mode, body); return Result.ok(null);
        }
    }
    static class FixtureService {
        private final JdbcTemplate jdbc;
        FixtureService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        @Transactional
        @OperationLog(value = "system:user:create", targetType = "user")
        public void write(String mode, String sensitiveBody) {
            jdbc.update("INSERT INTO sys_dept(name,status) VALUES (?,1)", "t06_" + mode);
            if ("rollback".equals(mode)) throw new BusinessException(ErrorCode.CONFLICT, "sensitive exception");
        }
    }
}
