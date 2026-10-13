package com.maplehush.admin.config.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisKeyBuilderTest {
    @Test
    void includesEnvironmentAndPurpose() {
        assertThat(new RedisKeyBuilder("maple_hush_test").key("captcha", "abc123"))
                .isEqualTo("maple_hush_test:captcha:abc123");
    }

    @Test
    void rejectsRawAccountNamesAndUnsafePrefixes() {
        assertThatThrownBy(() -> new RedisKeyBuilder("dev:bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisKeyBuilder("dev").key("login", "a@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
