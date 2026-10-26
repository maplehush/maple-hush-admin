package com.maplehush.admin.system.auth.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.security.AuthCookies;
import com.maplehush.admin.system.auth.service.IAuthSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

import java.util.Map;

/**
 * 认证会话
 *
 * Cookie 会话接口由 Security 的来源与 CSRF 校验保护，不依赖 access
 */
@SecurityRequirements
@Tag(name = "认证会话")
@RestController
@RequestMapping("/api/auth")
public class AuthSessionController {
    private final IAuthSessionService sessions;
    private final AuthCookies cookies;
    private final CookieCsrfTokenRepository csrfTokens;
    private final JsonMapper json;

    public AuthSessionController(
            IAuthSessionService sessions,
            AuthCookies cookies,
            CookieCsrfTokenRepository csrfTokens,
            JsonMapper json) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
        this.json = json;
    }

    @Operation(summary = "获取 CSRF 请求令牌")
    @GetMapping("/csrf")
    public ResponseEntity<Result<Map<String, String>>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(Map.of("token", token.getToken())));
    }

    @Operation(summary = "刷新访问令牌", description = "请求来源必须通过 Origin 或 Referer 校验；请从同源文档页面调试。 浏览器自动携带 mh_refresh Cookie；先调用 GET /api/auth/csrf，再将 data.token 填入 X-XSRF-TOKEN 请求头。")
    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refresh,
            HttpServletRequest request,
            HttpServletResponse response) {
        IAuthSessionService.SessionResult session;
        try {
            session =
                    sessions.refresh(
                            refresh, request.getRemoteAddr(), request.getHeader("User-Agent"));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.UNAUTHENTICATED) {
                response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString());
                csrfTokens.saveToken(null, request, response);
            }
            throw exception;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.refresh(session.refreshToken(), session.expireTime()).toString())
                .body(json.writeValueAsString(session.accessToken()));
    }

    @Operation(summary = "退出当前会话", description = "请求来源必须通过 Origin 或 Referer 校验；请从同源文档页面调试。 浏览器自动携带 mh_refresh Cookie；先调用 GET /api/auth/csrf，再将 data.token 填入 X-XSRF-TOKEN 请求头。")
    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refresh,
            HttpServletRequest request,
            HttpServletResponse response) {
        sessions.logout(refresh, request.getRemoteAddr(), request.getHeader("User-Agent"));
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        response.getHeaders(HttpHeaders.SET_COOKIE).toArray(String[]::new))
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .body(Result.ok(null));
    }
}
