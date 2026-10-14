package com.maplehush.admin.system.log.service;

/** SysLoginLogService 业务接口。 */
public interface ISysLoginLogService {
    /** 以独立事务记录审计，写入失败不改变业务结果。 */
    void record(Long userId, String username, boolean success, String reason, String ip, String userAgent);

    /** 以独立事务记录登录、刷新或退出事件。 */
    void recordEvent(Long userId, String username, String event, boolean success, String reason,
            String ip, String userAgent);
}
