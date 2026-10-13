package com.maplehush.admin.config.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

/** 统一 HTTP JSON 中无偏移量的东八区本地时间格式。 */
@Configuration
public class JsonTimeConfiguration {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    JsonMapperBuilderCustomizer localDateTimeFormat() {
        return builder -> {
            SimpleModule module = new SimpleModule("maple-hush-local-date-time");
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMAT));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMAT));
            builder.addModule(module);
        };
    }
}
