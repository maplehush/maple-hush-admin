package com.maplehush.admin.system.log.service;

/**
 * SysLoginLogService 业务接口
 */
public interface ISysLoginLogService {
    /**
     * 以独立事务记录审计，写入失败不改变业务结果
     *
     * @param userId 审计用户 ID；登录失败且无法识别用户时可为空
     * @param username 审计账号名称；无法识别账号时可为空
     * @param success 操作是否成功
     * @param reason 可安全记录的稳定原因码，不传入原始异常或凭据
     * @param ip 请求来源地址，用于限流或审计
     * @param userAgent 客户端 User-Agent，审计写入时按长度上限截断
     */
    void record(
            Long userId,
            String username,
            boolean success,
            String reason,
            String ip,
            String userAgent);

    /**
     * 以独立事务记录登录、刷新或退出事件
     *
     * @param userId 审计用户 ID；登录失败且无法识别用户时可为空
     * @param username 审计账号名称；无法识别账号时可为空
     * @param event 登录事件类型，例如 LOGIN、REFRESH 或 LOGOUT
     * @param success 操作是否成功
     * @param reason 可安全记录的稳定原因码，不传入原始异常或凭据
     * @param ip 请求来源地址，用于限流或审计
     * @param userAgent 客户端 User-Agent，审计写入时按长度上限截断
     */
    void recordEvent(
            Long userId,
            String username,
            String event,
            boolean success,
            String reason,
            String ip,
            String userAgent);
}
