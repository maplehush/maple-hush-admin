package com.maplehush.admin.system.auth;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** 匿名验证码与登录、专用凭据改密接口。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthRedisService redis;
    private final AuthService auth;
    private final AuthCookies cookies;
    private final CookieCsrfTokenRepository csrfTokens;

    public AuthController(AuthRedisService redis, AuthService auth, AuthCookies cookies, CookieCsrfTokenRepository csrfTokens) {
        this.redis = redis;
        this.auth = auth;
        this.cookies = cookies;
        this.csrfTokens = csrfTokens;
    }

    @GetMapping("/captcha")
    public ResponseEntity<Result<AuthRedisService.Captcha>> captcha(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.ok(redis.issueCaptcha(request.getRemoteAddr())));
    }

    @PostMapping("/login")
    public ResponseEntity<Result<Map<String, Object>>> login(@Valid @RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse servletResponse) {
        AuthService.LoginResult result = auth.login(input.username(), input.password(), input.captchaId(),
                input.captchaCode(), request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        csrfTokens.saveToken(null, request, servletResponse);
        response.header(HttpHeaders.SET_COOKIE, servletResponse.getHeaders(HttpHeaders.SET_COOKIE).toArray(String[]::new));
        if (result.refreshToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookies.refresh(result.refreshToken(), result.expireTime()).toString());
        }
        Map<String, Object> data = result.passwordChangeRequired()
                ? Map.of("passwordChangeRequired", true, "passwordChangeToken", result.passwordChangeToken())
                : Map.of("passwordChangeRequired", false, "accessToken", result.accessToken());
        return response.body(Result.ok(data));
    }

    @PutMapping("/initial-password")
    public ResponseEntity<Result<Void>> initialPassword(@RequestHeader(value = HttpHeaders.AUTHORIZATION,
            required = false) String authorization, @Valid @RequestBody PasswordRequest input,
            HttpServletRequest request, HttpServletResponse servletResponse) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
        }
        auth.changeInitialPassword(authorization.substring(7), input.newPassword());
        csrfTokens.saveToken(null, request, servletResponse);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, servletResponse.getHeaders(HttpHeaders.SET_COOKIE).toArray(String[]::new))
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .body(Result.ok(null));
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 128) String password,
                               @NotBlank @Size(max = 64) String captchaId,
                               @NotBlank @Size(max = 16) String captchaCode) {}
    public record PasswordRequest(@NotBlank @Size(max = 128) String newPassword) {}
}
