package com.maplehush.admin.system.log.service.impl;

import com.maplehush.admin.system.log.mapper.SysOperationLogMapper;
import com.maplehush.admin.system.log.service.ISysOperationLogService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 审计使用独立事务；连提交阶段的故障也不能改变业务结果
 */
@Service
public class SysOperationLogServiceImpl implements ISysOperationLogService {
    private static final Logger LOG = LoggerFactory.getLogger(SysOperationLogServiceImpl.class);
    private final SysOperationLogMapper mapper;
    private final TransactionTemplate transactions;
    private final AtomicLong failures = new AtomicLong();

    public SysOperationLogServiceImpl(
            SysOperationLogMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void record(Entry entry) {
        try {
            transactions.executeWithoutResult(
                    status -> {
                        if (mapper.save(entry.outcome(entry.success())) != 1) {
                            throw new IllegalStateException("Operation audit was not inserted");
                        }
                    });
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
            LOG.error(
                    "Operation audit write failed operation={} traceId={} failureCount={} errorType={}",
                    entry.operationCode(),
                    entry.traceId(),
                    failures.get(),
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * @return 进程内审计写入失败数，配合错误日志定位和监控故障
     */
    @Override
    public long failureCount() {
        return failures.get();
    }
}
