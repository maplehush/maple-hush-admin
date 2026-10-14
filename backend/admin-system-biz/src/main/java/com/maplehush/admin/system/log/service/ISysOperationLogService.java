package com.maplehush.admin.system.log.service;

/** 关键业务操作审计。 */
public interface ISysOperationLogService {
    /** 以独立事务记录审计，写入失败不改变业务结果。 */
    void record(Entry entry);

    /** 获取当前进程的操作审计写入失败次数。 */
    long failureCount();

    record Entry(Long actorId, String actorUsername, String operationCode, String targetType, String targetId,
                        boolean success, String summary, String requestMethod, String requestPath, String clientIp, String traceId) {
        public Entry outcome(boolean successful) {
            return new Entry(actorId, actorUsername, operationCode, targetType, targetId, successful,
                    successful ? "操作成功" : "操作失败", requestMethod, requestPath, clientIp, traceId);
        }
    }
}
