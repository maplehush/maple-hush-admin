package com.maplehush.admin.system.file;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.file.support.UploadRateLimiter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class UploadRateLimiterTest {
    @Test
    @SuppressWarnings("unchecked")
    void acceptsWithinQuotaAndRejectsExhaustedQuota() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L, 0L);
        var limiter = new UploadRateLimiter(redis, new RedisKeyBuilder("test"), 10, 100);
        assertDoesNotThrow(() -> limiter.reserve(7));
        var error = assertThrows(BusinessException.class, () -> limiter.reserve(7));
        assertEquals(ErrorCode.RATE_LIMITED, error.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void failsClosedWhenRedisIsUnavailableOrReturnsNoResult() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        var limiter = new UploadRateLimiter(redis, new RedisKeyBuilder("test"), 10, 100);
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> limiter.reserve(7)).getErrorCode());
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("private-backend-detail"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> limiter.reserve(7)).getErrorCode());
    }
}
