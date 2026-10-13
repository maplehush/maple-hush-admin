package com.maplehush.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;

/** Redis 故障时验证码与登录都返回 503，不能绕过校验。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maple-hush.auth.bootstrap-enabled=true", "spring.data.redis.port=1"})
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "T04_IT_ENABLED", matches = "true")
class RedisUnavailableIT {
    @LocalServerPort private int port;
    @Autowired private JsonMapper json;

    @Test
    void captchaAndLoginFailClosed() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> captcha = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/auth/captcha")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, captcha.statusCode());
        assertEquals(50300, json.readTree(captcha.body()).get("code").asInt());

        HttpRequest loginRequest = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"wrong\","
                        + "\"captchaId\":\"abc\",\"captchaCode\":\"ABCD\"}"))
                .build();
        HttpResponse<String> login = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(503, login.statusCode());
        assertEquals(50300, json.readTree(login.body()).get("code").asInt());
    }
}
