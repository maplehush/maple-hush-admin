package com.maplehush.admin.system.auth.service.impl;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.security.token.AuthTokenService;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.service.IAuthService;
import com.maplehush.admin.system.auth.service.IAuthSessionService;
import com.maplehush.admin.system.auth.support.PasswordPolicy;
import com.maplehush.admin.system.log.service.ISysLoginLogService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 登录和首次改密；普通会话的签发、轮换与撤销由 IAuthSessionService 管理
 */
@Service
public class AuthServiceImpl implements IAuthService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuthTokenService tokens;
    private final IAuthRedisService redis;
    private final PasswordPolicy passwords;
    private final ISysLoginLogService audit;
    private final IAuthSessionService sessions;
    private final String dummyPasswordHash;

    public AuthServiceImpl(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            AuthTokenService tokens,
            IAuthRedisService redis,
            PasswordPolicy passwords,
            ISysLoginLogService audit,
            IAuthSessionService sessions) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.tokens = tokens;
        this.redis = redis;
        this.passwords = passwords;
        this.audit = audit;
        this.sessions = sessions;
        this.dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public LoginResult login(
            String username,
            String password,
            String captchaId,
            String captchaCode,
            String ip,
            String userAgent) {
        Long userId = null;
        try {
            redis.checkLoginLimit(ip, username);
            redis.consumeCaptcha(captchaId, captchaCode);
            List<User> users =
                    jdbc.query(
                            "SELECT id,username,real_name,password,avatar_url,introduction,status,"
                                    + "must_change_password,auth_version FROM sys_user WHERE username=?",
                            (rs, row) ->
                                    new User(
                                            rs.getLong("id"),
                                            rs.getString("username"),
                                            rs.getString("real_name"),
                                            rs.getString("password"),
                                            rs.getString("avatar_url"),
                                            rs.getString("introduction"),
                                            rs.getInt("status"),
                                            rs.getBoolean("must_change_password"),
                                            rs.getLong("auth_version")),
                            username);
            boolean passwordMatches =
                    encoder.matches(
                            password,
                            users.size() == 1
                                    ? users.getFirst().passwordHash()
                                    : dummyPasswordHash);
            if (users.size() != 1 || users.getFirst().status() != 1 || !passwordMatches) {
                redis.recordCredentialFailure(username);
                throw new BusinessException(ErrorCode.UNAUTHENTICATED, "账号或密码错误");
            }
            User user = users.getFirst();
            userId = user.id();
            redis.clearCredentialFailures(username);
            if (user.mustChangePassword()) {
                audit.record(userId, username, true, "PASSWORD_CHANGE_REQUIRED", ip, userAgent);
                return new LoginResult(
                        true,
                        null,
                        tokens.issuePasswordChange(user.id(), user.authVersion()),
                        null,
                        null);
            }
            IAuthSessionService.SessionResult session =
                    sessions.open(user.id(), user.authVersion());
            audit.record(userId, username, true, "OK", ip, userAgent);
            return new LoginResult(
                    false,
                    session.accessToken(),
                    null,
                    session.refreshToken(),
                    session.expireTime());
        } catch (BusinessException exception) {
            audit.record(userId, username, false, exception.getErrorCode().value(), ip, userAgent);
            throw exception;
        }
    }

    @Transactional
    @Override
    public void changeInitialPassword(String token, String newPassword) {
        AuthTokenService.Claims claims;
        try {
            claims = tokens.verifyPasswordChange(token);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
        }
        List<String> hashes =
                jdbc.queryForList(
                        "SELECT password FROM sys_user WHERE id=? AND status=1 "
                                + "AND must_change_password=1 AND auth_version=? FOR UPDATE",
                        String.class,
                        claims.userId(),
                        claims.authVersion());
        if (hashes.size() != 1) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
        }
        try {
            passwords.validateNew(newPassword, PasswordPolicy.TEMPORARY_PASSWORD);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, exception.getMessage());
        }
        if (encoder.matches(newPassword, hashes.getFirst())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "新密码不能与原密码相同");
        }
        int changed =
                jdbc.update(
                        "UPDATE sys_user SET password=?,must_change_password=0,auth_version=auth_version+1 "
                                + "WHERE id=? AND auth_version=? AND must_change_password=1",
                        encoder.encode(newPassword),
                        claims.userId(),
                        claims.authVersion());
        if (changed != 1) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
        }
        jdbc.update(
                "UPDATE auth_refresh_session SET revoked_time=? WHERE user_id=? AND revoked_time IS NULL",
                LocalDateTime.now(),
                claims.userId());
        try {
            redis.clearSnapshot(claims.userId());
        } catch (RuntimeException exception) {
            LOG.warn("User snapshot invalidation failed for userId={}", claims.userId());
        }
    }

    private record User(
            long id,
            String username,
            String realName,
            String passwordHash,
            String avatar,
            String introduction,
            int status,
            boolean mustChangePassword,
            long authVersion) {}
}
