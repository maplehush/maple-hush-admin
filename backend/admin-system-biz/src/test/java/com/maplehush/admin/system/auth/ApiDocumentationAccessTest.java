package com.maplehush.admin.system.auth;

import com.maplehush.admin.system.docs.config.ApiDocumentationAccess;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档开放必须同时满足开发或测试环境及显式启用；业务路径不属于文档白名单。
 */
class ApiDocumentationAccessTest {
    @Test
    void onlyEnabledDevelopmentOrTestCanExposeDocumentation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("springdoc.api-docs.enabled", "true");
        assertFalse(ApiDocumentationAccess.enabled(environment));
        environment.setActiveProfiles("dev");
        assertTrue(ApiDocumentationAccess.enabled(environment));
        environment.setActiveProfiles("dev", "prod");
        assertFalse(ApiDocumentationAccess.enabled(environment));
        environment.setActiveProfiles("test");
        assertTrue(ApiDocumentationAccess.enabled(environment));
        environment.setActiveProfiles("dev", "test");
        assertTrue(ApiDocumentationAccess.enabled(environment));
        environment.setActiveProfiles("test", "prod");
        assertFalse(ApiDocumentationAccess.enabled(environment));
        environment.setActiveProfiles("dev");
        environment.setProperty("springdoc.api-docs.enabled", "false");
        assertFalse(ApiDocumentationAccess.enabled(environment));
    }
}
