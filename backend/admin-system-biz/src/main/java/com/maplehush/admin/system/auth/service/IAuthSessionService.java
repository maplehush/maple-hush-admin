package com.maplehush.admin.system.auth.service;

import java.time.LocalDateTime;

/** 登录会话签发、刷新和撤销。 */
public interface IAuthSessionService {
    /** 为有效用户签发会话。 */
    SessionResult open(long userId, long expectedVersion);

    /** 轮换 refresh 凭据，重放时撤销会话族。 */
    SessionResult refresh(String raw, String ip, String agent);

    /** 撤销 refresh 凭据对应的会话族。 */
    void logout(String raw, String ip, String agent);

    record SessionResult(String accessToken, String refreshToken, LocalDateTime expireTime) {}
}
