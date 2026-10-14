package com.maplehush.admin.system.auth.service;

import java.time.LocalDateTime;

/**
 * 登录和首次改密业务
 */
public interface IAuthService {
    /**
     * 校验验证码和凭据，返回登录或待改密结果
     *
     * @param username 登录账号
     * @param password 用户输入的密码明文，仅用于本次校验，不得记录日志
     * @param captchaId 后端签发的一次性验证码标识
     * @param captchaCode 用户输入的验证码
     * @param ip 请求来源地址，用于限流或审计
     * @param userAgent 客户端 User-Agent，审计写入时按长度上限截断
     * @return 普通登录结果或专用待改密凭据；两类凭据不会同时签发
     */
    LoginResult login(
            String username,
            String password,
            String captchaId,
            String captchaCode,
            String ip,
            String userAgent);

    /**
     * 校验专用凭据并修改密码，撤销既有会话
     *
     * @param token 仅用于首次或重置后改密的短期凭据
     * @param newPassword 用户输入的新密码，须通过后端密码规则
     */
    void changeInitialPassword(String token, String newPassword);

    record LoginResult(
            boolean passwordChangeRequired,
            String accessToken,
            String passwordChangeToken,
            String refreshToken,
            LocalDateTime expireTime) {}
}
