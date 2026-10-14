package com.maplehush.admin.system.auth.security;

import com.maplehush.admin.system.auth.security.token.AuthTokenService;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.user.entity.SysUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 普通 access 仅接受 access 用途，并从 MySQL 核对账号版本与有效令牌族
 */
@Component
public class AccessTokenFilter extends OncePerRequestFilter {
    private final AuthTokenService tokens;
    private final JdbcTemplate jdbc;
    private final PermissionMapper users;
    private final IPermissionService permissions;

    public AccessTokenFilter(
            AuthTokenService tokens,
            JdbcTemplate jdbc,
            PermissionMapper users,
            IPermissionService permissions) {
        this.tokens = tokens;
        this.jdbc = jdbc;
        this.users = users;
        this.permissions = permissions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return java.util.Set.of("/api/auth/refresh", "/api/auth/logout", "/api/auth/csrf")
                .contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthTokenService.Claims claims = tokens.verifyAccess(header.substring(7));
                SysUser user = users.getUser(claims.userId());
                Integer sessions =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? "
                                        + "AND family_id=? AND auth_version=? AND used_time IS NULL AND revoked_time IS NULL AND expire_time>?",
                                Integer.class,
                                claims.userId(),
                                claims.familyId(),
                                claims.authVersion(),
                                LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
                if (user != null
                        && Integer.valueOf(1).equals(user.getStatus())
                        && !Boolean.TRUE.equals(user.getMustChangePassword())
                        && user.getAuthVersion() == claims.authVersion()
                        && sessions != null
                        && sessions > 0) {
                    IPermissionService.Context context = permissions.load(user);
                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    claims.userId(),
                                    null,
                                    context.permissions().codes().stream()
                                            .sorted()
                                            .map(SimpleGrantedAuthority::new)
                                            .toList());
                    authentication.setDetails(context);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
