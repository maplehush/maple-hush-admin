package com.maplehush.admin.system.auth.service;

/**
 * 验证码、登录限流与最小用户快照
 */
public interface IAuthRedisService {
    /**
     * 生成并暂存一次性验证码
     *
     * @param ip 请求来源地址，用于限流或审计
     * @return 验证码标识和 Base64 图片，不包含验证码答案
     */
    Captcha issueCaptcha(String ip);

    /**
     * 检查 IP 与账号的登录频率限制
     *
     * @param ip 请求来源地址，用于限流或审计
     * @param username 登录账号
     */
    void checkLoginLimit(String ip, String username);

    /**
     * 累计账号凭据失败次数
     *
     * @param username 登录账号
     */
    void recordCredentialFailure(String username);

    /**
     * 清除账号凭据失败计数
     *
     * @param username 登录账号
     */
    void clearCredentialFailures(String username);

    /**
     * 原子消费并校验验证码
     *
     * @param id 验证码标识
     * @param answer 用户输入的验证码答案
     */
    void consumeCaptcha(String id, String answer);

    /**
     * 读取用户缓存快照
     *
     * @param userId 目标用户 ID
     * @return 缓存快照 JSON；缓存未命中时为空
     */
    String readSnapshot(long userId);

    /**
     * 保存带过期时间的用户缓存快照
     *
     * @param userId 目标用户 ID
     * @param json 最小用户信息快照 JSON，不含密码和令牌
     */
    void writeSnapshot(long userId, String json);

    /**
     * 清除用户缓存快照
     *
     * @param userId 目标用户 ID
     */
    void clearSnapshot(long userId);

    record Captcha(String captchaId, String imageBase64) {}
}
