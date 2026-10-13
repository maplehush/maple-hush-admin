package com.maplehush.admin.system.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 登录审计只保存账号、结果和原因码，不保存凭据或验证码。 */
@Service
public class LoginAudit {
    private static final Logger LOG = LoggerFactory.getLogger(LoginAudit.class);
    private final JdbcTemplate jdbc;

    public LoginAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long userId, String username, boolean success, String reason, String ip, String userAgent) {
        try {
            jdbc.update("INSERT INTO sys_login_log "
                    + "(user_id,username,event_type,success,reason_code,client_ip,user_agent,trace_id) "
                    + "VALUES (?,?, 'LOGIN', ?,?,?,?,?)", userId, limited(username, 64), success ? 1 : 0,
                    limited(reason, 64), limited(ip, 45), limited(userAgent, 512), MDC.get("traceId"));
        } catch (RuntimeException exception) {
            LOG.error("Login audit write failed", exception);
        }
    }

    private static String limited(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
