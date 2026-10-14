package com.maplehush.admin.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 公共连接复用 Boot/Lettuce 自动配置，键和值显式使用 UTF-8 字符串
 */
@Configuration
public class RedisConfiguration {
    @Bean
    RedisTemplate<String, String> stringValueRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    RedisKeyBuilder redisKeyBuilder(@Value("${maple-hush.redis.key-prefix}") String prefix) {
        return new RedisKeyBuilder(prefix);
    }
}
