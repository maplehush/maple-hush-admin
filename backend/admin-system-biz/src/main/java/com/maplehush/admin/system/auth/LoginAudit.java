package com.maplehush.admin.system.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 登录审计只保存账号、结果和原因码，不保存凭据或验证码。 */
@Service
public class LoginAudit {
    private static final Logger LOG = LoggerFactory.getLogger(LoginAudit.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public LoginAudit(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Long userId, String username, boolean success, String reason, String ip, String userAgent) {
        recordEvent(userId, username, "LOGIN", success, reason, ip, userAgent);
    }

    public void recordEvent(Long userId, String username, String event, boolean success, String reason,
            String ip, String userAgent) {
        try {
            transactions.executeWithoutResult(status -> jdbc.update("INSERT INTO sys_login_log "
                    + "(user_id,username,event_type,success,reason_code,client_ip,user_agent,trace_id) "
                    + "VALUES (?,?,?,?,?,?,?,?)", userId, limited(username, 64), event, success ? 1 : 0,
                    limited(reason, 64), limited(ip, 45), limited(userAgent, 512), MDC.get("traceId")));
        } catch (RuntimeException exception) {
            LOG.error("Login audit write failed", exception);
        }
    }

    private static String limited(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(value.length(), max));
    }
}
