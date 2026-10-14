package com.maplehush.admin.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

class JsonTimeConfigurationTest {
    @Test
    void serializesAndDeserializesLocalDateTimeAtSecondPrecision() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonTimeConfiguration().localDateTimeFormat().customize(builder);
        JsonMapper mapper = builder.build();
        LocalDateTime value = LocalDateTime.of(2026, 10, 13, 12, 34, 56);

        assertThat(mapper.writeValueAsString(value)).isEqualTo("\"2026-10-13 12:34:56\"");
        assertThat(mapper.readValue("\"2026-10-13 12:34:56\"", LocalDateTime.class))
                .isEqualTo(value);
    }
}
