package com.maplehush.admin.system.auth.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 初始签发与轮换共用 Cookie 属性，Max-Age 不超过族内绝对到期时间
 */
@Component
public class AuthCookies {
    public static final String REFRESH = "mh_refresh";
    public static final String CSRF = "mh_csrf";
    private final boolean secure;

    public AuthCookies(Environment environment) {
        secure = !environment.acceptsProfiles(Profiles.of("dev"));
    }

    public boolean secure() {
        return secure;
    }

    public ResponseCookie refresh(String value, LocalDateTime expiry) {
        long remaining =
                Math.max(
                        0,
                        Duration.between(LocalDateTime.now(ZoneId.of("Asia/Shanghai")), expiry)
                                .getSeconds());
        return build(value, Duration.ofSeconds(remaining));
    }

    public ResponseCookie clearRefresh() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(REFRESH, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(age)
                .build();
    }
}
