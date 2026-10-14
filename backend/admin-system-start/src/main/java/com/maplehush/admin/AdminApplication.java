package com.maplehush.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

/**
 * 后端唯一启动入口
 */
@SpringBootApplication
@EnableScheduling
public class AdminApplication {
    public static void main(String[] args) {
        boolean recovery =
                Arrays.asList(args).contains("--maple-hush.maintenance.recover-super-admin=true");
        SpringApplication application = new SpringApplication(AdminApplication.class);
        if (recovery) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        ConfigurableApplicationContext context = application.run(args);
        if (recovery) {
            SpringApplication.exit(context);
        }
    }
}
