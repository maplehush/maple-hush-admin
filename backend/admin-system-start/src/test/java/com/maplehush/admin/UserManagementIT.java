package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthSessionService;
import com.maplehush.admin.system.user.mapper.SysUserMapper;
import com.maplehush.admin.system.user.mapper.SysUserRoleMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

/**
 * 真实 HTTP/MySQL/Redis 验证用户管理、唯一超管与会话失效
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "T10_IT_ENABLED", matches = "true")
class UserManagementIT {
    private static final String SCHEMA =
            "maple_hush_t10_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

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
    @Autowired private SysUserMapper users;
    @MockitoSpyBean private SysUserRoleMapper userRoles;
    @Autowired private IAuthSessionService sessions;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JsonMapper json;
    @Autowired private RedisKeyBuilder keys;

    @Autowired
    @Qualifier("stringValueRedisTemplate")
    private RedisTemplate<String, String> redis;

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
        access = sessions.open(admin, version(admin)).accessToken();
    }

    @AfterEach
    void clean() {
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
        jdbc.update("UPDATE sys_user SET dept_id=NULL,remark=NULL WHERE id=?", admin);
        jdbc.update("DELETE FROM sys_dept");
    }

    @Test
    void managementOutputExplicitlyIdentifiesBuiltinMembership() throws Exception {
        // 使用角色关联判定；账号改名后标识仍须为真，普通用户必须明确返回 false。
        jdbc.update("UPDATE sys_user SET username='renamed_builtin' WHERE id=?", admin);
        try {
            var protectedUser = data(request("GET", "/api/system/user/" + admin, null, access));
            assertNotNull(protectedUser.get("superAdmin"));
            assertTrue(protectedUser.get("superAdmin").asBoolean());
            long ordinary = create("t15_ordinary", "普通用户", null, List.of());
            var ordinaryUser = data(request("GET", "/api/system/user/" + ordinary, null, access));
            assertNotNull(ordinaryUser.get("superAdmin"));
            assertFalse(ordinaryUser.get("superAdmin").asBoolean());
            var page = data(request("GET", "/api/system/user/list?page=1&pageSize=100", null, access));
            for (var row : page.get("items")) {
                assertNotNull(row.get("superAdmin"));
                assertEquals(
                        Long.toString(admin).equals(row.get("id").asString()),
                        row.get("superAdmin").asBoolean());
            }
        } finally {
            jdbc.update("UPDATE sys_user SET username='admin' WHERE id=?", admin);
        }
    }

    @Test
    void createPagingDetailsAndSafeFieldsMatchContract() throws Exception {
        long dept = dept();
        long role = role("reader", List.of(1L, 10L));
        long one = create("t10_alice", "Alice", dept, List.of(Long.toString(role)));
        long two = create("t10_bob", "Alice二", dept, List.of());
        create("t10_other", "Other", null, List.of());
        jdbc.update("UPDATE sys_user SET create_time='2026-10-12 00:00:00' WHERE id=?", one);
        jdbc.update("UPDATE sys_user SET create_time='2026-10-12 23:59:59' WHERE id=?", two);
        var response =
                request(
                        "GET",
                        "/api/system/user/list?page=2&pageSize=1&name=Alice&deptId="
                                + dept
                                + "&status=1&startTime=2026-10-12&endTime=2026-10-12",
                        null,
                        access);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(2, data(response).get("total").asInt());
        assertEquals(Long.toString(two), data(response).get("items").get(0).get("id").asString());
        var detailResponse = request("GET", "/api/system/user/" + one, null, access);
        var detail = data(detailResponse);
        assertEquals("t10_alice", detail.get("username").asString());
        assertEquals("Alice", detail.get("name").asString());
        assertEquals(Long.toString(dept), detail.get("deptId").asString());
        assertEquals(Long.toString(role), detail.get("roleIds").get(0).asString());
        assertFalse(detailResponse.body().contains("password"));
        assertNull(users.selectById(one).getPassword());
        assertFalse(detailResponse.body().contains("authVersion"));
        assertTrue(
                passwords.matches(
                        "12345678",
                        jdbc.queryForObject(
                                "SELECT password FROM sys_user WHERE id=?", String.class, one)));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT must_change_password FROM sys_user WHERE id=?",
                        Integer.class,
                        one));
        assertEquals(
                409,
                request(
                                "POST",
                                "/api/system/user",
                                form("t10_alice", "Duplicate", null, List.of()),
                                access)
                        .statusCode());
        assertEquals(
                404,
                request("GET", "/api/system/user/9223372036854775807", null, access).statusCode());
    }

    @Test
    void profileUpdateClearsSnapshotButPreservesAccountIntroductionAndSession() throws Exception {
        long user = ordinary("t10_profile", List.of());
        jdbc.update(
                "UPDATE sys_user SET introduction='本人简介',avatar_url='https://example.test/avatar' WHERE id=?",
                user);
        var session = sessions.open(user, 0);
        assertNotNull(redis.opsForValue().get(keys.key("user", Long.toString(user))));
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/user/" + user,
                                Map.of("name", "新姓名", "remark", "管理员备注"),
                                access)
                        .statusCode());
        assertNull(redis.opsForValue().get(keys.key("user", Long.toString(user))));
        assertEquals(0, version(user));
        var current = data(request("GET", "/api/user/info", null, session.accessToken()));
        assertEquals("新姓名", current.get("realName").asString());
        assertEquals("本人简介", current.get("introduction").asString());
        assertEquals("https://example.test/avatar", current.get("avatar").asString());
        assertEquals("t10_profile", current.get("username").asString());
        assertEquals(
                400,
                request(
                                "PUT",
                                "/api/system/user/" + user,
                                Map.of("name", "x", "password", "injected"),
                                access)
                        .statusCode());
        assertEquals(
                400,
                request(
                                "PUT",
                                "/api/system/user/" + user,
                                Map.of("name", "x", "roleIds", List.of(Long.toString(builtin))),
                                access)
                        .statusCode());
        assertEquals(
                400,
                request(
                                "PUT",
                                "/api/system/user/" + user,
                                Map.of("name", "x", "avatar_url", "injected"),
                                access)
                        .statusCode());
    }

    @Test
    void statusAndRoleChangesInvalidateAccessRefreshAndCache() throws Exception {
        long user = ordinary("t10_member", List.of());
        var old = sessions.open(user, 0);
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/user/" + user,
                                Map.of("name", "Member", "status", 0),
                                access)
                        .statusCode());
        assertEquals(1, version(user));
        assertEquals(401, request("GET", "/api/user/info", null, old.accessToken()).statusCode());
        assertThrows(
                RuntimeException.class,
                () -> sessions.refresh(old.refreshToken(), "127.0.0.1", "test"));
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/user/" + user,
                                Map.of("name", "Member", "status", 1),
                                access)
                        .statusCode());
        long role = role("t10_reader", List.of(1L, 10L));
        var beforeGrant = sessions.open(user, version(user));
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/user/" + user + "/roles",
                                Map.of("roleIds", List.of(Long.toString(role))),
                                access)
                        .statusCode());
        assertEquals(3, version(user));
        assertEquals(List.of(role), roleIds(user));
        assertEquals(
                401,
                request("GET", "/api/auth/codes", null, beforeGrant.accessToken()).statusCode());
        assertNull(redis.opsForValue().get(keys.key("user", Long.toString(user))));
        assertEquals(200, request("GET", "/api/auth/codes", null, access).statusCode());
    }

    @Test
    void resetOnlyKicksTargetAndTemporaryLoginRequiresNewPassword() throws Exception {
        long user = ordinary("t10_reset", List.of());
        var old = sessions.open(user, 0);
        assertEquals(
                200,
                request("POST", "/api/system/user/" + user + "/reset-password", null, access)
                        .statusCode());
        assertEquals(1, version(user));
        assertEquals(401, request("GET", "/api/user/info", null, old.accessToken()).statusCode());
        assertThrows(
                RuntimeException.class,
                () -> sessions.refresh(old.refreshToken(), "127.0.0.1", "test"));
        assertEquals(200, request("GET", "/api/user/info", null, access).statusCode());
        var captcha = data(request("GET", "/api/auth/captcha", null, null));
        String id = captcha.get("captchaId").asString();
        String answer = redis.opsForValue().get(keys.key("captcha", id));
        var login =
                data(
                        request(
                                "POST",
                                "/api/auth/login",
                                Map.of(
                                        "username",
                                        "t10_reset",
                                        "password",
                                        "12345678",
                                        "captchaId",
                                        id,
                                        "captchaCode",
                                        answer),
                                null));
        assertTrue(login.get("passwordChangeRequired").asBoolean());
        assertFalse(login.has("accessToken"));
        String change = login.get("passwordChangeToken").asString();
        assertEquals(401, request("GET", "/api/user/info", null, change).statusCode());
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/auth/initial-password",
                                Map.of("newPassword", "t10 changed passphrase"),
                                change)
                        .statusCode());
        assertTrue(
                passwords.matches(
                        "t10 changed passphrase",
                        jdbc.queryForObject(
                                "SELECT password FROM sys_user WHERE id=?", String.class, user)));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT must_change_password FROM sys_user WHERE id=?",
                        Integer.class,
                        user));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:user:reset-password' AND success=1",
                        Integer.class));
        assertFalse(
                jdbc.queryForList("SELECT summary FROM sys_operation_log", String.class)
                        .toString()
                        .contains("12345678"));
    }

    @Test
    void builtinHolderIsProtectedByAssociationNotUsername() throws Exception {
        jdbc.update("UPDATE sys_user SET username='renamed_root' WHERE id=?", admin);
        try {
            assertEquals(
                    403, request("DELETE", "/api/system/user/" + admin, null, access).statusCode());
            assertEquals(
                    403,
                    request("POST", "/api/system/user/" + admin + "/reset-password", null, access)
                            .statusCode());
            assertEquals(
                    403,
                    request(
                                    "PUT",
                                    "/api/system/user/" + admin,
                                    Map.of("name", "Root", "status", 0),
                                    access)
                            .statusCode());
            assertEquals(
                    403,
                    request(
                                    "PUT",
                                    "/api/system/user/" + admin + "/roles",
                                    Map.of("roleIds", List.of()),
                                    access)
                            .statusCode());
            assertEquals(
                    200,
                    request(
                                    "PUT",
                                    "/api/system/user/" + admin,
                                    Map.of("name", "Root profile"),
                                    access)
                            .statusCode());
            assertEquals(List.of(builtin), roleIds(admin));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT status FROM sys_user WHERE id=?", Integer.class, admin));
        } finally {
            jdbc.update("UPDATE sys_user SET username='admin' WHERE id=?", admin);
        }
    }

    @Test
    void forbiddenRolesAndOutOfScopeTargetsCannotBeUsedForEscalation() throws Exception {
        long managerRole = role("t10_manager", List.of(1L, 10L, 11L, 12L, 13L, 14L, 15L));
        long manager = ordinary("t10_manager", List.of(managerRole));
        String token = sessions.open(manager, 0).accessToken();
        long powerful = role("t10_powerful", List.of(1L, 20L, 21L));
        long target = ordinary("t10_high", List.of(powerful));
        assertEquals(
                403,
                request(
                                "POST",
                                "/api/system/user",
                                form(
                                        "t10_escalation",
                                        "Escalation",
                                        null,
                                        List.of(Long.toString(powerful))),
                                token)
                        .statusCode());
        assertEquals(
                403,
                request("POST", "/api/system/user/" + target + "/reset-password", null, token)
                        .statusCode());
        assertEquals(
                403,
                request(
                                "PUT",
                                "/api/system/user/" + target + "/roles",
                                Map.of("roleIds", List.of()),
                                token)
                        .statusCode());
        assertEquals(
                403,
                request(
                                "POST",
                                "/api/system/user",
                                form("t10_super", "Super", null, List.of(Long.toString(builtin))),
                                access)
                        .statusCode());
        long disabled = role("t10_disabled", List.of(1L, 10L));
        jdbc.update("UPDATE sys_role SET status=0 WHERE id=?", disabled);
        assertEquals(
                400,
                request(
                                "POST",
                                "/api/system/user",
                                form(
                                        "t10_disabled",
                                        "Disabled",
                                        null,
                                        List.of(Long.toString(disabled))),
                                access)
                        .statusCode());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user WHERE username IN ('t10_escalation','t10_super','t10_disabled')",
                        Integer.class));
    }

    @Test
    void createWithoutAssignPermissionAndEnablingPrivilegedAccountAreRejected() throws Exception {
        long limited = role("t10_limited", List.of(1L, 10L, 11L, 12L));
        long operator = ordinary("t10_limited", List.of(limited));
        String token = sessions.open(operator, 0).accessToken();
        assertEquals(
                200,
                request(
                                "POST",
                                "/api/system/user",
                                form("t10_no_roles", "No roles", null, List.of()),
                                token)
                        .statusCode());
        assertEquals(
                403,
                request(
                                "POST",
                                "/api/system/user",
                                form(
                                        "t10_has_roles",
                                        "Has roles",
                                        null,
                                        List.of(Long.toString(limited))),
                                token)
                        .statusCode());
        long target = ordinary("t10_inactive", List.of(limited));
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", target);
        assertEquals(
                403,
                request(
                                "PUT",
                                "/api/system/user/" + target,
                                Map.of("name", "Inactive", "status", 1),
                                token)
                        .statusCode());
    }

    @Test
    void deleteCleansRelationsAndSessionsButPreservesAuditSnapshots() throws Exception {
        long role = role("t10_reader", List.of(1L, 10L));
        long user = ordinary("t10_delete", List.of(role));
        var old = sessions.open(user, 0);
        jdbc.update(
                "INSERT INTO sys_login_log(user_id,username,event_type,success) VALUES (?, 't10_delete','LOGIN',1)",
                user);
        assertEquals(200, request("DELETE", "/api/system/user/" + user, null, access).statusCode());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user WHERE id=?", Integer.class, user));
        assertEquals(List.of(), roleIds(user));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=?",
                        Integer.class,
                        user));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_login_log WHERE user_id=?", Integer.class, user));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:user:delete' AND target_id=? AND success=1",
                        Integer.class,
                        Long.toString(user)));
        assertEquals(401, request("GET", "/api/user/info", null, old.accessToken()).statusCode());
        assertNull(redis.opsForValue().get(keys.key("user", Long.toString(user))));
    }

    @Test
    void concurrentBuiltinGrantsAreBothRejectedAndOrdinaryGrantsAreAtomic() throws Exception {
        long user = ordinary("t10_concurrent", List.of());
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one =
                    executor.submit(
                            () -> {
                                go.await();
                                return request(
                                                "PUT",
                                                "/api/system/user/" + user + "/roles",
                                                Map.of("roleIds", List.of(Long.toString(builtin))),
                                                access)
                                        .statusCode();
                            });
            var two =
                    executor.submit(
                            () -> {
                                go.await();
                                return request(
                                                "PUT",
                                                "/api/system/user/" + user + "/roles",
                                                Map.of("roleIds", List.of(Long.toString(builtin))),
                                                access)
                                        .statusCode();
                            });
            go.countDown();
            assertEquals(403, one.get());
            assertEquals(403, two.get());
        }
        assertEquals(List.of(), roleIds(user));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user_role WHERE role_id=?",
                        Integer.class,
                        builtin));
        long first = role("t10_first", List.of(1L, 10L));
        long second = role("t10_second", List.of(1L, 40L));
        CountDownLatch ordinaryGo = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one =
                    executor.submit(
                            () -> {
                                ordinaryGo.await();
                                return request(
                                                "PUT",
                                                "/api/system/user/" + user + "/roles",
                                                Map.of("roleIds", List.of(Long.toString(first))),
                                                access)
                                        .statusCode();
                            });
            var two =
                    executor.submit(
                            () -> {
                                ordinaryGo.await();
                                return request(
                                                "PUT",
                                                "/api/system/user/" + user + "/roles",
                                                Map.of("roleIds", List.of(Long.toString(second))),
                                                access)
                                        .statusCode();
                            });
            ordinaryGo.countDown();
            assertEquals(200, one.get());
            assertEquals(200, two.get());
        }
        assertEquals(1, roleIds(user).size());
        assertEquals(2, version(user));
    }

    @Test
    void invalidInputsAndInterfacePermissionsReturnStableErrors() throws Exception {
        assertEquals(401, request("GET", "/api/system/user/list", null, null).statusCode());
        long role = role("t10_empty", List.of());
        String limited = sessions.open(ordinary("t10_empty", List.of(role)), 0).accessToken();
        assertEquals(403, request("GET", "/api/system/user/list", null, limited).statusCode());
        assertEquals(
                400,
                request("GET", "/api/system/user/list?pageSize=101", null, access).statusCode());
        assertEquals(
                400,
                request("GET", "/api/system/user/list?startTime=2026-02-30", null, access)
                        .statusCode());
        assertEquals(400, request("GET", "/api/system/user/-1", null, access).statusCode());
        assertEquals(
                400,
                request(
                                "POST",
                                "/api/system/user",
                                form("t10_bad", "Bad", 9223372036854775807L, List.of()),
                                access)
                        .statusCode());
        var injected = form("t10_injected", "Bad", null, List.of());
        injected.put("password", "plaintext");
        assertEquals(400, request("POST", "/api/system/user", injected, access).statusCode());
    }

    @Test
    void selfResetInvalidatesOnlyTheOrdinaryOperator() throws Exception {
        long role = role("t10_self_role", List.of(1L, 10L, 14L));
        long user = ordinary("t10_self", List.of(role));
        String token = sessions.open(user, 0).accessToken();
        assertEquals(
                200,
                request("POST", "/api/system/user/" + user + "/reset-password", null, token)
                        .statusCode());
        assertEquals(401, request("GET", "/api/user/info", null, token).statusCode());
        assertEquals(200, request("GET", "/api/user/info", null, access).statusCode());
        assertEquals(1, version(user));
    }

    @Test
    void roleWriteFailureRollsBackRelationsVersionAndCacheButKeepsFailureAudit() throws Exception {
        long first = role("t10_before", List.of(1L, 10L));
        long second = role("t10_after", List.of(1L, 40L));
        long user = ordinary("t10_rollback", List.of(first));
        var old = sessions.open(user, 0);
        doThrow(new DataIntegrityViolationException("injected relation failure"))
                .when(userRoles)
                .saveRoles(eq(user), anyList());
        assertEquals(
                409,
                request(
                                "PUT",
                                "/api/system/user/" + user + "/roles",
                                Map.of("roleIds", List.of(Long.toString(second))),
                                access)
                        .statusCode());
        assertEquals(List.of(first), roleIds(user));
        assertEquals(0, version(user));
        assertNotNull(redis.opsForValue().get(keys.key("user", Long.toString(user))));
        assertEquals(200, request("GET", "/api/user/info", null, old.accessToken()).statusCode());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_operation_log WHERE operation_code='system:user:assign-role' AND success=0 AND target_id=?",
                        Integer.class,
                        Long.toString(user)));
    }

    @Test
    void existingDisabledRoleMayBeRetainedButCannotBeNewlyAssigned() throws Exception {
        long disabled = role("t10_existing_disabled", List.of(1L, 10L));
        long target = ordinary("t10_existing_disabled", List.of(disabled));
        jdbc.update("UPDATE sys_role SET status=0 WHERE id=?", disabled);
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/user/" + target + "/roles",
                                Map.of("roleIds", List.of(Long.toString(disabled))),
                                access)
                        .statusCode());
        assertEquals(0, version(target));
        long extra = role("t10_extra", List.of(1L, 40L));
        assertEquals(
                200,
                request(
                                "PUT",
                                "/api/system/user/" + target + "/roles",
                                Map.of(
                                        "roleIds",
                                        List.of(Long.toString(disabled), Long.toString(extra))),
                                access)
                        .statusCode());
        assertEquals(List.of(disabled, extra), roleIds(target));
        assertEquals(1, version(target));
        long other = ordinary("t10_new_disabled", List.of());
        assertEquals(
                400,
                request(
                                "PUT",
                                "/api/system/user/" + other + "/roles",
                                Map.of("roleIds", List.of(Long.toString(disabled))),
                                access)
                        .statusCode());
        assertEquals(List.of(), roleIds(other));
    }

    private long create(String username, String name, Long dept, List<String> roles)
            throws Exception {
        var response =
                request("POST", "/api/system/user", form(username, name, dept, roles), access);
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(response.body().contains("12345678"));
        return Long.parseLong(data(response).get("id").asString());
    }

    private Map<String, Object> form(String username, String name, Long dept, List<String> roles) {
        var body = new HashMap<String, Object>();
        body.put("username", username);
        body.put("name", name);
        body.put("deptId", dept == null ? null : Long.toString(dept));
        body.put("roleIds", roles);
        body.put("status", 1);
        body.put("remark", "管理备注");
        return body;
    }

    private long role(String code, List<Long> menus) {
        jdbc.update(
                "INSERT INTO sys_role(role_code,role_name,is_builtin,status) VALUES (?, ?,0,1)",
                code,
                code);
        long id =
                jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class, code);
        for (Long menu : menus) {
            jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES (?,?)", id, menu);
        }
        return id;
    }

    private long ordinary(String username, List<Long> roles) {
        jdbc.update(
                "INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES (?, '普通用户',?,1,0)",
                username,
                passwords.encode("t10 existing passphrase"));
        long id =
                jdbc.queryForObject(
                        "SELECT id FROM sys_user WHERE username=?", Long.class, username);
        for (Long role : roles) {
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", id, role);
        }
        return id;
    }

    private long dept() {
        jdbc.update("INSERT INTO sys_dept(name,status,sort) VALUES ('T10部门',1,0)");
        return jdbc.queryForObject("SELECT id FROM sys_dept WHERE name='T10部门'", Long.class);
    }

    private long version(long user) {
        return jdbc.queryForObject(
                "SELECT auth_version FROM sys_user WHERE id=?", Long.class, user);
    }

    private List<Long> roleIds(long user) {
        return jdbc.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id=? ORDER BY role_id",
                Long.class,
                user);
    }

    private JsonNode data(HttpResponse<String> response) {
        return json.readTree(response.body()).get("data");
    }

    private HttpResponse<String> request(String method, String path, Object body, String token)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if ("POST".equals(method)) {
            builder.header("Origin", TestOrigin.allowed(port));
        }
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
