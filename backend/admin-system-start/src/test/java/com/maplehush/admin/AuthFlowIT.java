package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.AuthRedisService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实 MySQL、Redis 和 HTTP；在隔离开发库中以 T04_IT_ENABLED=true 显式启用。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "maple-hush.auth.bootstrap-enabled=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T04_IT_ENABLED", matches = "true")
class AuthFlowIT {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JsonMapper json;
    @Autowired private AuthRedisService authRedis;
    @Autowired private RedisKeyBuilder keys;
    @Autowired @Qualifier("stringValueRedisTemplate") private RedisTemplate<String, String> redis;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String username;
    private long userId;

    @BeforeEach
    void createUser() {
        username = "t04_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbc.update("INSERT INTO sys_user (username,real_name,password,status,must_change_password,auth_version) "
                + "VALUES (?,?,?,1,1,0)", username, "T04 测试用户", encoder.encode("12345678"));
        userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
    }

    @AfterEach
    void removeUser() {
        if (username != null) {
            jdbc.update("DELETE FROM auth_refresh_session WHERE user_id=?", userId);
            jdbc.update("DELETE FROM sys_login_log WHERE username=?", username);
            jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
            redis.delete(keys.key("user", Long.toString(userId)));
        }
    }

    @Test
    void forcedChangeThenNormalLoginHasExclusiveCredentialsAndSnapshot() throws Exception {
        Captcha first = captcha();
        Map<String, String> firstBody = loginBody(first, "12345678");
        HttpResponse<String> changeLogin = send("POST", "/api/auth/login", firstBody, null);
        JsonNode firstData = data(changeLogin);
        assertEquals(200, changeLogin.statusCode());
        assertTrue(firstData.get("passwordChangeRequired").asBoolean());
        assertNotNull(firstData.get("passwordChangeToken"));
        assertFalse(firstData.has("accessToken"));
        assertFalse(changeLogin.headers().allValues("Set-Cookie").stream().anyMatch(v -> v.startsWith("mh_refresh=")));
        assertNull(redis.opsForValue().get(keys.key("user", Long.toString(userId))));
        String changeToken = firstData.get("passwordChangeToken").asText();

        assertEquals(40011, json.readTree(send("POST", "/api/auth/login", firstBody, null).body())
                .get("code").asInt());
        assertEquals(401, send("GET", "/api/user/info", null, changeToken).statusCode());
        assertEquals(40010, json.readTree(send("PUT", "/api/auth/initial-password",
                Map.of("newPassword", "12345678"), changeToken).body()).get("code").asInt());

        String newPassword = "t04 passphrase " + UUID.randomUUID();
        assertEquals(200, send("PUT", "/api/auth/initial-password",
                Map.of("newPassword", newPassword), changeToken).statusCode());
        assertEquals(401, send("PUT", "/api/auth/initial-password",
                Map.of("newPassword", newPassword + " again"), changeToken).statusCode());
        String hash = jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?", String.class, userId);
        assertFalse(hash.equals(newPassword));
        assertTrue(encoder.matches(newPassword, hash));

        HttpResponse<String> normal = send("POST", "/api/auth/login", loginBody(captcha(), newPassword), null);
        JsonNode normalData = data(normal);
        assertEquals(200, normal.statusCode());
        assertFalse(normalData.get("passwordChangeRequired").asBoolean());
        assertTrue(normalData.has("accessToken"));
        assertFalse(normalData.has("passwordChangeToken"));
        assertEquals(404, send("GET", "/api/not-yet-implemented", null,
                normalData.get("accessToken").asText()).statusCode());
        String cookie = normal.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("mh_refresh=")).findFirst().orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        String refresh = cookie.split(";", 2)[0].substring("mh_refresh=".length());
        String stored = jdbc.queryForObject("SELECT token_hash FROM auth_refresh_session WHERE user_id=?",
                String.class, userId);
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(refresh.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, stored);
        String snapshot = redis.opsForValue().get(keys.key("user", Long.toString(userId)));
        assertNotNull(snapshot);
        assertFalse(snapshot.contains("password"));
        assertTrue(redis.getExpire(keys.key("user", Long.toString(userId))) > 0);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE username=? AND success=1",
                Integer.class, username));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE username=? "
                + "AND reason_code='CAPTCHA_INVALID'", Integer.class, username));
    }

    @Test
    void captchaIsConsumedOnceAndRateLimitIsAtomic() {
        String ip = "test-" + UUID.randomUUID();
        AuthRedisService.Captcha captcha = authRedis.issueCaptcha(ip);
        String answer = redis.opsForValue().get(keys.key("captcha", captcha.captchaId()));
        authRedis.consumeCaptcha(captcha.captchaId(), answer);
        BusinessException reused = assertThrows(BusinessException.class,
                () -> authRedis.consumeCaptcha(captcha.captchaId(), answer));
        assertEquals(ErrorCode.CAPTCHA_INVALID, reused.getErrorCode());
        AuthRedisService.Captcha wrong = authRedis.issueCaptcha("wrong-" + UUID.randomUUID());
        String correctAnswer = redis.opsForValue().get(keys.key("captcha", wrong.captchaId()));
        assertEquals(ErrorCode.CAPTCHA_INVALID, assertThrows(BusinessException.class,
                () -> authRedis.consumeCaptcha(wrong.captchaId(), "incorrect")).getErrorCode());
        assertEquals(ErrorCode.CAPTCHA_INVALID, assertThrows(BusinessException.class,
                () -> authRedis.consumeCaptcha(wrong.captchaId(), correctAnswer)).getErrorCode());
        AuthRedisService.Captcha expiring = authRedis.issueCaptcha("expire-" + UUID.randomUUID());
        String expiringAnswer = redis.opsForValue().get(keys.key("captcha", expiring.captchaId()));
        redis.expire(keys.key("captcha", expiring.captchaId()), Duration.ofMillis(100));
        try { Thread.sleep(250); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        assertEquals(ErrorCode.CAPTCHA_INVALID, assertThrows(BusinessException.class,
                () -> authRedis.consumeCaptcha(expiring.captchaId(), expiringAnswer)).getErrorCode());
        for (int i = 0; i < 9; i++) {
            authRedis.issueCaptcha(ip);
        }
        BusinessException limited = assertThrows(BusinessException.class, () -> authRedis.issueCaptcha(ip));
        assertEquals(ErrorCode.RATE_LIMITED, limited.getErrorCode());
        String account = "account-" + UUID.randomUUID();
        for (int i = 0; i < 8; i++) authRedis.recordCredentialFailure(account);
        assertEquals(ErrorCode.RATE_LIMITED, assertThrows(BusinessException.class,
                () -> authRedis.checkLoginLimit(ip, account)).getErrorCode());
    }

    @Test
    void concurrentCaptchaConsumptionAllowsOneRequest() throws Exception {
        AuthRedisService.Captcha captcha = authRedis.issueCaptcha("parallel-" + UUID.randomUUID());
        String answer = redis.opsForValue().get(keys.key("captcha", captcha.captchaId()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                ready.countDown();
                go.await();
                try {
                    authRedis.consumeCaptcha(captcha.captchaId(), answer);
                    return true;
                } catch (BusinessException exception) {
                    assertEquals(ErrorCode.CAPTCHA_INVALID, exception.getErrorCode());
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            ready.await();
            go.countDown();
            assertTrue(first.get() != second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void wrongPasswordAndUnknownAccountHaveSamePublicError() throws Exception {
        HttpResponse<String> wrong = send("POST", "/api/auth/login", loginBody(captcha(), "wrong-password"), null);
        Captcha other = captcha();
        String missingUsername = "missing_" + UUID.randomUUID().toString().substring(0, 8);
        try {
            HttpResponse<String> unknown = send("POST", "/api/auth/login",
                    Map.of("username", missingUsername,
                            "password", "wrong-password", "captchaId", other.id(), "captchaCode", other.answer()), null);
            assertEquals(401, wrong.statusCode());
            assertEquals(401, unknown.statusCode());
            assertEquals(json.readTree(wrong.body()).get("message").asText(),
                    json.readTree(unknown.body()).get("message").asText());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE username=? AND success=0",
                    Integer.class, username));
        } finally {
            jdbc.update("DELETE FROM sys_login_log WHERE username=?", missingUsername);
        }
    }

    private Captcha captcha() throws Exception {
        JsonNode value = data(send("GET", "/api/auth/captcha", null, null));
        String id = value.get("captchaId").asText();
        return new Captcha(id, redis.opsForValue().get(keys.key("captcha", id)));
    }

    private Map<String, String> loginBody(Captcha captcha, String password) {
        return Map.of("username", username, "password", password,
                "captchaId", captcha.id(), "captchaCode", captcha.answer());
    }

    private JsonNode data(HttpResponse<String> response) {
        return json.readTree(response.body()).get("data");
    }

    private HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if ("POST".equals(method)) request.header("Origin", "http://127.0.0.1:" + port);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Captcha(String id, String answer) {}
}
