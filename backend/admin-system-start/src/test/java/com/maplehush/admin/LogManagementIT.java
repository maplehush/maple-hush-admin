package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.service.IAuthService;
import com.maplehush.admin.system.auth.service.IAuthSessionService;
import com.maplehush.admin.system.log.mapper.SysLoginLogMapper;
import com.maplehush.admin.system.log.mapper.SysOperationLogMapper;
import com.maplehush.admin.system.log.service.ISysLogCleanupService;
import com.maplehush.admin.system.log.service.ISysLoginLogService;
import com.maplehush.admin.system.log.service.ISysOperationLogService;
import com.maplehush.admin.system.log.service.impl.SysLogCleanupServiceImpl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 真实 HTTP/MySQL 验证日志分表查询、权限、独立审计和清理
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "T12_IT_ENABLED", matches = "true")
class LogManagementIT {
    private static final String SCHEMA =
            "maple_hush_t12_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                "jdbc:mysql://127.0.0.1:3306/", "root", "123456");
                var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + SCHEMA);
        }
        properties.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://127.0.0.1:3306/"
                                + SCHEMA
                                + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true");
        properties.add("maple-hush.redis.key-prefix", () -> SCHEMA);
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection =
                        DriverManager.getConnection(
                                "jdbc:mysql://127.0.0.1:3306/", "root", "123456");
                var sql = connection.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IAuthSessionService sessions;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JsonMapper json;
    @Autowired private RedisKeyBuilder keys;

    @Autowired
    @Qualifier("stringValueRedisTemplate")
    private RedisTemplate<String, String> redis;

    @Autowired private ISysLogCleanupService cleanupService;
    @Autowired private SysLoginLogMapper loginMapper;
    @Autowired private SysOperationLogMapper operationMapper;
    @Autowired private ISysLoginLogService loginAudit;
    @Autowired private ISysOperationLogService operationAudit;
    @Autowired private IAuthService auth;
    @Autowired private IAuthRedisService authRedis;

    private final HttpClient client = HttpClient.newHttpClient();
    private long admin;
    private long builtin;
    private String access;

    @BeforeEach
    void prepare() {
        admin = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class);
        builtin =
                jdbc.queryForObject(
                        "SELECT id FROM sys_role WHERE role_code='super_admin'", Long.class);
        jdbc.update("UPDATE sys_user SET must_change_password=0 WHERE id=?", admin);
        access = sessions.open(admin, 0).accessToken();
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM sys_dict_item");
        jdbc.update("DELETE FROM sys_dict_type");
        jdbc.update("DELETE FROM auth_refresh_session");
        jdbc.update("DELETE FROM sys_login_log");
        jdbc.update("DELETE FROM sys_operation_log");
        jdbc.update("DELETE FROM sys_user_role WHERE role_id<>?", builtin);
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id<>?", builtin);
        jdbc.update("DELETE FROM sys_role WHERE id<>?", builtin);
        for (Long id : jdbc.queryForList("SELECT id FROM sys_user", Long.class)) {
            redis.delete(keys.key("user", id.toString()));
        }
        jdbc.update("DELETE FROM sys_user WHERE id<>?", admin);
    }

    @Test
    void loginPagingInclusiveDatesFiltersAndSnapshots() throws Exception {
        login("alice", "LOGIN", true, "2026-10-01 00:00:00");
        login("alice", "LOGOUT", true, "2026-10-02 23:59:59");
        login("alice", "LOGIN", false, "2026-10-03 00:00:00");
        var response =
                get(
                        "/login-log/list?page=2&pageSize=1&username=alice&success=true&startTime=2026-10-01&endTime=2026-10-02",
                        access);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(2, data(response).get("total").asInt());
        var row = data(response).get("items").get(0);
        assertEquals("LOGIN", row.get("eventType").asString());
        assertEquals("2026-10-01 00:00:00", row.get("createTime").asString());
        assertTrue(row.get("id").isString());
        assertEquals(
                1,
                data(get("/login-log/list?eventType=LOGOUT&traceId=trace-test", access))
                        .get("total")
                        .asInt());
        assertEquals(1, data(get("/login-log/list?success=false", access)).get("total").asInt());
        assertEquals(0, scalar("SELECT COUNT(*) FROM sys_operation_log"));
    }

    @Test
    void operationPagingStableSortAndSummaryExcludeUntrustedSecrets() throws Exception {
        for (int i = 0; i < 3; i++) {
            jdbc.update(
                    "INSERT INTO sys_operation_log(actor_user_id,actor_username,operation_code,target_type,target_id,success,summary,request_method,request_path,client_ip,trace_id,create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    admin,
                    "alice",
                    "system:dict:update",
                    "dict_item",
                    "7",
                    i != 1,
                    "password=PRIVATE token=SECRET",
                    "PUT",
                    "/api/system/dict/item/{id}",
                    "127.0.0.1",
                    "trace-test",
                    "2026-10-02 10:00:00");
        }
        var result =
                get(
                        "/operation-log/list?pageSize=2&actorUsername=alice&operationCode=system%3Adict%3Aupdate&sortBy=id&sortOrder=asc&startTime=2026-10-02%2010:00:00&endTime=2026-10-02%2010:00:00",
                        access);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(3, data(result).get("total").asInt());
        var rows = data(result).get("items");
        assertTrue(
                Long.parseLong(rows.get(0).get("id").asString())
                        < Long.parseLong(rows.get(1).get("id").asString()));
        assertEquals("操作成功", rows.get(0).get("summary").asString());
        assertEquals("操作失败", rows.get(1).get("summary").asString());
        assertEquals(Long.toString(admin), rows.get(0).get("actorUserId").asString());
        assertFalse(result.body().contains("PRIVATE"));
        assertFalse(result.body().contains("SECRET"));
        assertEquals(
                1,
                data(get("/operation-log/list?success=false&traceId=trace-test", access))
                        .get("total")
                        .asInt());
        assertEquals(0, scalar("SELECT COUNT(*) FROM sys_login_log"));
    }

    @Test
    void separatePermissionsAndAnonymousAccess() throws Exception {
        String loginReader = reader(60), operationReader = reader(70);
        assertEquals(200, get("/login-log/list", loginReader).statusCode());
        assertEquals(403, get("/operation-log/list", loginReader).statusCode());
        assertEquals(403, get("/login-log/list", operationReader).statusCode());
        assertEquals(200, get("/operation-log/list", operationReader).statusCode());
        assertEquals(401, get("/login-log/list", null).statusCode());
        assertEquals(401, get("/operation-log/list", null).statusCode());
    }

    @Test
    void invalidPagingDatesAndSqlSortAreRejected() throws Exception {
        for (String path : List.of("/login-log/list", "/operation-log/list")) {
            for (String query :
                    List.of(
                            "page=0",
                            "page=2147483648",
                            "pageSize=101",
                            "pageSize=-1",
                            "sortBy=create_time",
                            "sortBy=id%20desc",
                            "sortOrder=drop",
                            "success=2",
                            "startTime=2026-02-30",
                            "endTime=bad",
                            "startTime=2026-10-03&endTime=2026-10-02")) {
                var response = get(path + "?" + query, access);
                assertEquals(400, response.statusCode(), path + query + response.body());
            }
        }
        assertEquals(400, get("/login-log/list?eventType=SECRET", access).statusCode());
        assertEquals(
                400,
                get("/operation-log/list?operationCode=" + "a".repeat(121), access).statusCode());
    }

    @Test
    void authenticationAuditIncludesCaptchaCredentialsLimitAndLogoutWithoutSecrets() {
        String ip = "t12-" + UUID.randomUUID();
        var bad = authRedis.issueCaptcha(ip);
        assertThrows(
                BusinessException.class,
                () ->
                        auth.login(
                                "admin",
                                "private-password",
                                bad.captchaId(),
                                "wrong-answer",
                                ip,
                                "test-browser"));
        var valid = authRedis.issueCaptcha(ip);
        String answer = redis.opsForValue().get(keys.key("captcha", valid.captchaId()));
        assertThrows(
                BusinessException.class,
                () ->
                        auth.login(
                                "admin",
                                "private-password",
                                valid.captchaId(),
                                answer,
                                ip,
                                "test-browser"));
        jdbc.update(
                "UPDATE sys_user SET password=? WHERE id=?",
                passwords.encode("t12 valid password"),
                admin);
        var good = authRedis.issueCaptcha(ip);
        String goodAnswer = redis.opsForValue().get(keys.key("captcha", good.captchaId()));
        var logged =
                auth.login(
                        "admin",
                        "t12 valid password",
                        good.captchaId(),
                        goodAnswer,
                        ip,
                        "test-browser");
        sessions.logout(logged.refreshToken(), ip, "test-browser");
        for (int i = 0; i < 8; i++) {
            authRedis.recordCredentialFailure("rate-test");
        }
        assertThrows(
                BusinessException.class,
                () ->
                        auth.login(
                                "rate-test",
                                "private-password",
                                "private-captcha",
                                "private-answer",
                                ip,
                                "test-browser"));
        assertTrue(
                scalar("SELECT COUNT(*) FROM sys_login_log WHERE reason_code='CAPTCHA_INVALID'")
                        >= 1);
        assertTrue(
                scalar("SELECT COUNT(*) FROM sys_login_log WHERE reason_code='RATE_LIMITED'") >= 1);
        assertEquals(
                1,
                scalar(
                        "SELECT COUNT(*) FROM sys_login_log WHERE event_type='LOGOUT' AND success=1"));
        String stored = jdbc.queryForList("SELECT * FROM sys_login_log").toString();
        for (String secret :
                List.of(
                        "private-password",
                        "private-captcha",
                        "private-answer",
                        logged.accessToken(),
                        logged.refreshToken(),
                        good.captchaId())) {
            assertFalse(stored.contains(secret));
        }
        assertEquals(0, scalar("SELECT COUNT(*) FROM sys_operation_log"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void auditFailuresAreObservableWithoutUndoingBusiness(CapturedOutput output) throws Exception {
        long before = operationAudit.failureCount();
        jdbc.execute(
                "CREATE TRIGGER t12_op_fail BEFORE INSERT ON sys_operation_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-db-error'");
        try {
            var result =
                    request(
                            "POST",
                            "/api/system/dict/type",
                            Map.of("dictCode", "audited", "name", "审计故障仍成功"),
                            access);
            assertEquals(200, result.statusCode(), result.body());
            assertEquals(1, scalar("SELECT COUNT(*) FROM sys_dict_type"));
            assertEquals(before + 1, operationAudit.failureCount());
        } finally {
            jdbc.execute("DROP TRIGGER t12_op_fail");
        }
        long old = loginAudit.failureCount();
        jdbc.execute(
                "CREATE TRIGGER t12_login_fail BEFORE INSERT ON sys_login_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-db-error'");
        try {
            var session = sessions.open(admin, 0);
            sessions.logout(session.refreshToken(), "127.0.0.1", "test-browser");
            assertTrue(
                    scalar(
                                    "SELECT COUNT(*) FROM auth_refresh_session WHERE revoked_time IS NOT NULL")
                            >= 1);
            assertEquals(old + 1, loginAudit.failureCount());
        } finally {
            jdbc.execute("DROP TRIGGER t12_login_fail");
        }
        assertFalse(output.getAll().contains("private-db-error"));
        assertTrue(output.getAll().contains("Login audit write failed"));
        assertTrue(output.getAll().contains("Operation audit write failed"));
    }

    @Test
    void rollbackLeavesIndependentFixedAudit() throws Exception {
        request(
                "POST",
                "/api/system/dict/type",
                Map.of("dictCode", "same", "name", "first"),
                access);
        assertEquals(
                409,
                request(
                                "POST",
                                "/api/system/dict/type",
                                Map.of("dictCode", "same", "name", "password=private-value"),
                                access)
                        .statusCode());
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_dict_type"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_operation_log WHERE success=0"));
        assertFalse(
                jdbc.queryForList("SELECT * FROM sys_operation_log")
                        .toString()
                        .contains("private-value"));
    }

    @Test
    void cleanupRetainsRecentRecordsAndBoundsEachTableToTenBatches() throws Exception {
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        login(
                "expired",
                "LOGIN",
                true,
                now.minusDays(91).toString().replace('T', ' ').substring(0, 19));
        login(
                "kept",
                "LOGIN",
                true,
                now.minusDays(89).toString().replace('T', ' ').substring(0, 19));
        jdbc.update(
                "INSERT INTO sys_operation_log(operation_code,success,create_time) VALUES ('system:dict:create',1,?),('system:dict:update',0,?)",
                now.minusDays(181),
                now.minusDays(179));
        cleanup();
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_login_log"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_operation_log"));
        assertEquals(
                "kept", jdbc.queryForObject("SELECT username FROM sys_login_log", String.class));
        jdbc.execute(
                "INSERT INTO sys_login_log(username,event_type,success,create_time) WITH RECURSIVE seq(n) AS (SELECT 0 UNION ALL SELECT n+1 FROM seq WHERE n<100) SELECT 'bulk','LOGIN',0,'2000-01-01' FROM seq a CROSS JOIN seq b");
        cleanup();
        assertEquals(201, scalar("SELECT COUNT(*) FROM sys_login_log WHERE username='bulk'"));
        cleanup();
        assertEquals(0, scalar("SELECT COUNT(*) FROM sys_login_log WHERE username='bulk'"));
    }

    @Test
    void cleanupFailureStillProcessesOtherTable() throws Exception {
        login("old", "LOGIN", false, "2000-01-01 00:00:00");
        jdbc.update(
                "INSERT INTO sys_operation_log(operation_code,success,create_time) VALUES ('system:dict:create',1,'2000-01-01')");
        jdbc.execute(
                "CREATE TRIGGER t12_cleanup_fail BEFORE DELETE ON sys_login_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-error'");
        try {
            cleanup();
            assertEquals(1, scalar("SELECT COUNT(*) FROM sys_login_log"));
            assertEquals(0, scalar("SELECT COUNT(*) FROM sys_operation_log"));
        } finally {
            jdbc.execute("DROP TRIGGER t12_cleanup_fail");
        }
    }

    @Test
    void cleanupConfigurationCanDisableOrOverrideRetention() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        login(
                "old",
                "LOGIN",
                true,
                now.minusDays(11).toString().replace('T', ' ').substring(0, 19));
        jdbc.update(
                "INSERT INTO sys_operation_log(operation_code,success,create_time) VALUES ('system:dict:create',1,?)",
                now.minusDays(21));
        var disabled = new SysLogCleanupServiceImpl(loginMapper, operationMapper, false, 10, 20);
        assertEquals(0, disabled.cleanupExpiredLogs());
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_login_log"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_operation_log"));
        var custom = new SysLogCleanupServiceImpl(loginMapper, operationMapper, true, 10, 20);
        assertEquals(2, custom.cleanupExpiredLogs());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SysLogCleanupServiceImpl(loginMapper, operationMapper, true, 0, 20));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SysLogCleanupServiceImpl(loginMapper, operationMapper, true, 10, 3651));
    }

    @Test
    void cleanupStrictCutoffAndWriterSummaryAreEnforced() {
        var cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
        login("boundary", "LOGIN", true, "2026-01-01 00:00:00");
        login("old", "LOGIN", true, "2025-12-31 23:59:59");
        assertEquals(1, loginMapper.deleteExpiredBefore(cutoff));
        assertEquals(
                "boundary",
                jdbc.queryForObject("SELECT username FROM sys_login_log", String.class));
        jdbc.update(
                "INSERT INTO sys_operation_log(operation_code,success,create_time) VALUES ('system:dict:create',1,'2026-01-01'),('system:dict:update',0,'2025-12-31 23:59:59')");
        assertEquals(1, operationMapper.deleteExpiredBefore(cutoff));
        operationAudit.record(
                new ISysOperationLogService.Entry(
                        admin,
                        "admin",
                        "system:dict:create",
                        "dict_type",
                        null,
                        true,
                        "password=private-payload",
                        "POST",
                        "/api/system/dict/type",
                        "127.0.0.1",
                        "test-summary"));
        assertEquals(
                "操作成功",
                jdbc.queryForObject(
                        "SELECT summary FROM sys_operation_log WHERE trace_id='test-summary'",
                        String.class));
    }

    private void cleanup() throws Exception {
        cleanupService.cleanupExpiredLogs();
    }

    private void login(String name, String event, boolean success, String time) {
        jdbc.update(
                "INSERT INTO sys_login_log(username,event_type,success,reason_code,client_ip,user_agent,trace_id,create_time) VALUES (?,?,?,?,?,?,?,?)",
                name,
                event,
                success,
                "OK",
                "127.0.0.1",
                "browser",
                "trace-test",
                time);
    }

    private String reader(int menu) {
        String name = "log_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES (?,'日志测试',?,1,0)",
                name,
                passwords.encode("log test phrase"));
        long user =
                jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
        jdbc.update(
                "INSERT INTO sys_role(role_code,role_name,is_builtin,status) VALUES (?,'日志查看',0,1)",
                name);
        long role =
                jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, name);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", user, role);
        jdbc.update(
                "INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,1),(?,?)", role, role, menu);
        return sessions.open(user, 0).accessToken();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return request("GET", "/api/system" + path, null, token);
    }

    private int scalar(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private JsonNode data(HttpResponse<String> response) {
        return json.readTree(response.body()).get("data");
    }

    private HttpResponse<String> request(String method, String path, Object body, String token)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(
                builder.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(
                                                json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
