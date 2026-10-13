package com.maplehush.admin.system.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LoginAuditTest {
    @Test
    void auditCommitFailureDoesNotFailCompletedLogout() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doThrow(new TransactionSystemException("test commit failure")).when(manager).commit(any());
        LoginAudit audit = new LoginAudit(mock(JdbcTemplate.class), manager);
        assertDoesNotThrow(() -> audit.recordEvent(1L, "test-user", "LOGOUT", true, "OK", "127.0.0.1", "test"));
    }
}
