package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.common.storage.service.IOssStorageService;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthSessionService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
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
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

/**
 * 真实 HTTP/MySQL/Redis 验证统一上传及用户头像绑定；OSS 使用替身
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T13_FILE_IT_ENABLED", matches = "true")
class FileUploadIT {
    private static final String SCHEMA =
            "maple_hush_files_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

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

    @MockitoBean private IOssStorageService storage;

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
    void uploadReturnsReceiptAndNeverChangesCurrentAvatar() throws Exception {
        var response = upload(image(), access, "");
        assertEquals(200, response.statusCode(), response.body());
        var file = data(response);
        assertTrue(file.get("uploadId").asString().matches("[a-f0-9]{32}"));
        assertEquals("image/png", file.get("contentType").asString());
        assertTrue(file.get("size").asInt() > 0);
        assertTrue(file.get("url").asString().contains("/vben-admin-img/files/" + admin + "/"));
        assertTrue(
                jdbc.queryForObject(
                        "SELECT avatar_url IS NULL FROM sys_user WHERE id=?",
                        Boolean.class,
                        admin));
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(any(), bytes.capture(), org.mockito.ArgumentMatchers.eq("image/png"));
        assertTrue(ImageIO.read(new java.io.ByteArrayInputStream(bytes.getValue())) != null);
        assertTrue(
                redis.getExpire(keys.key("file_upload", file.get("uploadId").asString())) > 7000);
    }

    @Test
    void createUserBindsUploadAndManagementReadsReturnAvatar() throws Exception {
        var file = uploaded(access);
        String id = create("avatar_created", file.get("uploadId").asString(), 200);
        String url = file.get("url").asString();
        assertEquals(
                url,
                jdbc.queryForObject(
                        "SELECT avatar_url FROM sys_user WHERE id=?", String.class, id));
        assertEquals(
                url,
                data(request("GET", "/api/system/user/" + id, null, access))
                        .get("avatar")
                        .asString());
        var list = data(request("GET", "/api/system/user/list?name=头像绑定", null, access));
        assertEquals(url, list.get("items").get(0).get("avatar").asString());
    }

    @Test
    void updateUserCanBindNewUploadAndOmittedFieldPreservesIt() throws Exception {
        String id = create("avatar_update", null, 200);
        var file = uploaded(access);
        var form = Map.of("name", "头像绑定更新", "avatarUploadId", file.get("uploadId").asString());
        assertEquals(200, request("PUT", "/api/system/user/" + id, form, access).statusCode());
        assertEquals(
                200,
                request("PUT", "/api/system/user/" + id, Map.of("name", "头像绑定保留"), access)
                        .statusCode());
        assertEquals(
                file.get("url").asString(),
                jdbc.queryForObject(
                        "SELECT avatar_url FROM sys_user WHERE id=?", String.class, id));
    }

    @Test
    void foreignUploadCannotBeBoundByAnotherOperator() throws Exception {
        long user = ordinary();
        String token = sessions.open(user, 0).accessToken();
        var file = uploaded(token);
        create("foreign_receipt", file.get("uploadId").asString(), 403);
        assertEquals(0, scalar("SELECT COUNT(*) FROM sys_user WHERE username='foreign_receipt'"));
    }

    @Test
    void expiredUnknownAndUrlInputsAreRejectedWithoutCreatingUser() throws Exception {
        var file = uploaded(access);
        String receipt = file.get("uploadId").asString();
        redis.delete(keys.key("file_upload", receipt));
        create("expired_receipt", receipt, 400);
        create("unknown_receipt", "a".repeat(32), 400);
        create("url_receipt", "https://evil.invalid/file.png", 400);
        assertEquals(
                400,
                request(
                                "POST",
                                "/api/system/user",
                                Map.of(
                                        "username",
                                        "arbitrary_url",
                                        "name",
                                        "拒绝",
                                        "avatar",
                                        file.get("url").asString()),
                                access)
                        .statusCode());
    }

    @Test
    void malformedFilesExtraFieldsAndAnonymousAreRejected() throws Exception {
        assertEquals(
                400,
                upload("not-image.pdf".getBytes(StandardCharsets.UTF_8), access, "").statusCode());
        assertEquals(400, upload(new byte[0], access, "").statusCode());
        assertEquals(400, upload(image(), access, "path").statusCode());
        assertEquals(413, upload(new byte[2 * 1024 * 1024 + 1], access, "").statusCode());
        assertEquals(401, upload(image(), null, "").statusCode());
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    void storageFailureReturnsDependencyErrorAndCreatesNoReceipt() throws Exception {
        doThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "存储暂不可用"))
                .when(storage)
                .put(any(), any(), any());
        var response = upload(image(), access, "");
        assertEquals(503, response.statusCode(), response.body());
    }

    private JsonNode uploaded(String token) throws Exception {
        var response = upload(image(), token, "");
        assertEquals(200, response.statusCode(), response.body());
        return data(response);
    }

    private int scalar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String create(String username, String receipt, int expected) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("username", username);
        body.put("name", "头像绑定");
        if (receipt != null) {
            body.put("avatarUploadId", receipt);
        }
        var response = request("POST", "/api/system/user", body, access);
        assertEquals(expected, response.statusCode(), response.body());
        return expected == 200 ? data(response).get("id").asString() : null;
    }

    private long ordinary() {
        String name = "file_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO sys_user(username,real_name,password,status,must_change_password) VALUES (?,'普通用户',?,1,0)",
                name,
                passwords.encode("ordinary file password"));
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name);
    }

    private byte[] image() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return out.toByteArray();
    }

    private HttpResponse<String> upload(byte[] file, String token, String extra) throws Exception {
        String boundary = "maple-" + UUID.randomUUID();
        var out = new ByteArrayOutputStream();
        out.write(
                ("--"
                                + boundary
                                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../photo.jpg\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        out.write(file);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if (!extra.isEmpty()) {
            out.write(
                    ("--"
                                    + boundary
                                    + "\r\nContent-Disposition: form-data; name=\""
                                    + extra
                                    + "\"\r\n\r\nunsafe\r\n")
                            .getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/files/upload"))
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
