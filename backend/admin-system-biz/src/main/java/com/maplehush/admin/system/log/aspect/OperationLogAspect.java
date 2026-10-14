package com.maplehush.admin.system.log.aspect;

import com.maplehush.admin.common.annotation.OperationLog;
import com.maplehush.admin.system.log.service.ISysOperationLogService.Entry;
import com.maplehush.admin.system.log.service.ISysOperationLogService;
import com.maplehush.admin.system.permission.service.IPermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/** 切面位于事务拦截器外层，成功只在真实提交后记录；不采集参数或异常内容。 */
@Aspect
@Component
@Order(-100)
public class OperationLogAspect {
    private final ISysOperationLogService audit;

    public OperationLogAspect(ISysOperationLogService audit) { this.audit = audit; }

    @Around("@annotation(operation)")
    public Object record(ProceedingJoinPoint call, OperationLog operation) throws Throwable {
        Entry entry = capture(call, operation);
        Object result;
        try { result = call.proceed(); }
        catch (Throwable exception) { audit.record(entry.outcome(false)); throw exception; }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            // 已加入调用方事务时，方法返回不等于业务提交。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    audit.record(entry.outcome(status == STATUS_COMMITTED));
                }
            });
        } else {
            audit.record(entry.outcome(true));
        }
        return result;
    }

    private Entry capture(ProceedingJoinPoint call, OperationLog operation) {
        Long actor = null; String username = null;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof IPermissionService.Context context) {
            actor = context.user().getId(); username = context.user().getUsername();
        }
        String method = null; String path = null; String ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            method = request.getMethod(); ip = request.getRemoteAddr();
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            // 只记录服务端路由模板，避免路径参数和查询参数泄露个人信息。
            path = pattern == null ? null : pattern.toString();
        }
        String target = null;
        int index = operation.targetIdArgument();
        if (index >= 0 && index < call.getArgs().length && call.getArgs()[index] instanceof Long id && id > 0) target = id.toString();
        return new Entry(actor, limited(username, 64), limited(operation.value(), 120), limited(operation.targetType(), 64),
                target, true, null, limited(method, 10), limited(path, 500), limited(ip, 45), MDC.get("traceId"));
    }

    private String limited(String value, int length) {
        return value == null ? null : value.substring(0, Math.min(value.length(), length));
    }
}
