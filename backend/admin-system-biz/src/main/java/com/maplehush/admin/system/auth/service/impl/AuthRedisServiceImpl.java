package com.maplehush.admin.system.auth.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.support.AuthDigests;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 验证码、固定窗口限流和登录快照都使用带环境前缀的 Redis 键
 */
@Service
public class AuthRedisServiceImpl implements IAuthRedisService {
    private static final DefaultRedisScript<String> TAKE =
            new DefaultRedisScript<>(
                    "local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]); end; return v",
                    String.class);
    private static final DefaultRedisScript<Long> LIMIT =
            new DefaultRedisScript<>(
                    "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",
                    Long.class);
    private final RedisTemplate<String, String> redis;
    private final RedisKeyBuilder keys;
    private final Duration snapshotTtl;

    public AuthRedisServiceImpl(
            @Qualifier("stringValueRedisTemplate") RedisTemplate<String, String> redis,
            RedisKeyBuilder keys,
            @Value("${maple-hush.auth.user-snapshot-ttl-seconds:600}") long snapshotTtlSeconds) {
        if (snapshotTtlSeconds < 1 || snapshotTtlSeconds > 86400) {
            throw new IllegalArgumentException("用户快照 TTL 须为 1 至 86400 秒");
        }
        this.redis = redis;
        this.keys = keys;
        this.snapshotTtl = Duration.ofSeconds(snapshotTtlSeconds);
    }

    @Override
    public Captcha issueCaptcha(String ip) {
        checkLimit("captcha_ip", AuthDigests.digest(ip), 10, 60);
        LineCaptcha image = CaptchaUtil.createLineCaptcha(160, 50, 4, 80);
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue()
                    .set(
                            keys.key("captcha", id),
                            image.getCode().toUpperCase(Locale.ROOT),
                            Duration.ofMinutes(2));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        return new Captcha(id, Base64.getEncoder().encodeToString(image.getImageBytes()));
    }

    @Override
    public void checkLoginLimit(String ip, String username) {
        checkLimit("login_ip", AuthDigests.digest(ip), 20, 60);
        try {
            String count = redis.opsForValue().get(accountFailuresKey(username));
            if (count != null && Long.parseLong(count) >= 8) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁，请稍后再试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void recordCredentialFailure(String username) {
        checkLimit(
                "login_failure", AuthDigests.digest(username.toLowerCase(Locale.ROOT)), 8, 15 * 60);
    }

    @Override
    public void clearCredentialFailures(String username) {
        try {
            redis.delete(accountFailuresKey(username));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private String accountFailuresKey(String username) {
        return keys.key("login_failure", AuthDigests.digest(username.toLowerCase(Locale.ROOT)));
    }

    @Override
    public void consumeCaptcha(String id, String answer) {
        if (id == null || !id.matches("[a-fA-F0-9]{32}") || answer == null) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID, "验证码无效，请重新获取");
        }
        try {
            String expected = redis.execute(TAKE, List.of(keys.key("captcha", id)));
            if (expected == null
                    || !MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.UTF_8),
                            answer.trim()
                                    .toUpperCase(Locale.ROOT)
                                    .getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(ErrorCode.CAPTCHA_INVALID, "验证码无效，请重新获取");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public String readSnapshot(long userId) {
        try {
            return redis.opsForValue().get(keys.key("user", Long.toString(userId)));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void writeSnapshot(long userId, String json) {
        try {
            redis.opsForValue().set(keys.key("user", Long.toString(userId)), json, snapshotTtl);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void clearSnapshot(long userId) {
        try {
            redis.delete(keys.key("user", Long.toString(userId)));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private void checkLimit(String purpose, String id, int max, int windowSeconds) {
        try {
            Long count =
                    redis.execute(
                            LIMIT, List.of(keys.key(purpose, id)), Integer.toString(windowSeconds));
            if (count == null) {
                throw new IllegalStateException("Rate limit script returned null");
            }
            if (count > max) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁，请稍后再试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private static BusinessException unavailable(RuntimeException cause) {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "服务暂不可用，请稍后重试");
    }
}
