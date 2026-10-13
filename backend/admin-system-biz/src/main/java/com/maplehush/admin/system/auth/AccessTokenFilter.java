package com.maplehush.admin.system.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 普通 access 仅接受 access 用途，并从 MySQL 核对账号版本与有效令牌族。 */
@Component
public class AccessTokenFilter extends OncePerRequestFilter {
    private final AuthTokenService tokens;
    private final JdbcTemplate jdbc;

    public AccessTokenFilter(AuthTokenService tokens, JdbcTemplate jdbc) {
        this.tokens = tokens;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthTokenService.Claims claims = tokens.verifyAccess(header.substring(7));
                List<Long> versions = jdbc.queryForList("SELECT auth_version FROM sys_user WHERE id=? AND status=1 "
                        + "AND must_change_password=0", Long.class, claims.userId());
                Integer sessions = jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_session WHERE user_id=? "
                        + "AND family_id=? AND used_time IS NULL AND revoked_time IS NULL AND expire_time>?", Integer.class,
                        claims.userId(), claims.familyId(), LocalDateTime.now());
                if (versions.size() == 1 && versions.getFirst() == claims.authVersion() && sessions != null
                        && sessions > 0) {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            claims.userId(), null, List.of()));
                }
            } catch (IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
