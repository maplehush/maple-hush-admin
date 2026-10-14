package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthSessionService;
import com.maplehush.admin.system.user.service.IAvatarStorageService;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

/**
 * 真实 HTTP/MySQL/Redis 验证本人资料、改密和头像事务；外部存储使用测试替身
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T13_IT_ENABLED", matches = "true")
class ProfileManagementIT {
    private static final String SCHEMA =
            "maple_hush_t13_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

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

    @MockitoBean private IAvatarStorageService storage;

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
        jdbc.update(
                "UPDATE sys_user SET must_change_password=0,auth_version=0,password=? WHERE id=?",
                passwords.encode("previous valid password"),
                admin);
        redis.delete(keys.key("password_change", Long.toString(admin)));
        reset(storage);
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
    void ordinaryUserUpdatesOnlyOwnProfileAndInvalidatesSnapshot() throws Exception {
        long user = ordinary();
        String token = sessions.open(user, 0).accessToken();
        request("GET", "/api/user/info", null, token);
        String cached = redis.opsForValue().get(keys.key("user", Long.toString(user)));
        var result =
                request(
                        "PUT",
                        "/api/user/profile",
                        Map.of("realName", "新姓名", "introduction", "个人介绍"),
                        token);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(Long.toString(user), data(result).get("userId").asString());
        assertEquals("新姓名", data(result).get("realName").asString());
        assertEquals(
                "个人介绍",
                jdbc.queryForObject(
                        "SELECT introduction FROM sys_user WHERE id=?", String.class, user));
        assertEquals(0, scalar("SELECT auth_version FROM sys_user WHERE id=?", user));
        assertFalse(redis.hasKey(keys.key("user", Long.toString(user))));
        // 模拟缓存失效失败或并发回填旧快照，MySQL 资料仍须生效。
        redis.opsForValue().set(keys.key("user", Long.toString(user)), cached);
        var info = data(request("GET", "/api/user/info", null, token));
        assertEquals("新姓名", info.get("realName").asString());
        assertEquals("个人介绍", info.get("introduction").asString());
        assertEquals(
                "管理员备注",
                jdbc.queryForObject("SELECT remark FROM sys_user WHERE id=?", String.class, user));
    }

    @Test
    void unknownFieldsAndUnauthorizedRequestsCannotChangeIdentityOrRoles() throws Exception {
        for (String field :
                List.of("userId", "username", "roles", "deptId", "status", "avatar", "remark")) {
            var form = new java.util.HashMap<String, Object>();
            form.put("realName", "不应修改");
            form.put(field, "unexpected");
            assertEquals(
                    400, request("PUT", "/api/user/profile", form, access).statusCode(), field);
        }
        assertEquals(
                400,
                request("PUT", "/api/user/profile", Map.of("realName", " "), access).statusCode());
        assertEquals(
                400,
                request(
                                "PUT",
                                "/api/user/profile",
                                Map.of("realName", "x", "introduction", "x".repeat(501)),
                                access)
                        .statusCode());
        assertEquals(
                401,
                request("PUT", "/api/user/profile", Map.of("realName", "x"), null).statusCode());
        assertEquals(
                401,
                request(
                                "PUT",
                                "/api/user/password",
                                Map.of("oldPassword", "x", "newPassword", "new valid password"),
                                null)
                        .statusCode());
        assertEquals(401, upload(image("png"), null, "").statusCode());
    }

    @Test
    void changePasswordRevokesEveryOwnSessionAndPreservesOtherUsers() throws Exception {
        long other = ordinary();
        String otherToken = sessions.open(other, 0).accessToken();
        var second = sessions.open(admin, 0);
        var result =
                request(
                        "PUT",
                        "/api/user/password",
                        Map.of(
                                "oldPassword",
                                "previous valid password",
                                "newPassword",
                                "brand new long password"),
                        access);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(
                result.headers().allValues("Set-Cookie").stream()
                        .anyMatch(v -> v.contains("Max-Age=0")));
        assertEquals(1, scalar("SELECT auth_version FROM sys_user WHERE id=?", admin));
        assertEquals(
                0,
                scalar(
                        "SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND revoked_time IS NULL",
                        admin));
        assertEquals(401, request("GET", "/api/user/info", null, access).statusCode());
        assertEquals(
                401, request("GET", "/api/user/info", null, second.accessToken()).statusCode());
        assertEquals(200, request("GET", "/api/user/info", null, otherToken).statusCode());
        assertTrue(
                passwords.matches(
                        "brand new long password",
                        jdbc.queryForObject(
                                "SELECT password FROM sys_user WHERE id=?", String.class, admin)));
        assertEquals(0, scalar("SELECT must_change_password FROM sys_user WHERE id=?", admin));
        String logs = jdbc.queryForList("SELECT * FROM sys_operation_log").toString();
        assertFalse(logs.contains("previous valid password"));
        assertFalse(logs.contains("brand new long password"));
    }

    @Test
    void badOldPasswordAndWeakNewPasswordDoNotChangeSessionsAndAreLimited() throws Exception {
        for (String next :
                List.of("previous valid password", "12345678", "short", "密".repeat(30))) {
            assertEquals(
                    400,
                    request(
                                    "PUT",
                                    "/api/user/password",
                                    Map.of(
                                            "oldPassword",
                                            "previous valid password",
                                            "newPassword",
                                            next),
                                    access)
                            .statusCode());
        }
        assertEquals(
                400,
                request(
                                "PUT",
                                "/api/user/password",
                                Map.of(
                                        "oldPassword",
                                        "incorrect",
                                        "newPassword",
                                        "another valid password"),
                                access)
                        .statusCode());
        // short 在 Bean Validation 阶段被拒绝，不消耗进入 Service 的限流次数。
        assertEquals(
                400,
                request(
                                "PUT",
                                "/api/user/password",
                                Map.of(
                                        "oldPassword",
                                        "incorrect",
                                        "newPassword",
                                        "another valid password"),
                                access)
                        .statusCode());
        assertEquals(
                429,
                request(
                                "PUT",
                                "/api/user/password",
                                Map.of(
                                        "oldPassword",
                                        "incorrect",
                                        "newPassword",
                                        "another valid password"),
                                access)
                        .statusCode());
        assertEquals(0, scalar("SELECT auth_version FROM sys_user WHERE id=?", admin));
        assertEquals(200, request("GET", "/api/user/info", null, access).statusCode());
    }

    @Test
    void validPngAndJpegAreReencodedAndBoundToCurrentUser() throws Exception {
        when(storage.upload(eq(admin), any()))
                .thenReturn(
                        "https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/"
                                + admin
                                + "/new.png");
        for (String format : List.of("png", "jpeg")) {
            var result = upload(image(format), access, "");
            assertEquals(200, result.statusCode(), result.body());
            String url = data(result).get("avatar").asString();
            assertEquals(
                    url,
                    jdbc.queryForObject(
                            "SELECT avatar_url FROM sys_user WHERE id=?", String.class, admin));
            assertEquals(
                    url,
                    data(request("GET", "/api/user/info", null, access)).get("avatar").asString());
        }
        assertEquals(0, scalar("SELECT auth_version FROM sys_user WHERE id=?", admin));
    }

    @Test
    void fakeEmptyOversizeAndExcessiveDimensionsAreRejectedBeforeStorage() throws Exception {
        for (byte[] bytes :
                List.of(
                        new byte[0],
                        "<svg>fake</svg>".getBytes(StandardCharsets.UTF_8),
                        new byte[] {(byte) 137, 80, 78, 71})) {
            assertEquals(400, upload(bytes, access, "").statusCode());
        }
        assertEquals(413, upload(new byte[2 * 1024 * 1024 + 1], access, "").statusCode());
        var big = new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(big, "png", out);
        assertEquals(400, upload(out.toByteArray(), access, "").statusCode());
        verify(storage, never()).upload(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void multipartCannotOverrideUserAndMissingFileIsInvalid() throws Exception {
        assertEquals(400, upload(image("png"), access, "userId").statusCode());
        var response =
                request(
                        "POST",
                        "/api/user/avatar",
                        Map.of("avatar", "https://evil.invalid/x.png"),
                        access);
        assertTrue(response.statusCode() == 400 || response.statusCode() == 415, response.body());
        verify(storage, never()).upload(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void failedDatabaseSaveCompensatesNewObjectAndKeepsOldAvatar() throws Exception {
        String previous =
                "https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/"
                        + admin
                        + "/previous.png";
        String next =
                "https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/"
                        + admin
                        + "/new.png";
        jdbc.update("UPDATE sys_user SET avatar_url=? WHERE id=?", previous, admin);
        when(storage.upload(eq(admin), any())).thenReturn(next);
        jdbc.execute(
                "CREATE TRIGGER t13_avatar_fail BEFORE UPDATE ON sys_user FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='avatar save unavailable'");
        try {
            assertEquals(500, upload(image("png"), access, "").statusCode());
            assertEquals(
                    previous,
                    jdbc.queryForObject(
                            "SELECT avatar_url FROM sys_user WHERE id=?", String.class, admin));
            verify(storage).deleteOwned(admin, next);
            verify(storage, never()).deleteOwned(admin, previous);
        } finally {
            jdbc.execute("DROP TRIGGER t13_avatar_fail");
        }
    }

    @Test
    void successfulAvatarReplacementDeletesOnlyAfterCommitAndStorageFailureDoesNotWrite()
            throws Exception {
        String previous =
                "https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/"
                        + admin
                        + "/previous.png";
        String next =
                "https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/"
                        + admin
                        + "/new.png";
        jdbc.update("UPDATE sys_user SET avatar_url=? WHERE id=?", previous, admin);
        when(storage.upload(eq(admin), any())).thenReturn(next);
        assertEquals(200, upload(image("png"), access, "").statusCode());
        verify(storage).deleteOwned(admin, previous);
        when(storage.upload(eq(admin), any()))
                .thenThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "存储暂不可用"));
        assertEquals(503, upload(image("png"), access, "").statusCode());
        assertEquals(
                next,
                jdbc.queryForObject(
                        "SELECT avatar_url FROM sys_user WHERE id=?", String.class, admin));
    }

    @Test
    void realWebpIsDecodedAndStoredAsPng() throws Exception {
        when(storage.upload(eq(admin), any()))
                .thenAnswer(
                        call -> {
                            byte[] bytes = call.getArgument(1);
                            assertEquals(137, Byte.toUnsignedInt(bytes[0]));
                            assertEquals(80, Byte.toUnsignedInt(bytes[1]));
                            assertTrue(
                                    ImageIO.read(new java.io.ByteArrayInputStream(bytes)) != null);
                            return "https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/"
                                    + admin
                                    + "/webp.png";
                        });
        byte[] bytes;
        try (var fixture = getClass().getResourceAsStream("/fixtures/1_webp_ll.webp")) {
            bytes = fixture.readAllBytes();
        }
        var result = upload(bytes, access, "");
        assertEquals(200, result.statusCode(), result.body());
    }

    @Test
    void authenticationChangeDuringUploadRejectsDatabaseWriteAndCleansNewObject() throws Exception {
        String next =
                "https://maple-hush.oss-cn-beijing.aliyuncs.com/vben-admin-img/"
                        + admin
                        + "/race.png";
        when(storage.upload(eq(admin), any()))
                .thenAnswer(
                        call -> {
                            jdbc.update(
                                    "UPDATE sys_user SET auth_version=auth_version+1 WHERE id=?",
                                    admin);
                            return next;
                        });
        assertEquals(401, upload(image("png"), access, "").statusCode());
        verify(storage).deleteOwned(admin, next);
    }

    @Test
    void concurrentPasswordChangesOnlyOneRequestCanUseOldCredentials() throws Exception {
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            var one =
                    pool.submit(
                            () -> {
                                gate.await();
                                return request(
                                                "PUT",
                                                "/api/user/password",
                                                Map.of(
                                                        "oldPassword",
                                                        "previous valid password",
                                                        "newPassword",
                                                        "parallel new password one"),
                                                access)
                                        .statusCode();
                            });
            var two =
                    pool.submit(
                            () -> {
                                gate.await();
                                return request(
                                                "PUT",
                                                "/api/user/password",
                                                Map.of(
                                                        "oldPassword",
                                                        "previous valid password",
                                                        "newPassword",
                                                        "parallel new password two"),
                                                access)
                                        .statusCode();
                            });
            gate.countDown();
            assertEquals(
                    List.of(200, 401),
                    java.util.stream.Stream.of(one.get(), two.get()).sorted().toList());
        }
    }

    private int scalar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private long ordinary() {
        String name = "profile_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO sys_user(username,real_name,password,status,must_change_password,remark) VALUES (?,'普通用户',?,1,0,'管理员备注')",
                name,
                passwords.encode("ordinary valid password"));
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
    }

    private byte[] image(String format) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private HttpResponse<String> upload(byte[] file, String token, String extra) throws Exception {
        String boundary = "maple-" + UUID.randomUUID();
        var out = new ByteArrayOutputStream();
        out.write(
                ("--"
                                + boundary
                                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"untrusted.exe\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        out.write(file);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if (!extra.isEmpty()) {
            out.write(
                    ("--"
                                    + boundary
                                    + "\r\nContent-Disposition: form-data; name=\""
                                    + extra
                                    + "\"\r\n\r\n999\r\n")
                            .getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/user/avatar"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(
                builder.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
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
