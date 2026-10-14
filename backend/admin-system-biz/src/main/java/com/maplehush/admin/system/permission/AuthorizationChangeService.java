package com.maplehush.admin.system.permission;

import com.maplehush.admin.system.auth.AuthRedisService;
import com.maplehush.admin.system.permission.mapper.PermissionMapper;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 管理写操作在同一事务更新授权与版本，提交后清理资料快照。 */
@Service
public class AuthorizationChangeService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationChangeService.class);
    private final PermissionMapper mapper;
    private final AuthRedisService redis;

    public AuthorizationChangeService(PermissionMapper mapper, AuthRedisService redis) {
        this.mapper = mapper; this.redis = redis;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void roleChanged(long roleId) { increment(mapper.listRoleUsers(roleId)); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void menusChanged() { increment(mapper.listAssignedUsers()); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void usersChanged(Collection<Long> userIds) { increment(userIds); }

    /** 本人资料/头像变更不需递增权限版本，但必须在提交后使快照失效。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void profileChanged(long userId) { clearAfterCommit(java.util.List.of(userId)); }

    private void increment(Collection<Long> userIds) {
        var ids = userIds.stream().distinct().sorted().toList();
        ids.forEach(id -> {
            if (mapper.incrementVersion(id) != 1) throw new IllegalStateException("Authorization user no longer exists");
        });
        clearAfterCommit(ids);
    }

    private void clearAfterCommit(Collection<Long> ids) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                for (Long id : ids) {
                    try { redis.clearSnapshot(id); }
                    catch (RuntimeException ignored) { LOG.warn("User cache invalidation failed userId={}", id); }
                }
            }
        });
    }
}
