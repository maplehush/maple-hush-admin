package com.maplehush.admin.system.auth.service.impl;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.system.auth.entity.AuthRefreshSession;
import com.maplehush.admin.system.auth.mapper.AuthSessionMapper;
import com.maplehush.admin.system.auth.security.token.AuthTokenService;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.service.IAuthSessionService;
import com.maplehush.admin.system.auth.service.IUserSnapshotService;
import com.maplehush.admin.system.auth.support.AuthDigests;
import com.maplehush.admin.system.log.service.ISysLoginLogService;
import com.maplehush.admin.system.user.entity.SysUser;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 初始会话和轮换同用 MySQL 事务；拒绝重放时先提交整族撤销，再返回 401。 */
@Service
public class AuthSessionServiceImpl implements IAuthSessionService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthSessionServiceImpl.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final AuthSessionMapper mapper;
    private final TransactionTemplate transactions;
    private final AuthTokenService tokens;
    private final IUserSnapshotService snapshots;
    private final IAuthRedisService redis;
    private final ISysLoginLogService audit;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionServiceImpl(AuthSessionMapper mapper, TransactionTemplate transactions, AuthTokenService tokens,
            IUserSnapshotService snapshots, IAuthRedisService redis, ISysLoginLogService audit) {
        this.mapper = mapper;
        this.transactions = transactions;
        this.tokens = tokens;
        this.snapshots = snapshots;
        this.redis = redis;
        this.audit = audit;
    }

    @Override
    public SessionResult open(long userId, long expectedVersion) {
        return transactions.execute(status -> {
            SysUser user = mapper.lockUser(userId);
            if (!validUser(user) || user.getAuthVersion() != expectedVersion) throw unauthenticated();
            String family = UUID.randomUUID().toString();
            LocalDateTime expiry = now().plusDays(7);
            snapshots.write(user);
            return insertSuccessor(user, family, expiry);
        });
    }

    @Override
    public SessionResult refresh(String raw, String ip, String agent) {
        AuthRefreshSession preliminary = lookup(raw);
        if (preliminary == null) throw unauthenticated();
        Outcome outcome = transactions.execute(status -> {
            SysUser user = mapper.lockUser(preliminary.getUserId());
            AuthRefreshSession old = mapper.lockSession(preliminary.getId());
            if (old == null) return new Outcome(null, user, "SESSION_INVALID");
            LocalDateTime now = now();
            if (old.getUsedTime() != null && old.getExpireTime().isAfter(now)) {
                mapper.revokeFamily(old.getFamilyId(), now);
                return new Outcome(null, user, "REFRESH_REPLAY");
            }
            if (old.getRevokedTime() != null || !old.getExpireTime().isAfter(now)
                    || !validUser(user) || !old.getAuthVersion().equals(user.getAuthVersion())) {
                mapper.revokeFamily(old.getFamilyId(), now);
                return new Outcome(null, user, "SESSION_INVALID");
            }
            snapshots.write(user);
            if (mapper.markUsed(old.getId(), now) != 1) throw new IllegalStateException("会话轮换状态异常");
            return new Outcome(insertSuccessor(user, old.getFamilyId(), old.getExpireTime()), user, null);
        });
        if (outcome.result() == null) {
            if (outcome.user() != null) {
                invalidateSnapshot(outcome.user().getId());
                audit.recordEvent(outcome.user().getId(), outcome.user().getUsername(), "REFRESH", false,
                        outcome.reason(), ip, agent);
            }
            throw unauthenticated();
        }
        return outcome.result();
    }

    @Override
    public void logout(String raw, String ip, String agent) {
        AuthRefreshSession preliminary = lookup(raw);
        if (preliminary == null) return;
        SysUser user = transactions.execute(status -> {
            SysUser locked = mapper.lockUser(preliminary.getUserId());
            AuthRefreshSession session = mapper.lockSession(preliminary.getId());
            if (session != null) mapper.revokeFamily(session.getFamilyId(), now());
            return locked;
        });
        if (user != null) {
            invalidateSnapshot(user.getId());
            audit.recordEvent(user.getId(), user.getUsername(), "LOGOUT", true, "OK", ip, agent);
        }
    }

    private SessionResult insertSuccessor(SysUser user, String family, LocalDateTime expiry) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthRefreshSession session = new AuthRefreshSession();
        session.setUserId(user.getId());
        session.setAuthVersion(user.getAuthVersion());
        session.setFamilyId(family);
        session.setTokenHash(AuthDigests.digest(raw));
        session.setExpireTime(expiry);
        mapper.insert(session);
        return new SessionResult(tokens.issueAccess(user.getId(), user.getAuthVersion(), family), raw, expiry);
    }

    private AuthRefreshSession lookup(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{64}")) return null;
        return mapper.findByHash(AuthDigests.digest(raw));
    }

    private boolean validUser(SysUser user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus()) && !Boolean.TRUE.equals(user.getMustChangePassword());
    }

    private void invalidateSnapshot(long userId) {
        try { redis.clearSnapshot(userId); }
        catch (RuntimeException exception) { LOG.warn("Session revoked but snapshot invalidation failed for userId={}", userId); }
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "请重新登录");
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private record Outcome(SessionResult result, SysUser user, String reason) {}
}
