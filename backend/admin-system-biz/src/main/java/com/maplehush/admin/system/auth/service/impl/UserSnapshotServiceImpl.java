package com.maplehush.admin.system.auth.service.impl;

import com.maplehush.admin.system.auth.mapper.AuthSessionMapper;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.service.IUserSnapshotService;
import com.maplehush.admin.system.auth.vo.CurrentUserVO;
import com.maplehush.admin.system.permission.service.IPermissionService;
import com.maplehush.admin.system.user.entity.SysUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 只从已校验的 MySQL 用户重建缓存，不从 Redis 反向确定会话身份
 */
@Service
public class UserSnapshotServiceImpl implements IUserSnapshotService {
    private static final Logger LOG = LoggerFactory.getLogger(UserSnapshotServiceImpl.class);
    private final AuthSessionMapper mapper;
    private final IAuthRedisService redis;
    private final JsonMapper json;

    public UserSnapshotServiceImpl(
            AuthSessionMapper mapper, IAuthRedisService redis, JsonMapper json) {
        this.mapper = mapper;
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void write(SysUser user) {
        redis.writeSnapshot(
                user.getId(),
                json.writeValueAsString(
                        new Snapshot(
                                Long.toString(user.getId()),
                                user.getUsername(),
                                user.getRealName(),
                                user.getAvatarUrl(),
                                user.getIntroduction(),
                                mapper.roleCodes(user.getId()),
                                user.getAuthVersion())));
    }

    /**
     * 认证上下文来自 MySQL，缓存失效或故障时仅回退资料读取，不绕过认证
     */
    @Override
    public CurrentUserVO getCurrent(IPermissionService.Context context) {
        SysUser user = context.user();
        try {
            String cached = redis.readSnapshot(user.getId());
            if (cached != null) {
                Snapshot snapshot = json.readValue(cached, Snapshot.class);
                if (snapshot.authVersion() == user.getAuthVersion()
                        && snapshot.userId().equals(Long.toString(user.getId()))
                        && java.util.Objects.equals(snapshot.username(), user.getUsername())
                        && java.util.Objects.equals(snapshot.realName(), user.getRealName())
                        && java.util.Objects.equals(snapshot.avatar(), user.getAvatarUrl())
                        && java.util.Objects.equals(
                                snapshot.introduction(), user.getIntroduction())) {
                    return new CurrentUserVO(
                            snapshot.userId(),
                            snapshot.username(),
                            snapshot.realName(),
                            snapshot.avatar(),
                            snapshot.introduction(),
                            context.roles(),
                            "/dashboard/workspace");
                }
            }
        } catch (RuntimeException ignored) {
            LOG.warn("Current-user cache read failed userId={}", user.getId());
        }
        try {
            write(user);
        } catch (RuntimeException ignored) {
            LOG.warn("Current-user cache rebuild failed userId={}", user.getId());
        }
        return new CurrentUserVO(
                Long.toString(user.getId()),
                user.getUsername(),
                user.getRealName(),
                user.getAvatarUrl(),
                user.getIntroduction(),
                context.roles(),
                "/dashboard/workspace");
    }

    private record Snapshot(
            String userId,
            String username,
            String realName,
            String avatar,
            String introduction,
            List<String> roles,
            long authVersion) {}
}
