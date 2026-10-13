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
import tools.jackson.databind.json.JsonMapper;

/** T04 仅开放匿名认证入口；Cookie 写接口的 CSRF 防护随 T05 落地。 */
@Configuration
public class AuthSecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    UserDetailsService userDetailsService() {
        return username -> { throw new UsernameNotFoundException("用户身份由 JWT 验证"); };
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenFilter accessTokenFilter,
            JsonMapper json) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/captcha", "/api/auth/login", "/api/auth/initial-password",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(json.writeValueAsString(Result.fail(ErrorCode.UNAUTHENTICATED.code(),
                            ErrorCode.UNAUTHENTICATED.value(), "请重新登录")));
                }));
        http.addFilterBefore(accessTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
