package com.maplehush.admin.system.auth.service;

/** 验证码、登录限流与最小用户快照。 */
public interface IAuthRedisService {
    /** 生成并暂存一次性验证码。 */
    Captcha issueCaptcha(String ip);

    /** 检查 IP 与账号的登录频率限制。 */
    void checkLoginLimit(String ip, String username);

    /** 累计账号凭据失败次数。 */
    void recordCredentialFailure(String username);

    /** 清除账号凭据失败计数。 */
    void clearCredentialFailures(String username);

    /** 原子消费并校验验证码。 */
    void consumeCaptcha(String id, String answer);

    /** 读取用户缓存快照。 */
    String readSnapshot(long userId);

    /** 保存带过期时间的用户缓存快照。 */
    void writeSnapshot(long userId, String json);

    /** 清除用户缓存快照。 */
    void clearSnapshot(long userId);

    record Captcha(String captchaId, String imageBase64) {}
}
