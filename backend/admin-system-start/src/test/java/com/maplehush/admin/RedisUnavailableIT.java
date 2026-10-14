package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Redis 故障时验证码与登录都返回 503，不能绕过校验
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maple-hush.auth.bootstrap-enabled=true", "spring.data.redis.port=1"})
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T04_IT_ENABLED", matches = "true")
class RedisUnavailableIT {
    @LocalServerPort private int port;
    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private com.maplehush.admin.system.auth.security.token.AuthTokenService tokens;

    @Test
    void captchaAndLoginFailClosed() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> captcha =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:" + port + "/api/auth/captcha"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(503, captcha.statusCode());
        assertEquals(50300, json.readTree(captcha.body()).get("code").asInt());

        HttpRequest loginRequest =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Origin", "http://127.0.0.1:" + port)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"username\":\"admin\",\"password\":\"wrong\","
                                                + "\"captchaId\":\"abc\",\"captchaCode\":\"ABCD\"}"))
                        .build();
        HttpResponse<String> login =
                client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(503, login.statusCode());
        assertEquals(50300, json.readTree(login.body()).get("code").asInt());
    }

    @Test
    void failedSnapshotWriteRollsBackRotationButLogoutStillRevokes() throws Exception {
        String username = "t05_failure_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        long userId = 0;
        try {
            jdbc.update(
                    "INSERT INTO sys_user (username,real_name,password,status,must_change_password,auth_version) "
                            + "VALUES (?,?,?,1,0,0)",
                    username,
                    "Redis 故障测试",
                    passwords.encode("t05 test passphrase"));
            userId =
                    jdbc.queryForObject(
                            "SELECT id FROM sys_user WHERE username=?", Long.class, username);
            String raw =
                    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[48]);
            String hash =
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    raw.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
            String family = java.util.UUID.randomUUID().toString();
            jdbc.update(
                    "INSERT INTO auth_refresh_session (user_id,auth_version,family_id,token_hash,expire_time) VALUES (?,0,?,?,?)",
                    userId,
                    family,
                    hash,
                    java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1));
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + port;
            String access = tokens.issueAccess(userId, 0, family);
            var info =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/api/user/info"))
                                    .header("Authorization", "Bearer " + access)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, info.statusCode(), info.body());
            assertEquals(
                    username, json.readTree(info.body()).get("data").get("username").asString());
            var csrf =
                    client.send(
                            HttpRequest.newBuilder(URI.create(base + "/api/auth/csrf"))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            String token = json.readTree(csrf.body()).get("data").get("token").asString();
            String cookie =
                    csrf.headers().allValues("Set-Cookie").stream()
                            .filter(v -> v.startsWith("mh_csrf="))
                            .findFirst()
                            .orElseThrow()
                            .split(";", 2)[0];
            for (String path : new String[] {"refresh", "logout"}) {
                var response =
                        client.send(
                                HttpRequest.newBuilder(URI.create(base + "/api/auth/" + path))
                                        .header("Origin", base)
                                        .header("Cookie", "mh_refresh=" + raw + "; " + cookie)
                                        .header("X-XSRF-TOKEN", token)
                                        .POST(HttpRequest.BodyPublishers.noBody())
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                assertEquals(path.equals("refresh") ? 503 : 200, response.statusCode());
                if (path.equals("refresh")) {
                    assertEquals(
                            1,
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND used_time IS NULL AND revoked_time IS NULL",
                                    Integer.class,
                                    userId));
                }
            }
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? AND revoked_time IS NOT NULL",
                            Integer.class,
                            userId));
        } finally {
            jdbc.update("DELETE FROM auth_refresh_session WHERE user_id=?", userId);
            jdbc.update("DELETE FROM sys_login_log WHERE user_id=?", userId);
            jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
        }
    }
}
