package com.maplehush.admin.system.log.service.impl;

import com.maplehush.admin.system.log.mapper.SysLoginLogMapper;
import com.maplehush.admin.system.log.mapper.SysOperationLogMapper;
import com.maplehush.admin.system.log.service.ISysLogCleanupService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * 两张日志表独立分批清理，每批独立提交，失败时继续处理另一张表
 */
@Service
public class SysLogCleanupServiceImpl implements ISysLogCleanupService {
    private static final Logger LOG = LoggerFactory.getLogger(SysLogCleanupServiceImpl.class);

    /**
     * 与 Mapper SQL 一致的每批上限
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * 每表每次最多十批，限制数据库占用
     */
    private static final int MAX_BATCHES = 10;

    private final SysLoginLogMapper login;
    private final SysOperationLogMapper operation;
    private final boolean enabled;
    private final int loginDays;
    private final int operationDays;
    private final AtomicLong failures = new AtomicLong();

    public SysLogCleanupServiceImpl(
            SysLoginLogMapper login,
            SysOperationLogMapper operation,
            @Value("${maple-hush.log.cleanup-enabled:true}") boolean enabled,
            @Value("${maple-hush.log.login-retention-days:90}") int loginDays,
            @Value("${maple-hush.log.operation-retention-days:180}") int operationDays) {
        if (loginDays < 1 || loginDays > 3650 || operationDays < 1 || operationDays > 3650) {
            throw new IllegalArgumentException("日志保留天数必须为 1～3650");
        }
        this.login = login;
        this.operation = operation;
        this.enabled = enabled;
        this.loginDays = loginDays;
        this.operationDays = operationDays;
    }

    @Override
    @Scheduled(
            fixedDelayString = "${maple-hush.log.cleanup-delay-ms:3600000}",
            initialDelayString = "${maple-hush.log.cleanup-delay-ms:3600000}")
    public int cleanupExpiredLogs() {
        if (!enabled) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
        int total = clean("login", () -> login.deleteExpiredBefore(now.minusDays(loginDays)));
        total +=
                clean(
                        "operation",
                        () -> operation.deleteExpiredBefore(now.minusDays(operationDays)));
        if (total > 0) {
            LOG.info("Expired audit logs removed count={}", total);
        }
        return total;
    }

    @Override
    public long failureCount() {
        return failures.get();
    }

    private int clean(String kind, IntSupplier delete) {
        int total = 0;
        try {
            for (int batch = 0; batch < MAX_BATCHES; batch++) {
                int removed = delete.getAsInt();
                total += removed;
                if (removed < BATCH_SIZE) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            // 数据库异常消息可能包含 SQL 或字段值，仅记录类别并用计数监控。
            LOG.error(
                    "Audit cleanup failed kind={} failureCount={} errorType={}",
                    kind,
                    failures.get(),
                    exception.getClass().getSimpleName());
        }
        return total;
    }
}
