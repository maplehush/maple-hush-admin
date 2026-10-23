package com.maplehush.admin.system.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.system.log.aspect.OperationLogAspect;
import com.maplehush.admin.system.log.service.ISysOperationLogService;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

class OperationLogAspectTest {
    @Test
    void capturesActualRequestPathWithoutQueryString() throws Throwable {
        var audit = mock(ISysOperationLogService.class);
        var call = mock(ProceedingJoinPoint.class);
        var operation = mock(OperationLog.class);
        var request = mock(HttpServletRequest.class);
        when(call.getArgs()).thenReturn(new Object[0]);
        when(call.proceed()).thenReturn("saved");
        when(operation.value()).thenReturn("system:dict:update");
        when(operation.targetType()).thenReturn("dict_item");
        when(operation.targetIdArgument()).thenReturn(-1);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/api/system/dict/item/2");
        when(request.getQueryString()).thenReturn("token=must-not-be-recorded");
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn("/api/system/dict/item/{id}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertEquals("saved", new OperationLogAspect(audit).record(call, operation));
            var captured = ArgumentCaptor.forClass(ISysOperationLogService.Entry.class);
            verify(audit).record(captured.capture());
            assertEquals("/api/system/dict/item/2", captured.getValue().requestPath());
            assertEquals("PUT", captured.getValue().requestMethod());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
