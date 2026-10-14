package com.maplehush.admin.system.log.service;

import com.maplehush.admin.system.log.mapper.SysOperationLogMapper;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 审计使用独立事务；连提交阶段的故障也不能改变业务结果。 */
@Service
public class SysOperationLogService {
    private static final Logger LOG = LoggerFactory.getLogger(SysOperationLogService.class);
    private final SysOperationLogMapper mapper;
    private final TransactionTemplate transactions;
    private final AtomicLong failures = new AtomicLong();

    public SysOperationLogService(SysOperationLogMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Entry entry) {
        try {
            transactions.executeWithoutResult(status -> {
                if (mapper.save(entry) != 1) throw new IllegalStateException("Operation audit was not inserted");
            });
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            LOG.error("Operation audit write failed operation={} traceId={}", entry.operationCode(), entry.traceId(), exception);
        }
    }

    /** @return 进程内审计写入失败数，配合错误日志定位和监控故障 */
    public long failureCount() { return failures.get(); }

    public record Entry(Long actorId, String actorUsername, String operationCode, String targetType, String targetId,
                        boolean success, String summary, String requestMethod, String requestPath, String clientIp, String traceId) {
        public Entry outcome(boolean successful) {
            return new Entry(actorId, actorUsername, operationCode, targetType, targetId, successful,
                    successful ? "操作成功" : "操作失败", requestMethod, requestPath, clientIp, traceId);
        }
    }
}
