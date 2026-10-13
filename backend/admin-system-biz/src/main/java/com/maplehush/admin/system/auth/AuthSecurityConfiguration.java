package com.maplehush.admin.system.auth;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import tools.jackson.databind.json.JsonMapper;

/** Cookie 会话写接口启用 Spring Security CSRF，Bearer 接口单独验证身份。 */
@Configuration
public class AuthSecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(AuthCookies cookies) {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieName(AuthCookies.CSRF);
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/api/auth");
        repository.setCookieCustomizer(builder -> builder.httpOnly(true).secure(cookies.secure()).sameSite("Lax"));
        return repository;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    UserDetailsService userDetailsService() {
        return username -> { throw new UsernameNotFoundException("用户身份由 JWT 验证"); };
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenFilter accessTokenFilter,
            JsonMapper json, CookieCsrfTokenRepository csrfTokens, AuthOriginPolicy origins) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(new HeaderOnlyCsrfHandler())
                        .requireCsrfProtectionMatcher(request -> "POST".equals(request.getMethod())
                                && ("/api/auth/refresh".equals(request.getServletPath())
                                || "/api/auth/logout".equals(request.getServletPath()))))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/captcha", "/api/auth/login", "/api/auth/initial-password",
                                "/api/auth/csrf", "/api/auth/refresh", "/api/auth/logout",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                    ErrorCode error = exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN;
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(json.writeValueAsString(Result.fail(error.code(), error.value(),
                            error == ErrorCode.CSRF_INVALID ? "请求校验失败，请重试" : "没有操作权限")));
                }).authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(json.writeValueAsString(Result.fail(ErrorCode.UNAUTHENTICATED.code(),
                            ErrorCode.UNAUTHENTICATED.value(), "请重新登录")));
                }));
        http.addFilterBefore(accessTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(new AuthOriginFilter(origins, json), CsrfFilter.class);
        return http.build();
    }

    private static final class HeaderOnlyCsrfHandler implements CsrfTokenRequestHandler {
        private final XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> deferred) {
            delegate.handle(request, response, deferred);
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
            if (request.getHeader("X-XSRF-TOKEN") == null) return null;
            return delegate.resolveCsrfTokenValue(request, token);
        }
    }
}
