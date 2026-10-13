package com.maplehush.admin.config.redis;

import java.util.regex.Pattern;

/** 在所有业务 Redis 键前加入环境隔离前缀。 */
public final class RedisKeyBuilder {
    private static final Pattern SAFE_PART = Pattern.compile("[a-zA-Z0-9_-]+");
    private final String prefix;

    public RedisKeyBuilder(String prefix) {
        if (prefix == null || !SAFE_PART.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Invalid Redis key prefix");
        }
        this.prefix = prefix;
    }

    public String key(String purpose, String opaqueId) {
        if (purpose == null || !SAFE_PART.matcher(purpose).matches()
                || opaqueId == null || !SAFE_PART.matcher(opaqueId).matches()) {
            throw new IllegalArgumentException("Redis key parts must be opaque and safe");
        }
        return prefix + ":" + purpose + ":" + opaqueId;
    }
}
