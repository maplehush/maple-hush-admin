package com.maplehush.admin.system.auth.service;

import java.time.LocalDateTime;

/**
 * 登录会话签发、刷新和撤销
 */
public interface IAuthSessionService {
    /**
     * 为有效用户签发会话
     *
     * @param userId 目标用户 ID
     * @param expectedVersion 登录校验时读取的用户授权版本
     * @return 本次 access/refresh 凭据与会话族到期时间
     */
    SessionResult open(long userId, long expectedVersion);

    /**
     * 轮换 refresh 凭据，重放时撤销会话族
     *
     * @param raw refresh Cookie 中的原始凭据，不得记录日志
     * @param ip 请求来源地址，用于限流或审计
     * @param agent 客户端 User-Agent，审计写入时按长度上限截断
     * @return 轮换后的 access/refresh 凭据和不延长的绝对到期时间
     */
    SessionResult refresh(String raw, String ip, String agent);

    /**
     * 撤销 refresh 凭据对应的会话族
     *
     * @param raw refresh Cookie 中的原始凭据，不得记录日志
     * @param ip 请求来源地址，用于限流或审计
     * @param agent 客户端 User-Agent，审计写入时按长度上限截断
     */
    void logout(String raw, String ip, String agent);

    record SessionResult(String accessToken, String refreshToken, LocalDateTime expireTime) {}
}
