package com.maplehush.admin.system.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.service.impl.AuthRedisServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class AuthRedisFailureTest {
    @Test
    void unavailableRedisDoesNotBypassCaptchaOrLoginLimit() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 1);
        factory.afterPropertiesSet();
        try {
            RedisTemplate<String, String> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            IAuthRedisService service = new AuthRedisServiceImpl(template, new RedisKeyBuilder("t04_failure"), 600);
            BusinessException captcha = assertThrows(BusinessException.class, () -> service.issueCaptcha("127.0.0.1"));
            assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, captcha.getErrorCode());
            BusinessException login = assertThrows(BusinessException.class,
                    () -> service.checkLoginLimit("127.0.0.1", "nobody"));
            assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, login.getErrorCode());
        } finally {
            factory.destroy();
        }
    }
}
