package com.maplehush.admin.system.auth.service;

import java.time.LocalDateTime;

/** 登录和首次改密业务。 */
public interface IAuthService {
    /** 校验验证码和凭据，返回登录或待改密结果。 */
    LoginResult login(String username, String password, String captchaId, String captchaCode,
            String ip, String userAgent);

    /** 校验专用凭据并修改密码，撤销既有会话。 */
    void changeInitialPassword(String token, String newPassword);

    record LoginResult(boolean passwordChangeRequired, String accessToken,
                              String passwordChangeToken, String refreshToken, LocalDateTime expireTime) {}
}
