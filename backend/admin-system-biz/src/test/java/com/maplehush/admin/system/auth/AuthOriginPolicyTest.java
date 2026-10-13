package com.maplehush.admin.system.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthOriginPolicyTest {
    @Test
    void productionUsesConfiguredOriginAndRejectsForgedHost() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AuthOriginPolicy policy = new AuthOriginPolicy("https://admin.example.com", environment);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("evil.example");
        request.addHeader("Origin", "https://evil.example");
        assertFalse(policy.isAllowed(request));
        request.removeHeader("Origin");
        request.addHeader("Origin", "https://admin.example.com.evil.example");
        assertFalse(policy.isAllowed(request));
        request.removeHeader("Origin");
        request.addHeader("Origin", "null");
        assertFalse(policy.isAllowed(request));
        request.removeHeader("Origin");
        request.addHeader("Referer", "https://admin.example.com/login");
        assertTrue(policy.isAllowed(request));
        request.addHeader("Origin", "https://evil.example");
        assertFalse(policy.isAllowed(request), "Origin 优先，不能用 Referer 覆盖恶意 Origin");
    }

    @Test
    void secureCookiesAreHostOnlyAndBoundToAbsoluteExpiry() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AuthCookies cookies = new AuthCookies(environment);
        String value = cookies.refresh("token", java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).plusSeconds(10)).toString();
        assertTrue(value.contains("Secure"));
        assertTrue(value.contains("HttpOnly"));
        assertTrue(value.contains("Path=/api/auth"));
        assertTrue(value.contains("SameSite=Lax"));
        assertFalse(value.contains("Domain="));
    }
}
