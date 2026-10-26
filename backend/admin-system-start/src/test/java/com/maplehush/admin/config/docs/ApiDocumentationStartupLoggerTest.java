package com.maplehush.admin.config.docs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 检查真实监听端口、HTTPS 和上下文路径，生产环境不输出文档链接。
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiDocumentationStartupLoggerTest {
    @Test
    void readyTestServerPrintsActualPortAndContextPath(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("springdoc.api-docs.enabled", "true");
        environment.setProperty("server.ssl.enabled", "true");
        new ApiDocumentationStartupLogger().onReady(event(environment));
        assertTrue(output.getOut().contains("Maple Hush Admin started successfully!"));
        assertTrue(output.getOut().contains("Knife4j: https://localhost:18443/admin/doc.html"));
        assertTrue(output.getOut().contains("OpenAPI: https://localhost:18443/admin/v3/api-docs"));
    }

    @Test
    void productionDoesNotPrintAccessibleDocumentationLinks(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test", "prod");
        environment.setProperty("springdoc.api-docs.enabled", "true");
        new ApiDocumentationStartupLogger().onReady(event(environment));
        assertTrue(output.getOut().contains("接口文档已关闭"));
        assertTrue(output.getOut().contains("Maple Hush Admin started successfully!"));
        assertFalse(output.getOut().contains("/doc.html"));
    }

    private ApplicationReadyEvent event(MockEnvironment environment) {
        ServletWebServerApplicationContext context = mock(ServletWebServerApplicationContext.class);
        WebServer webServer = mock(WebServer.class);
        MockServletContext servlet = new MockServletContext();
        servlet.setContextPath("/admin");
        when(context.getEnvironment()).thenReturn(environment);
        when(context.getWebServer()).thenReturn(webServer);
        when(context.getServletContext()).thenReturn(servlet);
        when(webServer.getPort()).thenReturn(18443);
        return new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO);
    }
}
