package com.maplehush.admin.system.auth;

import com.maplehush.admin.system.auth.mapper.AuthSessionMapper;
import com.maplehush.admin.system.user.entity.SysUser;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** 只从已校验的 MySQL 用户重建缓存，不从 Redis 反向确定会话身份。 */
@Service
public class UserSnapshotService {
    private final AuthSessionMapper mapper;
    private final AuthRedisService redis;
    private final JsonMapper json;

    public UserSnapshotService(AuthSessionMapper mapper, AuthRedisService redis, JsonMapper json) {
        this.mapper = mapper;
        this.redis = redis;
        this.json = json;
    }

    public void write(SysUser user) {
        redis.writeSnapshot(user.getId(), json.writeValueAsString(new Snapshot(Long.toString(user.getId()),
                user.getUsername(), user.getRealName(), user.getAvatarUrl(), user.getIntroduction(),
                mapper.roleCodes(user.getId()), user.getAuthVersion())));
    }

    private record Snapshot(String userId, String username, String realName, String avatar,
                            String introduction, List<String> roles, long authVersion) {}
}
