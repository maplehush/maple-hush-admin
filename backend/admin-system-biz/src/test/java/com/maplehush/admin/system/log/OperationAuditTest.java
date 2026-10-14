package com.maplehush.admin.system.log;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.maplehush.admin.system.log.mapper.SysOperationLogMapper;
import com.maplehush.admin.system.log.service.ISysOperationLogService;
import com.maplehush.admin.system.log.service.impl.SysOperationLogServiceImpl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class OperationAuditTest {
    @Test
    void commitFailureIsObservedButDoesNotReplaceBusinessResult() {
        var manager = mock(PlatformTransactionManager.class);
        var mapper = mock(SysOperationLogMapper.class);
        when(manager.getTransaction(any()))
                .thenAnswer(
                        call -> {
                            TransactionDefinition definition = call.getArgument(0);
                            assertEquals(
                                    TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                                    definition.getPropagationBehavior());
                            return new SimpleTransactionStatus();
                        });
        when(mapper.save(any())).thenReturn(1);
        doThrow(new TransactionSystemException("commit failure")).when(manager).commit(any());
        var service = new SysOperationLogServiceImpl(mapper, manager);
        var entry =
                new ISysOperationLogService.Entry(
                        1L,
                        "operator",
                        "system:user:update",
                        "user",
                        "2",
                        true,
                        "操作成功",
                        "PUT",
                        "/api/system/user/{id}",
                        "127.0.0.1",
                        "trace");
        assertDoesNotThrow(() -> service.record(entry));
        assertEquals(1, service.failureCount());
    }
}
