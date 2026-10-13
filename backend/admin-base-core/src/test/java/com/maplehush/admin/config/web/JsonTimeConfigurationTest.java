package com.maplehush.admin.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonTimeConfigurationTest {
    @Test
    void serializesAndDeserializesLocalDateTimeAtSecondPrecision() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonTimeConfiguration().localDateTimeFormat().customize(builder);
        JsonMapper mapper = builder.build();
        LocalDateTime value = LocalDateTime.of(2026, 10, 13, 12, 34, 56);

        assertThat(mapper.writeValueAsString(value)).isEqualTo("\"2026-10-13 12:34:56\"");
        assertThat(mapper.readValue("\"2026-10-13 12:34:56\"", LocalDateTime.class)).isEqualTo(value);
    }
}
