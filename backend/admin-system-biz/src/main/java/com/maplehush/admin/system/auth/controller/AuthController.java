package com.maplehush.admin.system.auth.controller;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.dto.InitialPasswordDTO;
import com.maplehush.admin.system.auth.dto.LoginDTO;
import com.maplehush.admin.system.auth.security.AuthCookies;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.service.IAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证登录
 *
 * 匿名验证码与登录、专用凭据改密接口
 */
@Tag(name = "认证登录")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IAuthRedisService redis;
    private final IAuthService auth;
    private final AuthCookies cookies;
    private final CookieCsrfTokenRepository csrfTokens;

    public AuthController(
            IAuthRedisService redis,
            IAuthService auth,
            AuthCookies cookies,
            CookieCsrfTokenRepository csrfTokens) {
        this.redis = redis;
        this.auth = auth;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
    }

    @SecurityRequirements
    @Operation(summary = "获取图片验证码")
    @GetMapping("/captcha")
    public ResponseEntity<Result<IAuthRedisService.Captcha>> captcha(HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(redis.issueCaptcha(request.getRemoteAddr())));
    }

    @SecurityRequirements
    @Operation(summary = "登录并签发会话", description = "请求来源必须通过 Origin 或 Referer 校验；请从同源文档页面调试。")
    @PostMapping("/login")
    public ResponseEntity<Result<Map<String, Object>>> login(
            @Valid @RequestBody LoginDTO input,
            HttpServletRequest request,
            HttpServletResponse servletResponse) {
        IAuthService.LoginResult result =
                auth.login(
                        input.username(),
                        input.password(),
                        input.captchaId(),
                        input.captchaCode(),
                        request.getRemoteAddr(),
                        request.getHeader(HttpHeaders.USER_AGENT));
        ResponseEntity.BodyBuilder response =
                ResponseEntity.ok().cacheControl(CacheControl.noStore());
        csrfTokens.saveToken(null, request, servletResponse);
        response.header(
                HttpHeaders.SET_COOKIE,
                servletResponse.getHeaders(HttpHeaders.SET_COOKIE).toArray(String[]::new));
        if (result.refreshToken() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookies.refresh(result.refreshToken(), result.expireTime()).toString());
        }
        Map<String, Object> data =
                result.passwordChangeRequired()
                        ? Map.of(
                                "passwordChangeRequired",
                                true,
                                "passwordChangeToken",
                                result.passwordChangeToken())
                        : Map.of(
                                "passwordChangeRequired",
                                false,
                                "accessToken",
                                result.accessToken());
        return response.body(Result.ok(data));
    }

    @Operation(summary = "首次登录修改密码", description = "Authorization 使用登录返回的 passwordChangeToken，不接受普通 accessToken。成功后重新登录。")
    @PutMapping("/initial-password")
    public ResponseEntity<Result<Void>> initialPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                    String authorization,
            @Valid @RequestBody InitialPasswordDTO input,
            HttpServletRequest request,
            HttpServletResponse servletResponse) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
        }
        auth.changeInitialPassword(authorization.substring(7), input.newPassword());
        csrfTokens.saveToken(null, request, servletResponse);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.SET_COOKIE,
                        servletResponse.getHeaders(HttpHeaders.SET_COOKIE).toArray(String[]::new))
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .body(Result.ok(null));
    }
}
