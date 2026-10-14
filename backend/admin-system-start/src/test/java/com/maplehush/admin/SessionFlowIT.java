package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.schedule.AuthSessionCleanup;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
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
import tools.jackson.databind.json.JsonMapper;

/** 以真实 HTTP、MySQL 和 Redis 验证会话轮换；测试普通用户在结束后清理。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maple-hush.auth.bootstrap-enabled=true",
                "maple-hush.auth.jwt-secret=t05-test-secret-0123456789abcdef0123456789"})
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T05_IT_ENABLED", matches = "true")
class SessionFlowIT {
    private static final String REDIS_PREFIX = "maple_hush_t05_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SCHEMA = "maple_hush_t05_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

    @DynamicPropertySource
    static void isolatedInfrastructure(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "123456");
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/" + SCHEMA
                + "?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true");
        properties.add("maple-hush.redis.key-prefix", () -> REDIS_PREFIX);
    }

    @AfterAll
    static void removeSchema() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/", "root", "123456");
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AuthSessionCleanup cleanup;
    @Autowired private JsonMapper json;
    @Autowired private RedisKeyBuilder keys;
    @Autowired @Qualifier("stringValueRedisTemplate") private RedisTemplate<String, String> redis;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String username;
    private long userId;

    @BeforeEach
    void createUser() {
        username = "t05_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_user (username,real_name,password,status,must_change_password,auth_version) "
                + "VALUES (?,?,?,1,0,0)", username, "T05 测试用户", passwords.encode("t05 strong passphrase"));
        userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
    }

    @AfterEach
    void removeUser() {
        jdbc.update("DELETE FROM auth_refresh_session WHERE user_id=?", userId);
        jdbc.update("DELETE FROM sys_login_log WHERE user_id=? OR username=?", userId, username);
        jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        redis.delete(keys.key("user", Long.toString(userId)));
    }

    @Test
    void rotationKeepsFamilyExpiryAndLogoutInvalidatesAccess() throws Exception {
        Login login = login();
        Csrf csrf = csrf();
        LocalDateTime expiry = jdbc.queryForObject("SELECT expire_time FROM auth_refresh_session WHERE user_id=?",
                LocalDateTime.class, userId);
        redis.delete(keys.key("user", Long.toString(userId)));
        HttpResponse<String> rotated = send("POST", "/api/auth/refresh", null,
                login.cookie() + "; " + csrf.cookie(), csrf.token(), origin(), null);
        assertEquals(200, rotated.statusCode(), rotated.body());
        assertTrue(json.readTree(rotated.body()).isString(), "refresh 正文必须为原始 JSON 字符串");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=?", Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND used_time IS NOT NULL",
                Integer.class, userId));
        assertEquals(expiry, jdbc.queryForObject("SELECT expire_time FROM auth_refresh_session WHERE user_id=? AND used_time IS NULL",
                LocalDateTime.class, userId));
        assertEquals(404, protectedRequest(login.access()).statusCode());
        assertNotNull(redis.opsForValue().get(keys.key("user", Long.toString(userId))));
        String newCookie = cookie(rotated, "mh_refresh");
        assertNotEquals(login.cookie(), newCookie);
        String expiredAccess = expiredAccess();
        assertEquals(401, protectedRequest(expiredAccess).statusCode());
        HttpResponse<String> logout = send("POST", "/api/auth/logout", null,
                newCookie + "; " + csrf.cookie(), csrf.token(), origin(), expiredAccess);
        assertEquals(200, logout.statusCode(), logout.body());
        assertTrue(logout.headers().allValues("Set-Cookie").stream().anyMatch(v -> v.startsWith("mh_refresh=") && v.contains("Max-Age=0")));
        assertTrue(expiredCookie(logout, "mh_csrf"));
        assertNull(redis.opsForValue().get(keys.key("user", Long.toString(userId))));
        assertEquals(401, protectedRequest(login.access()).statusCode());
        assertEquals(401, protectedRequest(json.readTree(rotated.body()).asString()).statusCode());
        Csrf afterLogout = csrf();
        assertNotEquals(csrf.cookie(), afterLogout.cookie());
        assertEquals(200, send("POST", "/api/auth/logout", null, afterLogout.cookie(),
                afterLogout.token(), origin(), null).statusCode());
    }

    @Test
    void concurrentReuseProducesOneSuccessAndRevokesFamily() throws Exception {
        Login login = login();
        Csrf csrf = csrf();
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<HttpResponse<String>> attempt = () -> {
                go.await();
                return send("POST", "/api/auth/refresh", null, login.cookie() + "; " + csrf.cookie(),
                        csrf.token(), origin(), null);
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            go.countDown();
            int a = first.get().statusCode();
            int b = second.get().statusCode();
            assertTrue((a == 200 && b == 401) || (a == 401 && b == 200));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND revoked_time IS NULL",
                Integer.class, userId));
        assertEquals(401, protectedRequest(login.access()).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE user_id=? AND reason_code='REFRESH_REPLAY'",
                Integer.class, userId));
    }

    @Test
    void csrfAndOriginFailuresDoNotRotateSession() throws Exception {
        Login login = login();
        Csrf csrf = csrf();
        String cookies = login.cookie() + "; " + csrf.cookie();
        assertEquals(40301, code(send("POST", "/api/auth/refresh", null, cookies, null, origin(), null)));
        assertEquals(40301, code(send("POST", "/api/auth/refresh", null, cookies, "wrong", origin(), null)));
        assertEquals(40301, code(send("POST", "/api/auth/refresh", null, cookies, csrf.token(), "https://evil.example", null)));
        assertEquals(40301, code(send("POST", "/api/auth/refresh", null, cookies, csrf.token(), null, null)));
        assertEquals(40301, code(send("POST", "/api/auth/refresh?_csrf="
                + java.net.URLEncoder.encode(csrf.token(), StandardCharsets.UTF_8), null, cookies, null, origin(), null)));
        assertEquals(40301, code(send("POST", "/api/auth/login", Map.of(), null, null, "https://evil.example", null)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND used_time IS NULL",
                Integer.class, userId));
        assertEquals(200, send("POST", "/api/auth/refresh", null, cookies, csrf.token(), null, null,
                origin() + "/login").statusCode());
    }

    @Test
    void authorizationVersionChangeRejectsRefreshAndClearsSnapshot() throws Exception {
        Login login = login();
        Csrf csrf = csrf();
        jdbc.update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=?", userId);
        assertEquals(401, protectedRequest(login.access()).statusCode());
        assertEquals(401, send("POST", "/api/auth/refresh", null,
                login.cookie() + "; " + csrf.cookie(), csrf.token(), origin(), null).statusCode());
        assertNull(redis.opsForValue().get(keys.key("user", Long.toString(userId))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND revoked_time IS NULL",
                Integer.class, userId));
    }

    @Test
    void familyDeadlineDoesNotExtendAndExpiredEvidenceIsRetained() throws Exception {
        Login login = login();
        Csrf csrf = csrf();
        LocalDateTime deadline = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusSeconds(30).withNano(0);
        jdbc.update("UPDATE auth_refresh_session SET expire_time=? WHERE user_id=?", deadline, userId);
        HttpResponse<String> response = send("POST", "/api/auth/refresh", null,
                login.cookie() + "; " + csrf.cookie(), csrf.token(), origin(), null);
        assertEquals(200, response.statusCode(), response.body());
        String header = response.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith("mh_refresh=")).findFirst().orElseThrow();
        assertTrue(header.matches(".*Max-Age=([0-9]|[12][0-9]|30);.*"));
        assertEquals(deadline, jdbc.queryForObject("SELECT expire_time FROM auth_refresh_session WHERE user_id=? AND used_time IS NULL",
                LocalDateTime.class, userId));
        jdbc.update("UPDATE auth_refresh_session SET expire_time=? WHERE user_id=?", deadline.minusMinutes(2), userId);
        assertEquals(401, send("POST", "/api/auth/refresh", null,
                cookie(response, "mh_refresh") + "; " + csrf.cookie(), csrf.token(), origin(), null).statusCode());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=?", Integer.class, userId));
    }

    @Test
    void cleanupKeepsRecentlyExpiredAndFutureRevokedEvidence() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        for (int days : new int[]{-8, -1, 1}) {
            jdbc.update("INSERT INTO auth_refresh_session (user_id,auth_version,family_id,token_hash,expire_time,used_time,revoked_time) "
                    + "VALUES (?,0,?,?,?, ?,?)", userId, UUID.randomUUID().toString(),
                    java.util.HexFormat.of().formatHex(new byte[32]).substring(0, 62) + (days == -8 ? "aa" : days == -1 ? "bb" : "cc"),
                    now.plusDays(days), now.minusDays(9), now.minusDays(9));
        }
        assertEquals(1, cleanup.cleanupExpiredSessions());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=?", Integer.class, userId));
    }

    private Login login() throws Exception {
        var captcha = json.readTree(send("GET", "/api/auth/captcha", null, null, null, null, null).body()).get("data");
        String id = captcha.get("captchaId").asString();
        String answer = redis.opsForValue().get(keys.key("captcha", id));
        HttpResponse<String> response = send("POST", "/api/auth/login",
                Map.of("username", username, "password", "t05 strong passphrase", "captchaId", id, "captchaCode", answer),
                null, null, origin(), null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(expiredCookie(response, "mh_csrf"));
        return new Login(json.readTree(response.body()).get("data").get("accessToken").asString(), cookie(response, "mh_refresh"));
    }

    private Csrf csrf() throws Exception {
        HttpResponse<String> response = send("GET", "/api/auth/csrf", null, null, null, null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertTrue(response.headers().allValues("Set-Cookie").stream().anyMatch(v -> v.startsWith("mh_csrf=") && v.contains("HttpOnly")));
        return new Csrf(json.readTree(response.body()).get("data").get("token").asString(), cookie(response, "mh_csrf"));
    }

    private int code(HttpResponse<String> response) { return json.readTree(response.body()).get("code").asInt(); }
    private String origin() { return "http://127.0.0.1:" + port; }
    private HttpResponse<String> protectedRequest(String token) throws Exception {
        return send("GET", "/api/not-yet-implemented", null, null, null, null, token);
    }
    private String cookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith(name + "="))
                .findFirst().orElseThrow().split(";", 2)[0];
    }

    private boolean expiredCookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith(name + "="))
                .map(v -> java.net.HttpCookie.parse(v).getFirst())
                .anyMatch(value -> value.getValue().isEmpty() && value.getMaxAge() == 0);
    }
    private HttpResponse<String> send(String method, String path, Object body, String cookies,
            String csrf, String origin, String access) throws Exception {
        return send(method, path, body, cookies, csrf, origin, access, null);
    }

    private HttpResponse<String> send(String method, String path, Object body, String cookies,
            String csrf, String origin, String access, String referer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin() + path)).timeout(Duration.ofSeconds(10));
        if (cookies != null) request.header("Cookie", cookies);
        if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
        if (origin != null) request.header("Origin", origin);
        if (referer != null) request.header("Referer", referer);
        if (access != null) request.header("Authorization", "Bearer " + access);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String expiredAccess() throws Exception {
        String family = jdbc.queryForObject("SELECT family_id FROM auth_refresh_session WHERE user_id=? LIMIT 1", String.class, userId);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer("maple-hush-admin").subject(Long.toString(userId)).claim("typ", "access")
                .claim("ver", 0L).claim("fid", family).expirationTime(Date.from(Instant.now().minusSeconds(60))).build());
        jwt.sign(new MACSigner("t05-test-secret-0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
    private record Login(String access, String cookie) {}
    private record Csrf(String token, String cookie) {}
}
