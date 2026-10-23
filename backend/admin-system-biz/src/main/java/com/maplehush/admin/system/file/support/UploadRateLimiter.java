package com.maplehush.admin.system.file.support;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一上传与个人头像共享账户限额；Lua 原子检查分钟频率和 24 小时数量。
 */
@Component
public class UploadRateLimiter {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local minute = tonumber(redis.call('GET', KEYS[1]) or '0')
            local day = tonumber(redis.call('GET', KEYS[2]) or '0')
            if minute >= tonumber(ARGV[1]) or day >= tonumber(ARGV[2]) then return 0 end
            if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], 86400) end
            return 1
            """, Long.class);

    private final RedisTemplate<String, String> redis;
    private final RedisKeyBuilder keys;
    private final int perMinute;
    private final int perDay;

    public UploadRateLimiter(
            @Qualifier("stringValueRedisTemplate") RedisTemplate<String, String> redis,
            RedisKeyBuilder keys,
            @Value("${maple-hush.file.upload-per-minute:10}") int perMinute,
            @Value("${maple-hush.file.upload-per-day:100}") int perDay) {
        if (perMinute < 1 || perDay < 1) {
            throw new IllegalArgumentException("上传频率和数量限制必须大于零");
        }
        this.redis = redis;
        this.keys = keys;
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /**
     * 在上传前预占一次配额；后续失败也计入次数，避免重复失败绕过频率限制。
     */
    public void reserve(long userId) {
        Long accepted;
        try {
            accepted = redis.execute(RESERVE,
                    List.of(keys.key("upload_minute", Long.toString(userId)),
                            keys.key("upload_day", Long.toString(userId))),
                    Integer.toString(perMinute), Integer.toString(perDay));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "上传限额服务暂不可用，请稍后重试");
        }
        if (accepted == null) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "上传限额服务暂不可用，请稍后重试");
        }
        if (accepted != 1) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "上传次数已达限制，请稍后重试");
        }
    }
}
