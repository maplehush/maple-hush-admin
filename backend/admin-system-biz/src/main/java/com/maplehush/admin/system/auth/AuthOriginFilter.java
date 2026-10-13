package com.maplehush.admin.system.auth;

import com.maplehush.admin.common.api.Result;
import com.maplehush.admin.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** 对会设置或消费登录 Cookie 的写接口检查来源。 */
final class AuthOriginFilter extends OncePerRequestFilter {
    private final AuthOriginPolicy origins;
    private final JsonMapper json;
    private static final Set<String> PATHS = Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    AuthOriginFilter(AuthOriginPolicy origins, JsonMapper json) {
        this.origins = origins;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && PATHS.contains(request.getServletPath()) && !origins.isAllowed(request)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(json.writeValueAsString(Result.fail(ErrorCode.CSRF_INVALID.code(),
                    ErrorCode.CSRF_INVALID.value(), "请求校验失败，请重试")));
            return;
        }
        chain.doFilter(request, response);
    }
}
