package com.maplehush.admin.system.auth;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Cookie 会话接口由 Security 的来源与 CSRF 校验保护，不依赖 access。 */
@RestController
@RequestMapping("/api/auth")
public class AuthSessionController {
    private final AuthSessionService sessions;
    private final AuthCookies cookies;
    private final CookieCsrfTokenRepository csrfTokens;
    private final JsonMapper json;

    public AuthSessionController(AuthSessionService sessions, AuthCookies cookies,
            CookieCsrfTokenRepository csrfTokens, JsonMapper json) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
        this.json = json;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Result<Map<String, String>>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(Map.of("token", token.getToken())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String refresh,
            HttpServletRequest request, HttpServletResponse response) {
        AuthSessionService.SessionResult session;
        try {
            session = sessions.refresh(refresh, request.getRemoteAddr(), request.getHeader("User-Agent"));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.UNAUTHENTICATED) {
                response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString());
                csrfTokens.saveToken(null, request, response);
            }
            throw exception;
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(session.refreshToken(), session.expireTime()).toString())
                .body(json.writeValueAsString(session.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refresh,
            HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(refresh, request.getRemoteAddr(), request.getHeader("User-Agent"));
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, response.getHeaders(HttpHeaders.SET_COOKIE).toArray(String[]::new))
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString()).body(Result.ok(null));
    }
}
