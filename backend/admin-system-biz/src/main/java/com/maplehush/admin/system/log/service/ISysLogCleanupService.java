package com.maplehush.admin.system.log.service;

/**
 * 日志留存维护，无对外删除接口
 */
public interface ISysLogCleanupService {
    /**
     * 分别按两张表的留存期执行有界删除；可通过配置关闭
     * @return 本轮合计删除的记录数
     */
    int cleanupExpiredLogs();

    /**
     * 获取进程内清理失败批次计数
     * @return 累计失败批次数
     */
    long failureCount();
}
